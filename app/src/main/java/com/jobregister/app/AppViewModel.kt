package com.jobregister.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jobregister.app.ai.MessageParser
import com.jobregister.app.data.JobRepository
import com.jobregister.app.model.Customer
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JobRepository.get(app)

    val jobs: StateFlow<List<Job>> = repo.jobs
    val customers: StateFlow<List<Customer>> = repo.customers
    val apartments: StateFlow<Map<String, String>> = repo.apartments
    val services: StateFlow<List<String>> = repo.services
    val serviceDetails: StateFlow<Map<String, String>> = repo.serviceDetails

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

    fun consumeMessage() { _message.value = null }

    /** Create a job for a registered customer unit picked from the dropdowns. */
    fun createJob(unit: String, service: String, description: String) {
        val category = when {
            service.contains("clean", ignoreCase = true) -> JobCategory.CLEANING
            service.contains("air", ignoreCase = true) -> JobCategory.AIRCON
            service.contains("pest", ignoreCase = true) -> JobCategory.PEST_CONTROL
            else -> JobCategory.GENERAL_REPAIR
        }
        val desc = if (description.isBlank()) service else "$service — $description"
        saveJob(Job(
            id = "J-" + UUID.randomUUID().toString().take(8).uppercase(),
            date = LocalDate.now().toString(),
            unit = unit,
            category = category,
            description = desc,
            status = JobStatus.PENDING,
            createdBy = userName.ifBlank { RoleConfig.role.name }
        ))
    }

    /** AI conversion step: raw WhatsApp text -> job row (not yet saved). */
    fun parseMessage(raw: String): Job =
        MessageParser.parse(raw, createdBy = userName.ifBlank { RoleConfig.role.name }).job

    fun saveJob(job: Job) {
        repo.upsert(job)
        pushJob(job)
        _message.value = "Job ${job.id} saved" + if (job.needsReview) " — flagged for review" else ""
    }

    fun updateStatus(id: String, status: JobStatus, remarks: String) {
        repo.updateStatus(id, status, remarks)
        repo.get(id)?.let { pushJob(it) }
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
            _message.value = err ?: "Synced with Job Register"
        }
    }

    private fun pushJob(job: Job) {
        viewModelScope.launch {
            repo.push(job)?.let { _message.value = "Saved locally; sync failed: $it" }
        }
    }
}
