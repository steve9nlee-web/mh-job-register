package com.jobregister.app

import com.jobregister.app.model.Job
import com.jobregister.app.model.Role

/**
 * Central definition of what each APK flavor is allowed to see and do.
 * Everything role-specific in the UI goes through this object, so the
 * information boundary per app is auditable in one place.
 *
 *  Workflow stage            ADMIN  CLEANER  REPAIRER  INITIATOR
 *  WhatsApp -> AI conversion   x                          x
 *  Job register (scope)       all   cleaning  repair    own view (no money)
 *  Human review / AI flags     x
 *  Status update               x       x        x
 *  Rate card matching          x
 *  Customer billing            x
 *  Contractor payable          x     own pay  own pay
 *  Invoice & payment           x
 *  Pending follow-up           x                          x
 */
object RoleConfig {

    val role: Role = Role.valueOf(BuildConfig.ROLE)

    // ---- capabilities ----
    val canCreateJobs get() = role == Role.ADMIN || role == Role.INITIATOR
    val canReviewFlags get() = role == Role.ADMIN
    val canUpdateStatus get() = role != Role.INITIATOR
    val canSeeCustomerBilling get() = role == Role.ADMIN
    val canSeeContractorPayable get() = role != Role.INITIATOR
    val canManageInvoices get() = role == Role.ADMIN
    val canSeeFollowUp get() = role == Role.ADMIN || role == Role.INITIATOR

    /** Contractors record the work with a before and an after photo. */
    val canAddWorkPhotos get() = role == Role.CLEANER || role == Role.REPAIRER

    /** Contractors get one "Completed" button instead of the status list. */
    val completionOnly get() = role == Role.CLEANER || role == Role.REPAIRER

    /** Only the admin releases a job to the trades. */
    val canApproveJobs get() = role == Role.ADMIN

    /**
     * Which jobs this APK shows at all. A contractor sees nothing until the
     * admin has approved the job, so work is never started on an unchecked
     * request.
     */
    fun visibleJobs(all: List<Job>): List<Job> = when (role) {
        Role.ADMIN -> all
        Role.CLEANER -> all.filter { it.category.isCleaning && it.approved }
        Role.REPAIRER -> all.filter { it.category.isRepair && it.approved }
        Role.INITIATOR -> all
    }.reversed().sortedByDescending { it.date }   // newest job at the top

    val appTitle: String get() = when (role) {
        Role.ADMIN -> "MH Job Register — Admin"
        Role.CLEANER -> "My Cleaning Jobs"
        Role.REPAIRER -> "My Repair Jobs"
        Role.INITIATOR -> "Job Intake"
    }
}
