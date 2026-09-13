package com.freetime.lumastore.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.freetime.lumastore.MainActivity
import com.freetime.lumastore.data.DeveloperNotification

class SystemNotificationManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "luma_store_system_notifications",
        Context.MODE_PRIVATE
    )

    init {
        createChannel()
    }

    fun showNewNotifications(notifications: List<DeveloperNotification>) {
        if (!canPostNotifications()) return

        val knownIds = preferences.getStringSet(KEY_KNOWN_IDS, emptySet()).orEmpty().toMutableSet()

        if (!preferences.getBoolean(KEY_INITIALIZED, false)) {
            knownIds += notifications.map { it.id }
            preferences.edit()
                .putStringSet(KEY_KNOWN_IDS, knownIds)
                .putBoolean(KEY_INITIALIZED, true)
                .apply()
            return
        }

        notifications
            .asSequence()
            .filter { it.readAt == null }
            .filter { it.id !in knownIds }
            .forEach { notification ->
                show(notification)
                knownIds += notification.id
            }

        preferences.edit().putStringSet(KEY_KNOWN_IDS, knownIds).apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private fun show(notification: DeveloperNotification) {
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_DEVELOPER, true)
            putExtra(EXTRA_NOTIFICATION_ID, notification.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notification.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val systemNotification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(notification.title.ifBlank { "Luma Store" })
            .setContentText(notification.message ?: "New developer notification")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(notification.message ?: "New developer notification")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(appContext)
            .notify(notification.id.hashCode(), systemNotification)
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Developer notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Status updates and review messages for submitted apps"
        }

        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_OPEN_DEVELOPER = "open_developer"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val CHANNEL_ID = "developer_notifications"
        private const val KEY_KNOWN_IDS = "known_ids"
        private const val KEY_INITIALIZED = "initialized"
    }
}
