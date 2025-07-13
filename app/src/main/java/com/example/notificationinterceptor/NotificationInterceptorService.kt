package com.example.notificationinterceptor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import android.util.Log

private const val TAG = "NotificationInterceptor"
private const val CHANNEL_ID_SLOTS_FOUND = "slots_found_channel"
private const val CHANNEL_NAME_SLOTS_FOUND = "Slots Found Notifications"
private const val NOTIFICATION_ID_SLOTS_FOUND = 1

private const val CHANNEL_ID_LOTS_OF_SLOTS = "lots_of_slots_channel"
private const val CHANNEL_NAME_LOTS_OF_SLOTS = "Lots of Slots Notifications"
private const val NOTIFICATION_ID_LOTS_OF_SLOTS = 2

private const val FOREGROUND_CHANNEL_ID = "foreground_service_channel"
private const val FOREGROUND_CHANNEL_NAME = "Notification Interceptor Service"
private const val FOREGROUND_NOTIFICATION_ID = 3

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class NotificationInterceptorService : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationInterceptorService onCreate")
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification posted: ${sbn?.packageName}")

        if (sbn == null) {
            Log.d(TAG, "StatusBarNotification is null, returning.")
            return
        }

        val packageName = sbn.packageName
        Log.d(TAG, "Incoming notification package: $packageName")

        runBlocking {
            val targetPackageName = dataStore.data.map {
                it[stringPreferencesKey("target_package_name")] ?: ""
            }.first()
            Log.d(TAG, "Configured target package: $targetPackageName")

            if (packageName != targetPackageName) {
                Log.d(TAG, "Package name mismatch. Ignoring notification.")
                return@runBlocking
            }

            val targetGroupName = dataStore.data.map {
                it[stringPreferencesKey("target_group_name")] ?: ""
            }.first()
            Log.d(TAG, "Configured target group name: $targetGroupName")

            val regex = listOf(
                ".*slot.*",
                ".*available.*"
            )
            Log.d(TAG, "Hardcoded dismiss regexes: $regex")

            val lotsOfSlotsRegex = listOf(
                ".*many slots.*",
                ".*tons of slots.*"
            )
            Log.d(TAG, "Hardcoded lots of slots regexes: $lotsOfSlotsRegex")

            val notification = sbn.notification
            val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            Log.d(TAG, "Notification Title: '$title', Text: '$text'")

            if (targetGroupName.isNotBlank() && !title.contains(targetGroupName, ignoreCase = true)) {
                Log.d(TAG, "Group name mismatch. Dismissing notification.")
                cancelNotification(sbn.key)
                return@runBlocking
            }

            val matchesLotsOfSlots = lotsOfSlotsRegex.any { text.contains(it, ignoreCase = true) }
            val matchesDismiss = regex.any { text.contains(it, ignoreCase = true) }

            if (matchesLotsOfSlots) {
                Log.d(TAG, "Lots of slots regex matched! Sending high priority notification.")
                sendLotsOfSlotsNotification()
                // Do not dismiss original notification
            } else if (matchesDismiss) {
                Log.d(TAG, "Dismiss regex matched! Dismissing original notification.")
                cancelNotification(sbn.key)
            } else {
                Log.d(TAG, "Neither regex matched. Keeping original notification.")
                // Do nothing, keep the original notification
            }
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                FOREGROUND_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Notification Interceptor")
            .setContentText("Monitoring notifications in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun sendSlotsFoundNotification() {
        Log.d(TAG, "Sending 'slots were found' notification.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SLOTS_FOUND,
                CHANNEL_NAME_SLOTS_FOUND,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SLOTS_FOUND)
            .setContentTitle("Slots were found")
            .setContentText("Slots were found")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SLOTS_FOUND, notification)
    }

    private fun sendLotsOfSlotsNotification() {
        Log.d(TAG, "Sending 'lots of slots available' notification.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID_LOTS_OF_SLOTS,
            CHANNEL_NAME_LOTS_OF_SLOTS,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LOTS_OF_SLOTS)
            .setContentTitle("Lots of slots available!")
            .setContentText("Lots of slots available!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LOTS_OF_SLOTS, notification)
    }
}