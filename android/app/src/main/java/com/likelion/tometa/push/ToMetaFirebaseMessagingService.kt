package com.likelion.tometa.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService

class ToMetaFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onRegistered(
        installationId: String
    ) {
        val saved =
            FirebaseInstallationIdStore(this)
                .save(installationId)

        if (!saved) {
            Log.e(
                TAG,
                "Firebase Installation ID 저장에 실패했습니다."
            )
        }
    }

    override fun onUnregistered(
        installationId: String
    ) {
        val cleared =
            FirebaseInstallationIdStore(this)
                .clearIfMatches(installationId)

        if (!cleared) {
            Log.e(
                TAG,
                "Firebase Installation ID 삭제에 실패했습니다."
            )
        }
    }

    private companion object {
        const val TAG =
            "ToMetaFirebaseMessaging"
    }
}