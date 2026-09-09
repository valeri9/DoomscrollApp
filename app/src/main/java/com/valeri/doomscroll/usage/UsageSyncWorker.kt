package com.valeri.doomscroll.usage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The only periodic wake-up in the app. Everything else is event-driven.
 *
 * Deliberately unconstrained (no network, no charging requirement) and infrequent — reading
 * UsageStatsManager is cheap, and half-hourly resolution is plenty for a screen-time chart.
 */
class UsageSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val importer = UsageStatsImporter(applicationContext)
        return runCatching {
            if (!importer.hasBackfilled()) importer.backfill()
            importer.syncRecent()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val NAME = "usage-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
