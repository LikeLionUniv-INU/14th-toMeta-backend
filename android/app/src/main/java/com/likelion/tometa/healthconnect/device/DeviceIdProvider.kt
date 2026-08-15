package com.likelion.tometa.healthconnect.device

import android.content.Context
import java.util.UUID

class DeviceIdProvider(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun getOrCreateDeviceId(): String {

        val savedDeviceId =
            preferences.getString(
                DEVICE_ID_KEY,
                null
            )

        if (savedDeviceId != null) {
            return savedDeviceId
        }

        val newDeviceId =
            UUID.randomUUID().toString()

        preferences.edit()
            .putString(
                DEVICE_ID_KEY,
                newDeviceId
            )
            .apply()

        return newDeviceId
    }

    private companion object {

        const val PREFERENCES_NAME =
            "com.likelion.tometa.health_connect"

        const val DEVICE_ID_KEY =
            "device_id"
    }
}