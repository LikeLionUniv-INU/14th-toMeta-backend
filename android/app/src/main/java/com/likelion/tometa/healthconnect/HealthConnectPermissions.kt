package com.likelion.tometa.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

object HealthConnectPermissions {

    val READ_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        HealthPermission.getReadPermission(SkinTemperatureRecord::class)
    )

    const val HISTORY_READ_PERMISSION =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    val HISTORY_READ_PERMISSIONS =
        setOf(HISTORY_READ_PERMISSION)

    const val BACKGROUND_READ_PERMISSION =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val BACKGROUND_READ_PERMISSIONS =
        setOf(BACKGROUND_READ_PERMISSION)
}