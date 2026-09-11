package com.jobregister.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jobregister.app.ai.MessageParser
import com.jobregister.app.data.JobRepository
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JobRepository.get(app)

    val jobs: StateFlow<List<Job>> = repo.jobs

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
