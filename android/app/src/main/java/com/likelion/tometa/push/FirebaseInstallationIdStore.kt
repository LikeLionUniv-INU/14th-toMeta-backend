package com.likelion.tometa.push

import android.content.Context

class FirebaseInstallationIdStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun save(
        installationId: String
    ): Boolean {

        require(
            installationId.isNotBlank()
        ) {
            "Firebase Installation ID가 비어 있습니다."
        }

        return preferences.edit()
            .putString(
                INSTALLATION_ID_KEY,
                installationId
            )
            .commit()
    }

    fun get(): String? =
        preferences.getString(
            INSTALLATION_ID_KEY,
            null
        )

    fun clearIfMatches(
        installationId: String
    ): Boolean {

        val currentInstallationId =
            get()

        if (currentInstallationId != installationId) {
            return true
        }

        return preferences.edit()
            .remove(INSTALLATION_ID_KEY)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME =
            "com.likelion.tometa.firebase_installation"

        const val INSTALLATION_ID_KEY =
            "firebase_installation_id"
    }
}