package com.jobregister.app.ai

import com.jobregister.app.model.Job
import com.jobregister.app.model.JobCategory
import com.jobregister.app.model.JobStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * On-device "AI conversion" step of the workflow:
 * WhatsApp message -> normalized job register row.
 *
 * Mirrors the flow chart: normalize dates, identify unit number, classify
 * category (typo-tolerant), detect pending/completed, and flag rows that
 * need human review when information is missing or ambiguous.
 */
object MessageParser {

    data class ParseResult(val job: Job)

    private val unitRegexes = listOf(
        Regex("""\b(?:unit|rumah|house|blk|block)\s*[:#]?\s*([A-Za-z]?-?\d{1,3}[-/]\d{1,3}(?:[-/]\d{1,3})?)""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Za-z]-\d{1,3}-\d{1,3})\b"""),
        Regex("""\b(\d{1,3}-\d{1,3}(?:-\d{1,3})?)\b""")
    )

    // typo-tolerant keyword table per category
    private val categoryKeywords: Map<JobCategory, List<String>> = mapOf(
        JobCategory.DEEP_CLEANING to listOf("deep clean", "deep cleaning", "spring clean", "post reno"),
        JobCategory.CLEANING to listOf("clean", "cleaning", "clening", "claen", "mop", "housekeep", "vacuum", "cuci"),
        JobCategory.PLUMBING to listOf("plumb", "plumbing", "pipe", "leak", "tap", "toilet", "clog", "choke", "sink", "paip"),
        JobCategory.ELECTRICAL to listOf("electric", "eletric", "wiring", "socket", "light", "lamp", "fuse", "trip", "power"),
        JobCategory.AIRCON to listOf("aircon", "air con", "air-con", "ac service", "aircond", "chemical wash", "gas top"),
        JobCategory.GENERAL_REPAIR to listOf("repair", "repare", "fix", "broken", "rosak", "door", "lock", "hinge", "window", "cabinet")
    )

    private val completedWords = listOf("done", "completed", "complete", "finish", "finished", "settled", "siap", "ok already", "done already")
    private val pendingWords = listOf("pending", "waiting", "wait", "not yet", "tomorrow", "later", "schedule", "belum", "next week", "on the way")
    private val notCompletedWords = listOf("cannot", "can't", "unable", "not completed", "failed", "no access", "postpone", "cancel")

    private val dateFormats = listOf("d/M/yyyy", "d/M/yy", "d-M-yyyy", "d-M-yy", "yyyy-MM-dd", "d.M.yyyy")

    fun parse(raw: String, createdBy: String, today: LocalDate = LocalDate.now()): ParseResult {
        val text = raw.trim()
        val lower = text.lowercase()

        // 1. Normalize date
        var date: LocalDate? = null
        val dateMatch = Regex("""\b(\d{1,2}[/.\-]\d{1,2}(?:[/.\-]\d{2,4})?|\d{4}-\d{2}-\d{2})\b""").find(text)
        if (dateMatch != null) {
            val token = dateMatch.value
            for (fmt in dateFormats) {
                try {
                    date = LocalDate.parse(token, DateTimeFormatter.ofPattern(fmt))
                    break
                } catch (_: Exception) { /* try next format */ }
            }
            if (date == null) {
                // day/month with no year -> assume current year
                val dm = Regex("""(\d{1,2})[/.\-](\d{1,2})""").find(token)
                if (dm != null) {
                    try {
                        date = LocalDate.of(today.year, dm.groupValues[2].toInt(), dm.groupValues[1].toInt())
                    } catch (_: Exception) { }
                }
            }
        }
        if (date == null) {
            date = when {
                "yesterday" in lower || "semalam" in lower -> today.minusDays(1)
                "tomorrow" in lower || "esok" in lower -> today.plusDays(1)
                else -> today
            }
        }

        // 2. Identify unit number
        val unit = unitRegexes.firstNotNullOfOrNull { rx ->
            rx.find(text)?.groupValues?.get(1)
        }?.uppercase() ?: ""

        // 3. Classify category (first keyword table hit wins; DEEP before CLEANING)
        val category = categoryKeywords.entries.firstOrNull { (_, words) ->
            words.any { it in lower }
        }?.key ?: JobCategory.UNKNOWN

        // 4. Detect pending / completed
        val status = when {
            notCompletedWords.any { it in lower } -> JobStatus.NOT_COMPLETED
            completedWords.any { it in lower } -> JobStatus.COMPLETED
            pendingWords.any { it in lower } -> JobStatus.PENDING
            else -> JobStatus.PENDING
        }

        // 5. Flag for human review when info is missing
        val reasons = buildList {
            if (unit.isBlank()) add("Unit number not found")
            if (category == JobCategory.UNKNOWN) add("Category could not be classified")
            if (text.length < 8) add("Message too short")
        }

        val job = Job(
            id = "J-" + UUID.randomUUID().toString().take(8).uppercase(),
            date = date.toString(),
            unit = unit,
            category = category,
            description = text.replace(Regex("""\s+"""), " ").take(160),
            status = status,
            needsReview = reasons.isNotEmpty(),
            reviewReason = reasons.joinToString("; "),
            createdBy = createdBy,
            rawMessage = raw
        )
        return ParseResult(job)
    }
}
