package com.example.notificationinterceptor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "NotificationInterceptor"

private const val CHANNEL_ID_LOTS_OF_SLOTS = "lots_of_slots_channel"
private const val CHANNEL_NAME_LOTS_OF_SLOTS = "Lots of Slots Notifications"
private const val NOTIFICATION_ID_LOTS_OF_SLOTS = 2

private const val CHANNEL_ID_SLOTS_AVAILABLE = "slots_available_channel"
private const val CHANNEL_NAME_SLOTS_AVAILABLE = "Slots Available Notifications"
private const val NOTIFICATION_ID_SLOTS_AVAILABLE = 4

private const val FOREGROUND_CHANNEL_ID = "foreground_service_channel"
private const val FOREGROUND_CHANNEL_NAME = "Notification Interceptor Service"
private const val FOREGROUND_NOTIFICATION_ID = 3

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class NotificationInterceptorService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    // Pre-compile regex for performance
    private val slotsAvailableRegexes = listOf(
        Regex(".*slot.*", RegexOption.IGNORE_CASE),
        Regex(".*available.*", RegexOption.IGNORE_CASE)
    )
    private val lotsOfSlotsRegexes = listOf(
        Regex(".*many slots.*", RegexOption.IGNORE_CASE),
        Regex(".*tons of slots.*", RegexOption.IGNORE_CASE)
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationInterceptorService onCreate")
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification posted: ${sbn?.packageName}")

        if (sbn == null) {
            Log.d(TAG, "StatusBarNotification is null, returning.")
            return
        }

        serviceScope.launch {
            val serviceEnabled = dataStore.data.map {
                it[booleanPreferencesKey("service_enabled")] ?: false
            }.first()

            if (!serviceEnabled) {
                Log.d(TAG, "Service is disabled, ignoring notification.")
                return@launch
            }

            val packageName = sbn.packageName
            Log.d(TAG, "Incoming notification package: $packageName")

            try {
                val targetPackageName = dataStore.data.map {
                    it[stringPreferencesKey("target_package_name")] ?: ""
                }.first()
                Log.d(TAG, "Configured target package: $targetPackageName")

                if (packageName != targetPackageName) {
                    Log.d(TAG, "Package name mismatch. Ignoring notification.")
                    return@launch
                }

                val targetGroupName = dataStore.data.map {
                    it[stringPreferencesKey("target_group_name")] ?: ""
                }.first()
                Log.d(TAG, "Configured target group name: $targetGroupName")

                val notification = sbn.notification
                val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                Log.d(TAG, "Notification Title: '$title', Text: '$text'")

                if (targetGroupName.isNotBlank() && !title.contains(targetGroupName, ignoreCase = true)) {
                    Log.d(TAG, "Group name mismatch. Dismissing notification.")
                    cancelNotification(sbn.key)
                    return@launch
                }

                val matchesLotsOfSlots = lotsOfSlotsRegexes.any { it.containsMatchIn(text) }
                val matchesSlotsAvailable = slotsAvailableRegexes.any { it.containsMatchIn(text) }

                if (matchesLotsOfSlots) {
                    Log.d(TAG, "Lots of slots regex matched! Sending high priority notification.")
                    sendLotsOfSlotsNotification()
                    // Do not dismiss original notification
                } else if (matchesSlotsAvailable) {
                    Log.d(TAG, "Slots available regex matched! Sending notification.")
                    sendSlotsAvailableNotification()
                    // Do not dismiss original notification
                } else {
                    Log.d(TAG, "Neither regex matched. Dismissing original notification.")
                    cancelNotification(sbn.key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Notification Interceptor")
            .setContentText("Monitoring notifications in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            FOREGROUND_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(foregroundChannel)

        val lotsOfSlotsChannel = NotificationChannel(
            CHANNEL_ID_LOTS_OF_SLOTS,
            CHANNEL_NAME_LOTS_OF_SLOTS,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(lotsOfSlotsChannel)

        val slotsAvailableChannel = NotificationChannel(
            CHANNEL_ID_SLOTS_AVAILABLE,
            CHANNEL_NAME_SLOTS_AVAILABLE,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(slotsAvailableChannel)
    }

    private fun sendLotsOfSlotsNotification() {
        Log.d(TAG, "Sending 'lots of slots available' notification.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_LOTS_OF_SLOTS)
            .setContentTitle("Lots of slots available!")
            .setContentText("Lots of slots available!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LOTS_OF_SLOTS, notification)
    }

    private fun sendSlotsAvailableNotification() {
        Log.d(TAG, "Sending 'slots available' notification.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SLOTS_AVAILABLE)
            .setContentTitle("Slots available!")
            .setContentText("Slots available!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SLOTS_AVAILABLE, notification)
    }
}