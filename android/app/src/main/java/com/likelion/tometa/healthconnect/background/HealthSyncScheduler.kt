package com.likelion.tometa.healthconnect.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object HealthSyncScheduler {

    private const val UNIQUE_WORK_NAME =
        "tometa_health_sync"

    private const val WORK_TAG =
        "health_connect_sync"

    private const val SYNC_INTERVAL_HOURS =
        3L

    fun schedule(
        context: Context
    ) {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED
                )
                .build()

        val request =
            PeriodicWorkRequestBuilder<HealthSyncWorker>(
                SYNC_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setInitialDelay(
                    SYNC_INTERVAL_HOURS,
                    TimeUnit.HOURS
                )
                .setConstraints(
                    constraints
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(
                    WORK_TAG
                )
                .build()

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }

    fun cancel(
        context: Context
    ) {
        WorkManager
            .getInstance(
                context.applicationContext
            )
            .cancelUniqueWork(
                UNIQUE_WORK_NAME
            )
    }
}