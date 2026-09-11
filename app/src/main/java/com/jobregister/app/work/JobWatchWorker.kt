package com.jobregister.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jobregister.app.data.JobRepository
import com.jobregister.app.util.Notifier
import java.util.concurrent.TimeUnit

/**
 * Keeps the register up to date while the app is closed. Each run pulls the
 * spreadsheet and notifies the phone about anything that changed, so an
 * admin or an initiator hears about a completed job without opening the app.
 */
class JobWatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = JobRepository.get(applicationContext)
        val error = repo.pull() ?: run {
            Notifier.notifyChanges(applicationContext, repo.changes.value)
            repo.clearChanges()
            return Result.success()
        }
        // Offline or the sheet was unreachable — try again on the next run.
        return if (error.contains("sync url", ignoreCase = true)) Result.success()
        else Result.retry()
    }

    companion object {
        private const val NAME = "job-watch"

        /** Android allows a 15 minute floor for repeating background work. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<JobWatchWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
