package com.perde.oynatici

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Takip edilen listeleri uygulama kapalıyken de 6 saatte bir kontrol eder; yeni varsa "Ekle ve indir" bildirimi gönderir. */
class FollowWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Dl.init(c)
        if (Dl.follows.isEmpty()) return Result.success()
        return try {
            Web.init(c)
            Dl.checkFollows(c, notify = true)
            Result.success()
        } catch (e: Exception) { Report.err("takip kontrolü", e); Result.retry() }
    }

    companion object {
        fun schedule(ctx: Context) = try {
            val req = PeriodicWorkRequestBuilder<FollowWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("takip", ExistingPeriodicWorkPolicy.KEEP, req)
            Unit
        } catch (e: Exception) { Report.err("takip zamanlama", e) }
    }
}
