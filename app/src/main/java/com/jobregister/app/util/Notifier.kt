package com.jobregister.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jobregister.app.MainActivity
import com.jobregister.app.R
import com.jobregister.app.RoleConfig
import com.jobregister.app.data.JobRepository
import com.jobregister.app.model.Role

/**
 * Phone notifications for job activity. What is worth telling someone about
 * depends on the app they are holding: the admin follows everything, the
 * person who raised a job wants to hear when it is done, and a contractor
 * wants to hear about work coming their way — never about their own updates.
 */
object Notifier {

    private const val CHANNEL_ID = "job_updates"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Job updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "New jobs and status changes from the Job Register" }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    data class Message(val jobId: String, val title: String, val body: String)

    /** What this role should hear about; changes it shouldn't are dropped. */
    fun messagesFor(changes: List<JobRepository.JobChange>): List<Message> =
        changes.mapNotNull { change ->
            val job = change.job
            val where = listOf(job.unit.ifBlank { "Unit ?" }, job.category.label)
                .joinToString("  ·  ")
            val who = job.updatedBy.takeIf { it.isNotBlank() }?.let { " by $it" } ?: ""
            val text: Pair<String, String>? = when (RoleConfig.role) {
                Role.ADMIN ->
                    if (change.isNew) "New job ${job.id}" to "$where\n${job.description}"
                    else "${job.status.label} — ${job.unit}" to "$where$who"
                Role.INITIATOR ->
                    // The initiator raised the job, so only progress matters.
                    if (change.isNew) null
                    else "${job.status.label} — ${job.unit}" to "$where$who"
                Role.CLEANER, Role.REPAIRER ->
                    if (change.isNew && RoleConfig.visibleJobs(listOf(job)).isNotEmpty())
                        "New job for you" to "$where\n${job.description}"
                    else null
            }
            text?.let { Message(job.id, it.first, it.second) }
        }

    /** Post one notification per change, tagged by job so they replace cleanly. */
    fun notifyChanges(context: Context, changes: List<JobRepository.JobChange>) {
        val messages = messagesFor(changes)
        if (messages.isEmpty()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        messages.forEach { message ->
            val note = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(message.title)
                .setContentText(message.body.replace("\n", "  ·  "))
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context)
                    .notify(message.jobId.hashCode(), note)
            } catch (_: SecurityException) {
                // Notification permission refused — the in-app list still updates.
            }
        }
    }
}
