package com.likelion.tometa.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.likelion.tometa.MainActivity
import com.likelion.tometa.R

class ToMetaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onRegistered(installationId: String) {
        val saved = FirebaseInstallationIdStore(this).save(installationId)

        if (!saved) {
            Log.e(TAG, "Firebase Installation ID 저장에 실패했습니다.")
        }
    }

    override fun onUnregistered(installationId: String) {
        val cleared = FirebaseInstallationIdStore(this).clearIfMatches(installationId)

        if (!cleared) {
            Log.e(TAG, "Firebase Installation ID 삭제에 실패했습니다.")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: DEFAULT_TITLE

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: return

        showNotification(title, body)
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(this)
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        PushNotificationChannel.ensureCreated(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PushNotificationChannel.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val TAG = "ToMetaFirebaseMessaging"
        const val DEFAULT_TITLE = "toMeta"
    }
}