package com.jobregister.app.data

import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin JSON client for the Google Apps Script web app that fronts the
 * Job Register spreadsheet (see backend/Code.gs in the repo).
 *
 * GET  <url>            -> {"jobs":[{...}, ...]}
 * POST <url> {job json} -> upserts one row by ID
 */
object SheetApi {

    suspend fun fetchJobs(baseUrl: String): List<Job> = withContext(Dispatchers.IO) {
        val conn = open(baseUrl, "GET")
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("jobs") ?: JSONArray()
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun upsertJob(baseUrl: String, job: Job): Unit = withContext(Dispatchers.IO) {
        val conn = open(baseUrl, "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(toJson(job).toString().toByteArray()) }
            conn.inputStream.bufferedReader().readText() // drain / follow Apps Script redirect
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.instanceFollowRedirects = true // Apps Script replies via 302
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        return conn
    }

    fun toJson(j: Job): JSONObject = JSONObject().apply {
        put("id", j.id); put("date", j.date); put("unit", j.unit)
        put("category", j.category.name); put("description", j.description)
        put("status", j.status.name); put("needsReview", j.needsReview)
        put("reviewReason", j.reviewReason); put("remarks", j.remarks)
        put("customerCharge", j.customerCharge ?: JSONObject.NULL)
        put("contractorPayable", j.contractorPayable ?: JSONObject.NULL)
        put("invoiced", j.invoiced); put("paid", j.paid)
        put("createdBy", j.createdBy); put("rawMessage", j.rawMessage)
    }

    fun fromJson(o: JSONObject): Job = Job(
        id = o.optString("id"),
        date = o.optString("date"),
        unit = o.optString("unit"),
        category = JobCategory.from(o.optString("category")),
        description = o.optString("description"),
        status = JobStatus.from(o.optString("status")),
        needsReview = o.optBoolean("needsReview", false),
        reviewReason = o.optString("reviewReason"),
        remarks = o.optString("remarks"),
        customerCharge = if (o.isNull("customerCharge")) null else o.optDouble("customerCharge"),
        contractorPayable = if (o.isNull("contractorPayable")) null else o.optDouble("contractorPayable"),
        invoiced = o.optBoolean("invoiced", false),
        paid = o.optBoolean("paid", false),
        createdBy = o.optString("createdBy"),
        rawMessage = o.optString("rawMessage")
    )
}
