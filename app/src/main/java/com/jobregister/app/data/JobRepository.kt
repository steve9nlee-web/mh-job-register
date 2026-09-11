package com.jobregister.app.data

import android.content.Context
import com.jobregister.app.BuildConfig
import com.jobregister.app.model.Customer
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import com.jobregister.app.model.RateCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Single source of truth inside the app. Jobs are cached locally
 * (SharedPreferences as JSON) and optionally synced with the Job Register
 * spreadsheet through SheetApi when a sync URL is configured in Settings.
 */
class JobRepository(context: Context) {

    private val prefs = context.getSharedPreferences("job_register", Context.MODE_PRIVATE)

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers

    private val _apartments = MutableStateFlow<Map<String, String>>(emptyMap())
    val apartments: StateFlow<Map<String, String>> = _apartments

    private val _services = MutableStateFlow<List<String>>(emptyList())
    val services: StateFlow<List<String>> = _services

    private val _serviceDetails = MutableStateFlow<Map<String, String>>(emptyMap())
    val serviceDetails: StateFlow<Map<String, String>> = _serviceDetails

    private val _photos = MutableStateFlow<List<SheetApi.JobPhoto>>(emptyList())
    val photos: StateFlow<List<SheetApi.JobPhoto>> = _photos

    // Decoded photos already downloaded in this session, keyed by Drive file id.
    private val photoCache = LinkedHashMap<String, ByteArray>()

    /** A job that appeared, or whose status moved, since the previous pull. */
    data class JobChange(val job: Job, val isNew: Boolean)

    private val _changes = MutableStateFlow<List<JobChange>>(emptyList())
    val changes: StateFlow<List<JobChange>> = _changes

