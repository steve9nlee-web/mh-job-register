package com.jobregister.app.data

import android.content.Context
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

    var syncUrl: String
        get() = prefs.getString("sync_url", "") ?: ""
        set(value) { prefs.edit().putString("sync_url", value.trim()).apply() }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(value) { prefs.edit().putString("user_name", value.trim()).apply() }

    init {
        _jobs.value = loadLocal() ?: sampleJobs()
        persist()
    }

    fun upsert(job: Job) {
        _jobs.value = _jobs.value.filterNot { it.id == job.id } + job
        persist()
    }

    fun get(id: String): Job? = _jobs.value.firstOrNull { it.id == id }

    fun updateStatus(id: String, status: JobStatus, remarks: String) {
        get(id)?.let { upsert(it.copy(status = status, remarks = remarks)) }
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
            val remote = SheetApi.fetchJobs(url)
            val remoteIds = remote.map { it.id }.toSet()
            _jobs.value = remote + _jobs.value.filterNot { it.id in remoteIds }
            persist()
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
            SheetApi.upsertJob(url, job); null
        } catch (e: Exception) {
            e.message ?: "Push failed"
        }
    }

    // ---- local persistence ----

    private fun persist() {
        val arr = JSONArray()
        _jobs.value.forEach { arr.put(SheetApi.toJson(it)) }
        prefs.edit().putString("jobs", arr.toString()).apply()
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
