package com.jobregister.app.data

import android.util.Base64
import com.jobregister.app.model.Customer
import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Thin JSON client for the Google Apps Script web app that fronts the
 * Job Register spreadsheet (see backend/Code.gs in the repo).
 *
 * GET  <url>            -> {"jobs":[{...}, ...]}
 * POST <url> {job json} -> upserts one row by ID
 */
object SheetApi {

    data class RemoteData(
        val jobs: List<Job>,
        val customers: List<Customer>,
        val apartments: Map<String, String>,
        val services: List<String>,
        val serviceDetails: Map<String, String> = emptyMap(),
        val photos: List<JobPhoto> = emptyList()
    )

    /** One row of the Photos tab: a job photo held in the shared Drive folder. */
    data class JobPhoto(
        val jobId: String,
        val fileId: String,
        val filename: String = "",
        val url: String = "",
        val uploadedAt: String = "",
        val kind: String = "job"     // "job", "before" or "after"
    )

    suspend fun fetchAll(baseUrl: String, key: String = ""): RemoteData = withContext(Dispatchers.IO) {
        val conn = open(withKey(baseUrl, key), "GET")
        try {
            val body = conn.inputStream.bufferedReader().readText()
            val obj = JSONObject(body)
            if (obj.has("error")) throw IllegalStateException(obj.getString("error"))

            val jobsArr = obj.optJSONArray("jobs") ?: JSONArray()
            val jobs = (0 until jobsArr.length()).map { fromJson(jobsArr.getJSONObject(it)) }

            val custArr = obj.optJSONArray("customers") ?: JSONArray()
            val customers = (0 until custArr.length()).map { i ->
                val c = custArr.getJSONObject(i)
                Customer(
                    apartment = c.optString("apartment"),
                    unit = c.optString("unit"),
                    service = c.optString("service"),
                    customerName = c.optString("customerName")
                )
            }.filter { it.unit.isNotBlank() }

            val aptArr = obj.optJSONArray("apartments") ?: JSONArray()
            val apartments = mutableMapOf<String, String>()
            for (i in 0 until aptArr.length()) {
                val a = aptArr.getJSONObject(i)
                val code = a.optString("code")
                if (code.isNotBlank()) apartments[code] = a.optString("name")
            }

            val infoArr = obj.optJSONArray("serviceInfo") ?: JSONArray()
            val serviceDetails = mutableMapOf<String, String>()
            val infoNames = mutableListOf<String>()
            for (i in 0 until infoArr.length()) {
                val o = infoArr.getJSONObject(i)
                val n = o.optString("name")
                if (n.isNotBlank()) {
                    infoNames.add(n)
                    serviceDetails[n] = o.optString("details")
                }
            }
            val svcArr = obj.optJSONArray("services") ?: JSONArray()
            val services = if (infoNames.isNotEmpty()) infoNames
            else (0 until svcArr.length())
                .map { svcArr.getString(it) }.filter { it.isNotBlank() }

            val photoArr = obj.optJSONArray("photos") ?: JSONArray()
            val photos = (0 until photoArr.length()).map { i ->
                val p = photoArr.getJSONObject(i)
                JobPhoto(
                    jobId = p.optString("jobId"),
                    fileId = p.optString("fileId"),
                    filename = p.optString("filename"),
                    url = p.optString("url"),
                    uploadedAt = p.optString("uploadedAt"),
                    kind = p.optString("kind").ifBlank { "job" }
                )
            }.filter { it.jobId.isNotBlank() && it.fileId.isNotBlank() }

            RemoteData(jobs, customers, apartments, services, serviceDetails, photos)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun upsertJob(baseUrl: String, key: String, job: Job): Unit = withContext(Dispatchers.IO) {
        val conn = open(withKey(baseUrl, key), "POST")
        try {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(toJson(job).toString().toByteArray()) }
            conn.inputStream.bufferedReader().readText() // drain / follow Apps Script redirect
        } finally {
            conn.disconnect()
        }
    }

    /** POST an arbitrary action body (customer/apartment/service changes). */
    suspend fun postAction(baseUrl: String, key: String, body: JSONObject): Unit =
        withContext(Dispatchers.IO) {
            val conn = open(withKey(baseUrl, key), "POST")
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.inputStream.bufferedReader().readText()
            } finally {
                conn.disconnect()
            }
        }

    /** Download one job photo through the web app. Returns the JPEG bytes. */
    suspend fun fetchPhoto(baseUrl: String, key: String, fileId: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val keyed = withKey(baseUrl, key)
            val url = keyed + (if ("?" in keyed) "&" else "?") +
                "photo=" + URLEncoder.encode(fileId, "UTF-8")
            val conn = open(url, "GET")
            try {
                val obj = JSONObject(conn.inputStream.bufferedReader().readText())
                val b64 = obj.optString("data")
                if (b64.isBlank()) null else Base64.decode(b64, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

    private fun withKey(baseUrl: String, key: String): String =
        if (key.isBlank()) baseUrl
        else baseUrl + (if ("?" in baseUrl) "&" else "?") + "key=" + URLEncoder.encode(key, "UTF-8")

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
        put("rooms", j.rooms); put("updatedBy", j.updatedBy)
        put("startedAt", j.startedAt); put("completedAt", j.completedAt)
        put("approvedBy", j.approvedBy); put("approvedAt", j.approvedAt)
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
        rawMessage = o.optString("rawMessage"),
        rooms = o.optString("rooms"),
        updatedBy = o.optString("updatedBy"),
        startedAt = o.optString("startedAt"),
        completedAt = o.optString("completedAt"),
        approvedBy = o.optString("approvedBy"),
        approvedAt = o.optString("approvedAt")
    )
}