    var syncUrl: String
        get() = prefs.getString("sync_url", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SYNC_URL
        set(value) { prefs.edit().putString("sync_url", value.trim()).apply() }

    var syncKey: String
        get() = prefs.getString("sync_key", null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_SYNC_KEY
        set(value) { prefs.edit().putString("sync_key", value.trim()).apply() }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) { prefs.edit().putString("user_name", value.trim()).apply() }

    init {
        _jobs.value = loadLocal() ?: sampleJobs()
        loadDirectory()
        persist()
    }

    fun upsert(job: Job) {
        _jobs.value = _jobs.value.filterNot { it.id == job.id } + job
        persist()
    }

    fun get(id: String): Job? = _jobs.value.firstOrNull { it.id == id }

    fun updateStatus(
        id: String,
        status: JobStatus,
        remarks: String,
        by: String = "",
        at: String = ""
    ) {
        get(id)?.let {
            upsert(it.copy(
                status = status,
                remarks = remarks,
                updatedBy = by.ifBlank { it.updatedBy },
                startedAt = if (status == JobStatus.IN_PROGRESS && it.startedAt.isBlank()) at
                    else it.startedAt,
                completedAt = if (status == JobStatus.COMPLETED) at.ifBlank { it.completedAt }
                    else it.completedAt
            ))
        }
    }

    /** Human review step: fix missing info and clear the AI flag. */
    fun resolveReview(id: String, unit: String, category: JobCategory, description: String) {
        get(id)?.let {
            upsert(it.copy(unit = unit, category = category, description = description,
                needsReview = false, reviewReason = ""))
        }
    }

    /** Rate-card matching: fill customer charge and contractor payable on completed jobs. */
    fun applyRateCard(id: String) {
        get(id)?.let { job ->
            RateCard.match(job.category)?.let { rate ->
                upsert(job.copy(customerCharge = rate.customerRate,
                    contractorPayable = rate.contractorPayable))
            }
        }
    }

    fun setBilling(id: String, customerCharge: Double?, contractorPayable: Double?) {
        get(id)?.let { upsert(it.copy(customerCharge = customerCharge, contractorPayable = contractorPayable)) }
    }

    fun markInvoiced(id: String, invoiced: Boolean) {
        get(id)?.let { upsert(it.copy(invoiced = invoiced)) }
    }

    fun markPaid(id: String, paid: Boolean) {
        get(id)?.let { upsert(it.copy(paid = paid)) }
    }

    // ---- sync ----

    /** Pull from the spreadsheet; remote rows win by id. Returns an error message or null. */
    suspend fun pull(): String? {
        val url = syncUrl
        if (url.isBlank()) return "No sync URL set (Settings)"
        return try {
            val remote = SheetApi.fetchAll(url, syncKey)
            _changes.value = detectChanges(remote.jobs)
            val remoteIds = remote.jobs.map { it.id }.toSet()
            _jobs.value = remote.jobs + _jobs.value.filterNot { it.id in remoteIds }
            if (remote.customers.isNotEmpty()) _customers.value = remote.customers
            if (remote.apartments.isNotEmpty()) _apartments.value = remote.apartments
            if (remote.services.isNotEmpty()) _services.value = remote.services
            if (remote.serviceDetails.isNotEmpty()) _serviceDetails.value = remote.serviceDetails
            _photos.value = remote.photos
            persist()
            persistDirectory()
            null
        } catch (e: Exception) {
            e.message ?: "Sync failed"
        }
    }

    /** Push one job to the spreadsheet, best effort. */
    suspend fun push(job: Job): String? {
        val url = syncUrl
        if (url.isBlank()) return null // local-only mode is fine
        return try {
            SheetApi.upsertJob(url, syncKey, job); null
        } catch (e: Exception) {
            e.message ?: "Push failed"
        }
    }

    // ---- customer database mutations (optimistic local + push) ----

    fun addCustomerLocal(c: Customer) {
        _customers.value = _customers.value.filterNot { it.unit == c.unit } + c
        persistDirectory()
    }

    fun deleteCustomerLocal(unit: String) {
        _customers.value = _customers.value.filterNot { it.unit == unit }
        persistDirectory()
    }

    fun addApartmentLocal(code: String, name: String) {
        _apartments.value = _apartments.value + (code to name)
        persistDirectory()
    }

    fun deleteApartmentLocal(code: String) {
        _apartments.value = _apartments.value - code
        persistDirectory()
    }

    fun addServiceLocal(name: String) {
        if (_services.value.none { it.equals(name, ignoreCase = true) }) {
            _services.value = _services.value + name
            persistDirectory()
        }
    }

    fun deleteServiceLocal(name: String) {
        _services.value = _services.value.filterNot { it.equals(name, ignoreCase = true) }
        persistDirectory()
    }

    /** JPEG bytes for one job photo, downloaded once and then cached. */
    suspend fun photoBytes(fileId: String): ByteArray? {
        synchronized(photoCache) { photoCache[fileId] }?.let { return it }
        val url = syncUrl
        if (url.isBlank()) return null
        val bytes = SheetApi.fetchPhoto(url, syncKey, fileId) ?: return null
        synchronized(photoCache) {
            photoCache[fileId] = bytes
            while (photoCache.size > 12) {
                photoCache.remove(photoCache.keys.first())
            }
        }
        return bytes
    }

    suspend fun pushAction(body: JSONObject): String? {
        val url = syncUrl
        if (url.isBlank()) return null
        return try {
            SheetApi.postAction(url, syncKey, body); null
        } catch (e: Exception) {
            e.message ?: "Push failed"
        }
    }

    /**
     * Compare the incoming rows with what this phone saw last time. The very
     * first sync after an install records the state silently, so a new phone
     * does not announce every job in the register.
     */
    private fun detectChanges(incoming: List<Job>): List<JobChange> {
        val raw = prefs.getString("seen_status", null)
        val previous = mutableMapOf<String, String>()
        if (raw != null) {
            try {
                val o = JSONObject(raw)
                o.keys().forEach { k -> previous[k] = o.optString(k) }
            } catch (_: Exception) { }
        }
        val changes = if (raw == null) emptyList() else incoming.mapNotNull { job ->
            val was = previous[job.id]
            when {
                was == null -> JobChange(job, true)
                was != job.status.name -> JobChange(job, false)
                else -> null
            }
        }
        val now = JSONObject()
        incoming.forEach { now.put(it.id, it.status.name) }
        prefs.edit().putString("seen_status", now.toString()).apply()
        return changes
    }

    fun clearChanges() { _changes.value = emptyList() }

    // ---- local persistence ----

    private fun persist() {
        val arr = JSONArray()
        _jobs.value.forEach { arr.put(SheetApi.toJson(it)) }
        prefs.edit().putString("jobs", arr.toString()).apply()
    }

    private fun persistDirectory() {
        val custArr = JSONArray()
        _customers.value.forEach { c ->
            custArr.put(JSONObject().apply {
                put("apartment", c.apartment); put("unit", c.unit)
                put("service", c.service); put("customerName", c.customerName)
            })
        }
        val aptObj = JSONObject()
        _apartments.value.forEach { (k, v) -> aptObj.put(k, v) }
        val detObj = JSONObject()
        _serviceDetails.value.forEach { (k, v) -> detObj.put(k, v) }
        prefs.edit()
            .putString("customers", custArr.toString())
            .putString("apartments", aptObj.toString())
            .putString("services", JSONArray(_services.value).toString())
            .putString("service_details", detObj.toString())
            .apply()
    }

    private fun loadDirectory() {
        try {
            prefs.getString("customers", null)?.let { raw ->
                val arr = JSONArray(raw)
                _customers.value = (0 until arr.length()).map { i ->
                    val c = arr.getJSONObject(i)
                    Customer(c.optString("apartment"), c.optString("unit"),
                        c.optString("service"), c.optString("customerName"))
                }
            }
            prefs.getString("apartments", null)?.let { raw ->
                val o = JSONObject(raw)
                val m = mutableMapOf<String, String>()
                o.keys().forEach { k -> m[k] = o.optString(k) }
                _apartments.value = m
            }
            prefs.getString("services", null)?.let { raw ->
                val arr = JSONArray(raw)
                _services.value = (0 until arr.length()).map { arr.getString(it) }
            }
            prefs.getString("service_details", null)?.let { raw ->
                val o = JSONObject(raw)
                val m = mutableMapOf<String, String>()
                o.keys().forEach { k -> m[k] = o.optString(k) }
                _serviceDetails.value = m
            }
        } catch (_: Exception) { }
    }

    private fun loadLocal(): List<Job>? {
        val raw = prefs.getString("jobs", null) ?: return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SheetApi.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { null }
    }

    private fun sampleJobs(): List<Job> {
        val today = LocalDate.now()
        return listOf(
            Job("J-SAMPLE01", today.minusDays(2).toString(), "PV-12-03", JobCategory.CLEANING,
                "Weekly cleaning unit PV-12-03", JobStatus.COMPLETED,
                customerCharge = 80.0, contractorPayable = 50.0, createdBy = "Sample"),
            Job("J-SAMPLE02", today.minusDays(1).toString(), "R-5-11", JobCategory.PLUMBING,
                "Kitchen sink choke, tenant reported leak", JobStatus.PENDING, createdBy = "Sample"),
            Job("J-SAMPLE03", today.minusDays(1).toString(), "", JobCategory.UNKNOWN,
                "tmr go see the thing at corner house", JobStatus.PENDING,
                needsReview = true, reviewReason = "Unit number not found; Category could not be classified",
                createdBy = "Sample", rawMessage = "tmr go see the thing at corner house"),
            Job("J-SAMPLE04", today.toString(), "OV-8-02", JobCategory.AIRCON,
                "Aircon service 2 units, chemical wash", JobStatus.WAITING,
                remarks = "Waiting for parts", createdBy = "Sample"),
            Job("J-SAMPLE05", today.minusDays(3).toString(), "L-3-07", JobCategory.GENERAL_REPAIR,
                "Fix bedroom door hinge", JobStatus.COMPLETED,
                customerCharge = 120.0, contractorPayable = 80.0, invoiced = true, createdBy = "Sample")
        )
    }

    companion object {
        @Volatile private var instance: JobRepository? = null
        fun get(context: Context): JobRepository =
            instance ?: synchronized(this) {
                instance ?: JobRepository(context.applicationContext).also { instance = it }
            }
    }
}
