package com.vantra.music.core.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vantra.music.ui.screens.update.maybeNotifyUpdate
import java.util.concurrent.TimeUnit

/**
 * Runs in the background about every 15 minutes (Android's lowest interval) while there is a
 * network. When the update site has a version newer than the installed one it posts the
 * "Vantra update" notification, even if the app was never opened. maybeNotifyUpdate keeps its
 * usual limit of three reminders per release, so this does not spam.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!getUpdateNotificationsSetting(applicationContext)) return Result.success()
        val release = getLatestRelease(force = true).getOrNull() ?: return Result.success()
        if (isNewerThanInstalled(release.version)) maybeNotifyUpdate(applicationContext, release)
        return Result.success()
    }
}

fun scheduleUpdateChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork("vantra_update_check", ExistingPeriodicWorkPolicy.KEEP, request)
}
