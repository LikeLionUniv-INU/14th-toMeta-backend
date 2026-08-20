package com.likelion.tometa.healthconnect

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures

class HealthConnectManager(
    private val context: Context
) {

    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun isAvailable(): Boolean {
        return getSdkStatus() ==
                HealthConnectClient.SDK_AVAILABLE
    }

    fun getClient(): HealthConnectClient {
        check(isAvailable()) {
            "Health Connect를 사용할 수 없습니다."
        }

        return HealthConnectClient.getOrCreate(context)
    }

    fun isHistoryReadAvailable(): Boolean {
        if (!isAvailable()) {
            return false
        }

        return getClient()
            .features
            .getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    fun isBackgroundReadAvailable(): Boolean {
        if (!isAvailable()) {
            return false
        }

        return getClient()
            .features
            .getFeatureStatus(
                HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            ) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (!isAvailable()) {
            return emptySet()
        }

        return getClient()
            .permissionController
            .getGrantedPermissions()
    }

    suspend fun hasAllPermissions(): Boolean {
        val grantedPermissions =
            getGrantedPermissions()

        return grantedPermissions.containsAll(
            HealthConnectPermissions.READ_PERMISSIONS
        )
    }

    suspend fun hasHistoryReadPermission(): Boolean {
        if (!isHistoryReadAvailable()) {
            return false
        }

        val grantedPermissions =
            getGrantedPermissions()

        return HealthConnectPermissions.HISTORY_READ_PERMISSION in
                grantedPermissions
    }

    suspend fun hasBackgroundReadPermission(): Boolean {
        if (!isBackgroundReadAvailable()) {
            return false
        }

        val grantedPermissions =
            getGrantedPermissions()

        return HealthConnectPermissions.BACKGROUND_READ_PERMISSION in
                grantedPermissions
    }

    fun openHealthConnectSettings() {
        val intent =
            Intent(
                HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
            )

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)
    }
}