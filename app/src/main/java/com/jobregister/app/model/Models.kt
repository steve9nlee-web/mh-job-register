package com.jobregister.app.model

/** Which app flavor is running. Compiled in per-APK via BuildConfig.ROLE. */
enum class Role { ADMIN, CLEANER, REPAIRER, INITIATOR }

enum class JobStatus(val label: String) {
    PENDING("Pending"),
    WAITING("Waiting / Parts"),
    COMPLETED("Completed"),
    NOT_COMPLETED("Not Completed");

    companion object {
        fun from(s: String?): JobStatus =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: PENDING
    }
}

enum class JobCategory(val label: String, val isCleaning: Boolean) {
    CLEANING("Cleaning", true),
    DEEP_CLEANING("Deep Cleaning", true),
    PLUMBING("Plumbing", false),
    ELECTRICAL("Electrical", false),
    AIRCON("Aircon Service", false),
    GENERAL_REPAIR("General Repair", false),
    UNKNOWN("Unclassified", false);

    val isRepair: Boolean get() = !isCleaning && this != UNKNOWN

    companion object {
        fun from(s: String?): JobCategory =
            entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** One row of the Job Register spreadsheet. */
data class Job(
    val id: String,
    val date: String,            // yyyy-MM-dd
    val unit: String,            // e.g. "A-12-03"
    val category: JobCategory,
    val description: String,
    val status: JobStatus = JobStatus.PENDING,
    val needsReview: Boolean = false,
    val reviewReason: String = "",
    val remarks: String = "",
    val customerCharge: Double? = null,   // filled by rate-card matching
    val contractorPayable: Double? = null,
    val invoiced: Boolean = false,
    val paid: Boolean = false,
    val createdBy: String = "",
    val rawMessage: String = ""           // original WhatsApp text
) {
    /** Ready for billing = completed and reviewed. */
    val billable: Boolean get() = status == JobStatus.COMPLETED && !needsReview
    val needsFollowUp: Boolean
        get() = status == JobStatus.PENDING || status == JobStatus.WAITING || status == JobStatus.NOT_COMPLETED
}

/** Rate card row: what the customer pays and what the contractor receives. */
data class Rate(
    val category: JobCategory,
    val customerRate: Double,
    val contractorPayable: Double
)

object RateCard {
    val default: List<Rate> = listOf(
        Rate(JobCategory.CLEANING, 80.0, 50.0),
        Rate(JobCategory.DEEP_CLEANING, 180.0, 120.0),
        Rate(JobCategory.PLUMBING, 150.0, 100.0),
        Rate(JobCategory.ELECTRICAL, 160.0, 110.0),
        Rate(JobCategory.AIRCON, 140.0, 95.0),
        Rate(JobCategory.GENERAL_REPAIR, 120.0, 80.0)
    )

    fun match(category: JobCategory): Rate? = default.firstOrNull { it.category == category }
}
