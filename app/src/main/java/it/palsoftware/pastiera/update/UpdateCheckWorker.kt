package it.palsoftware.pastiera.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Periodic worker that checks for new app updates using the existing
 * GitHub-based update checker and shows a notification when a new
 * version is available.
 */
class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        if (!shouldUseGithubUpdateChecks(context)) {
            return Result.success()
        }

        // Use a latch to bridge the async callback-based API with the
        // synchronous Worker API.
        val resultRef = AtomicReference<UpdateCheckResult?>()
        val completedRef = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        checkForUpdateNotices(
            context = context,
            releaseChannel = BuildConfig.RELEASE_CHANNEL,
            // Respect releases dismissed by the user via the dialog.
            ignoreDismissedReleases = true
        ) { result ->
            resultRef.set(result)
            completedRef.set(true)
            latch.countDown()
        }

        // Wait for the network response (or timeout).
        val awaitSuccess = latch.await(30, TimeUnit.SECONDS)
        val result = resultRef.get()
        if (!awaitSuccess || !completedRef.get() || result == null || !result.successful) {
            // Network error or timeout: ask WorkManager to retry later.
            return Result.retry()
        }

        if (result.hasAnnouncement) {
            if (result.releaseTag != null && result.displayName != null) {
                NotificationHelper.showUpdateAvailableNotification(
                    context = context,
                    displayName = result.displayName,
                    releasePageUrl = result.releasePageUrl,
                    isNightlyUpdate = result.isNightlyUpdate
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "pastiera_update_check"

        /**
         * Schedules a periodic background update check every 24 hours.
         * If already scheduled, the existing work is kept.
         */
        fun schedule(context: Context) {
            if (!shouldUseGithubUpdateChecks(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                24, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
