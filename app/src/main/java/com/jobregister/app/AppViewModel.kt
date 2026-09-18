package com.jobregister.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jobregister.app.ai.MessageParser
import com.jobregister.app.data.JobRepository
import com.jobregister.app.data.SheetApi
import com.jobregister.app.model.Customer
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Base64
import com.jobregister.app.util.Notifier
import com.jobregister.app.util.PhotoUtil
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JobRepository.get(app)

    val jobs: StateFlow<List<Job>> = repo.jobs
    val customers: StateFlow<List<Customer>> = repo.customers
    val apartments: StateFlow<Map<String, String>> = repo.apartments
    val services: StateFlow<List<String>> = repo.services
    val serviceDetails: StateFlow<Map<String, String>> = repo.serviceDetails
    val photos: StateFlow<List<SheetApi.JobPhoto>> = repo.photos

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    var syncUrl: String
        get() = repo.syncUrl
        set(value) { repo.syncUrl = value }

    var syncKey: String
        get() = repo.syncKey
        set(value) { repo.syncKey = value }

    var userName: String
        get() = repo.userName
        set(value) { repo.userName = value }

    init {
        refreshQuietly()
    }

    fun consumeMessage() { _message.value = null }

    /** Create a job for a registered customer unit picked from the dropdowns. */
    fun createJob(
        unit: String,
        service: String,
        description: String,
        photos: List<ByteArray> = emptyList(),
        rooms: String = ""
    ) {
        val category = when {
            service.contains("clean", ignoreCase = true) -> JobCategory.CLEANING
            service.contains("air", ignoreCase = true) -> JobCategory.AIRCON
            service.contains("pest", ignoreCase = true) -> JobCategory.PEST_CONTROL
            service.contains("plumb", ignoreCase = true) -> JobCategory.PLUMBING
            else -> JobCategory.GENERAL_REPAIR
        }
        val desc = listOf(service, rooms, description)
            .filter { it.isNotBlank() }.joinToString(" — ")
        // A job raised by anyone other than the admin waits for approval
        // before any contractor can see it. An admin raising it themselves
        // has already made that decision.
        val job = Job(
            id = "J-" + UUID.randomUUID().toString().take(8).uppercase(),
            date = LocalDate.now().toString(),
            unit = unit,
            category = category,
            description = desc,
            status = if (RoleConfig.canApproveJobs) JobStatus.PENDING
                else JobStatus.AWAITING_APPROVAL,
            createdBy = userName.ifBlank { RoleConfig.role.name },
            rooms = rooms,
            approvedBy = if (RoleConfig.canApproveJobs) {
                userName.ifBlank { RoleConfig.role.name }
            } else "",
            approvedAt = if (RoleConfig.canApproveJobs) stampNow() else ""
        )
        saveJob(job)
        uploadPhotos(job.id, photos)
        if (!job.approved) {
            _message.value = "Job ${job.id} sent to admin for approval"
        }
    }

    /** Admin releases the job so the trade it belongs to can pick it up. */
    fun approveJob(id: String) {
        repo.approve(id, userName.ifBlank { RoleConfig.role.name }, stampNow())
        repo.get(id)?.let { pushJob(it) }
        _message.value = "Job $id approved — sent to the contractor"
    }

    private fun stampNow(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    /** Stamp the photo with job no + date/time and upload it to the Drive folder. */
    /**
     * Stamp a photo with the job number and the time it was taken, then send
     * it to the backend. [kind] is "job" for the picture attached when the
     * job is raised, or "before"/"after" for the contractor's work record —
     * each kind lands in its own Drive folder.
     */
    fun uploadPhoto(jobId: String, bytes: ByteArray, kind: String = "job") =
        uploadPhotos(jobId, listOf(bytes), kind)

    /**
     * Stamp each photo with the job number and the time it was taken, then
     * send them one after another. [kind] is "job" for pictures attached when
     * the job is raised, or "before"/"after" for the contractor's work
     * record — each kind lands in its own Drive folder.
     */
    fun uploadPhotos(jobId: String, images: List<ByteArray>, kind: String = "job") {
        if (images.isEmpty()) return
        viewModelScope.launch {
            var failure: String? = null
            images.forEachIndexed { index, bytes ->
                _message.value = if (images.size == 1) "Uploading photo…"
                    else "Uploading photo ${index + 1} of ${images.size}…"
                failure = pushPhoto(jobId, bytes, kind) ?: failure
            }
            val error = failure
            if (error == null) {
                // Pull the Photos tab back so the pictures show on the job.
                repo.pull()
                _message.value = when {
                    images.size > 1 -> "${images.size} photos uploaded to Drive"
                    kind == "before" -> "Before photo saved"
                    kind == "after" -> "After photo saved"
                    else -> "Photo uploaded to Drive"
                }
            } else {
                _message.value = "Photo upload failed: $error"
            }
        }
    }

    private suspend fun pushPhoto(jobId: String, bytes: ByteArray, kind: String): String? {
        val now = LocalDateTime.now()
        val tag = when (kind) {
            "before" -> "BEFORE"
            "after" -> "AFTER"
            else -> ""
        }
        val stampedAt = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val label = listOf(jobId, tag, stampedAt).filter { it.isNotBlank() }.joinToString("  ")
        val stamped = PhotoUtil.stamp(bytes, label)
        val filename = listOf(
            jobId, tag, now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        ).filter { it.isNotBlank() }.joinToString("_") + ".jpg"
        return repo.pushAction(JSONObject().apply {
            put("type", "photo"); put("jobId", jobId)
            put("kind", kind)
            put("filename", filename)
            put("data", Base64.encodeToString(stamped, Base64.NO_WRAP))
        })
    }

    /** JPEG bytes of a job photo, fetched through the backend and cached. */
    suspend fun photoBytes(fileId: String): ByteArray? = repo.photoBytes(fileId)

    /** AI conversion step: raw WhatsApp text -> job row (not yet saved). */
    fun parseMessage(raw: String): Job {
        val job = MessageParser.parse(
            raw, createdBy = userName.ifBlank { RoleConfig.role.name }
        ).job
        // Rows converted from a WhatsApp message go through the same approval
        // gate as a job raised on the form.
        return if (RoleConfig.canApproveJobs) job
        else job.copy(status = JobStatus.AWAITING_APPROVAL)
    }

    fun saveJob(job: Job) {
        repo.upsert(job)
        pushJob(job)
        _message.value = "Job ${job.id} saved" + if (job.needsReview) " — flagged for review" else ""
    }

    fun updateStatus(id: String, status: JobStatus, remarks: String) {
        repo.updateStatus(
            id, status, remarks, userName.ifBlank { RoleConfig.role.name }, stampNow()
        )
        repo.get(id)?.let { pushJob(it) }
    }

    /**
     * Pull in the background with no snackbar. Used on launch and whenever
     * the app comes back to the front, so a job raised on another phone
     * turns up without anyone pressing sync.
     */
    fun refreshQuietly() {
        viewModelScope.launch {
            if (repo.pull() == null) announceChanges()
        }
    }

    /** Put anything that changed on the phone's notification shade. */
    private fun announceChanges() {
        val changes = repo.changes.value
        if (changes.isNotEmpty()) {
            Notifier.notifyChanges(getApplication<Application>(), changes)
            repo.clearChanges()
        }
    }

    fun resolveReview(id: String, unit: String, category: JobCategory, description: String) {
        repo.resolveReview(id, unit, category, description)
        repo.get(id)?.let { pushJob(it) }
    }

    fun applyRateCard(id: String) {
        repo.applyRateCard(id)
        repo.get(id)?.let { pushJob(it) }
    }

    fun setBilling(id: String, customer: Double?, contractor: Double?) {
        repo.setBilling(id, customer, contractor)
        repo.get(id)?.let { pushJob(it) }
    }

    fun markInvoiced(id: String, invoiced: Boolean) {
        repo.markInvoiced(id, invoiced)
        repo.get(id)?.let { pushJob(it) }
    }

    fun markPaid(id: String, paid: Boolean) {
        repo.markPaid(id, paid)
        repo.get(id)?.let { pushJob(it) }
    }

    // ---- customer database management ----

    private fun pushAction(body: JSONObject) {
        viewModelScope.launch {
            repo.pushAction(body)?.let { _message.value = "Saved locally; sync failed: $it" }
        }
    }

    fun addCustomer(apartment: String, unit: String, service: String, name: String) {
        repo.addCustomerLocal(Customer(apartment, unit, service, name))
        pushAction(JSONObject().apply {
            put("type", "customer"); put("action", "add")
            put("apartment", apartment); put("unit", unit)
            put("service", service); put("customerName", name)
        })
        _message.value = "Customer $unit saved"
    }

    fun deleteCustomer(unit: String) {
        repo.deleteCustomerLocal(unit)
        pushAction(JSONObject().apply {
            put("type", "customer"); put("action", "delete"); put("unit", unit)
        })
        _message.value = "Customer $unit deleted"
    }

    fun addApartment(code: String, name: String) {
        repo.addApartmentLocal(code, name)
        pushAction(JSONObject().apply {
            put("type", "apartment"); put("action", "add")
            put("code", code); put("name", name)
        })
        _message.value = "Apartment $code saved"
    }

    fun deleteApartment(code: String) {
        repo.deleteApartmentLocal(code)
        pushAction(JSONObject().apply {
            put("type", "apartment"); put("action", "delete"); put("code", code)
        })
        _message.value = "Apartment $code deleted"
    }

    fun addService(name: String) {
        repo.addServiceLocal(name)
        pushAction(JSONObject().apply {
            put("type", "service"); put("action", "add"); put("name", name)
        })
        _message.value = "Service saved"
    }

    fun deleteService(name: String) {
        repo.deleteServiceLocal(name)
        pushAction(JSONObject().apply {
            put("type", "service"); put("action", "delete"); put("name", name)
        })
        _message.value = "Service deleted"
    }

    fun sync() {
        viewModelScope.launch {
            _syncing.value = true
            val err = repo.pull()
            _syncing.value = false
            if (err == null) announceChanges()
            _message.value = err ?: "Synced with Job Register"
        }
    }

    private fun pushJob(job: Job) {
        viewModelScope.launch {
            repo.push(job)?.let { _message.value = "Saved locally; sync failed: $it" }
        }
    }
}
