package com.jobregister.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jobregister.app.model.Job
import com.jobregister.app.model.Role

/**
 * Central definition of what each APK flavor is allowed to see and do.
 * Everything role-specific in the UI goes through this object, so the
 * information boundary per app is auditable in one place.
 *
 * Admin and Initiator have their role compiled in. The Contractor APK is one
 * app for every executor: the person signs in with the staff code from the
 * `Staff` tab of the sheet, and that code decides whether the phone works as
 * a Cleaner or a Repairer.
 *
 *  Workflow stage            ADMIN  CLEANER  REPAIRER  INITIATOR
 *  WhatsApp paste -> job       x                          x
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

    /** True in the Contractor APK, where the role comes from a staff code. */
    val usesStaffCode: Boolean = BuildConfig.STAFF_CODE_LOGIN

    /** Roles a staff code may unlock in the Contractor APK. */
    val staffRoles = setOf(Role.CLEANER, Role.REPAIRER)

    private const val PREFS = "job_register"

    // Backed by Compose state, so signing in or out redraws the whole app.
    private var signedInRole by mutableStateOf<Role?>(null)

    var staffCode: String = ""
        private set

    /** Restore the signed-in staff role. Call before anything reads [role]. */
    fun load(context: Context) {
        if (!usesStaffCode) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        staffCode = prefs.getString("staff_code", "") ?: ""
        signedInRole = prefs.getString("staff_role", null)
            ?.let { r -> Role.entries.firstOrNull { it.name == r } }
            ?.takeIf { it in staffRoles && staffCode.isNotBlank() }
    }

    fun signIn(context: Context, code: String, newRole: Role) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("staff_code", code)
            .putString("staff_role", newRole.name)
            .apply()
        staffCode = code
        signedInRole = newRole
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("staff_code").remove("staff_role")
            .apply()
        staffCode = ""
        signedInRole = null
    }

    /** Admin and Initiator are always ready; the Contractor app needs a code. */
    val signedIn: Boolean get() = !usesStaffCode || signedInRole != null

    val role: Role
        get() = if (usesStaffCode) signedInRole ?: Role.CLEANER
            else Role.valueOf(BuildConfig.ROLE)

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
     * request — and nothing at all before signing in.
     */
    fun visibleJobs(all: List<Job>): List<Job> {
        if (!signedIn) return emptyList()
        return when (role) {
            Role.ADMIN -> all
            Role.CLEANER -> all.filter { it.category.isCleaning && it.approved }
            Role.REPAIRER -> all.filter { it.category.isRepair && it.approved }
            Role.INITIATOR -> all
        }.reversed().sortedByDescending { it.date }   // newest job at the top
    }

    val appTitle: String get() = when (role) {
        Role.ADMIN -> "MH Job Register — Admin"
        Role.CLEANER -> "My Cleaning Jobs"
        Role.REPAIRER -> "My Repair Jobs"
        Role.INITIATOR -> "Job Intake"
    }
}
