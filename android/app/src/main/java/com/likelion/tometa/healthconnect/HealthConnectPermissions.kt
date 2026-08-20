package com.likelion.tometa.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord

object HealthConnectPermissions {

    val READ_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class)
    )

    const val BACKGROUND_READ_PERMISSION =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val BACKGROUND_READ_PERMISSIONS =
        setOf(BACKGROUND_READ_PERMISSION)
}