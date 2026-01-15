package com.example.slotnotificationmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.provider.Settings
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "SlotNotificationMonitor"
private const val ENABLE_FOREGROUND_SERVICE =
        false // Set to true to enable persistent foreground notification

private const val FOREGROUND_CHANNEL_ID = "foreground_service_channel"
private const val FOREGROUND_CHANNEL_NAME = "monitoring slot messages..."
private const val FOREGROUND_NOTIFICATION_ID = 1

private const val CHANNEL_ID_LOTS_OF_SLOTS = "lots_of_slots_alarm_channel"
private const val CHANNEL_NAME_LOTS_OF_SLOTS = "Lots of Slots Notifications"
private const val NOTIFICATION_ID_LOTS_OF_SLOTS = 2

private const val CHANNEL_ID_SLOTS_AVAILABLE = "slots_available_channel"
private const val CHANNEL_NAME_SLOTS_AVAILABLE = "Slots Available Notifications"
private const val NOTIFICATION_ID_SLOTS_AVAILABLE = 3

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SlotNotificationMonitorService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var settingsCollectorJob: Job? = null

    // Cached settings
    @Volatile private var cachedServiceEnabled = false
    @Volatile private var cachedTargetPackageName = ""

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SlotNotificationMonitorService onCreate")
        createNotificationChannels()
        if (ENABLE_FOREGROUND_SERVICE) {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        }
        startSettingsCollector()
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsCollectorJob?.cancel()
        Log.d(TAG, "SlotNotificationMonitorService onDestroy")
    }

    private fun startSettingsCollector() {
        settingsCollectorJob =
                serviceScope.launch {
                    dataStore.data.collect { preferences ->
                        cachedServiceEnabled =
                                preferences[booleanPreferencesKey("service_enabled")] ?: false
                        cachedTargetPackageName =
                                preferences[stringPreferencesKey("target_package_name")]
                                        ?: "org.telegram.messenger"
                        Log.d(
                                TAG,
                                "Settings updated: enabled=$cachedServiceEnabled, package=$cachedTargetPackageName"
                        )
                    }
                }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification posted: ${sbn?.packageName}")

        if (sbn == null) {
            Log.d(TAG, "StatusBarNotification is null, returning.")
            return
        }

        serviceScope.launch {
            if (!cachedServiceEnabled) {
                Log.d(TAG, "Service is disabled, ignoring notification.")
                return@launch
            }

            val packageName = sbn.packageName
            Log.d(TAG, "Incoming notification package: $packageName")

            try {
                if (packageName != cachedTargetPackageName) {
                    Log.d(TAG, "Package name mismatch. Ignoring notification.")
                    return@launch
                }

                val notification = sbn.notification
                val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text =
                        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                                ?: ""
                Log.d(TAG, "Notification Title: '$title', Text: '$text'")
                if (text.matchesDisallowed()) {
                    Log.d(TAG, "Matched NA pattern. No notification sent.")
                } else {
                    Log.d(TAG, "Text does not match NA. Sending notification.")
                    if (text.matchesPriority()) {
                        Log.d(
                                TAG,
                                "Lots of slots regex matched! Sending high priority notification."
                        )
                        sendLotsOfSlotsNotification(text)
                    } else {
                        sendSlotsAvailableNotification(text)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification", e)
            }
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle("monitoring slots...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build()
    }

    private fun createNotificationChannels() {
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (ENABLE_FOREGROUND_SERVICE) {
            val foregroundChannel =
                    NotificationChannel(
                            FOREGROUND_CHANNEL_ID,
                            FOREGROUND_CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                    )
            notificationManager.createNotificationChannel(foregroundChannel)
        }

        val lotsOfSlotsChannel =
                NotificationChannel(
                        CHANNEL_ID_LOTS_OF_SLOTS,
                        CHANNEL_NAME_LOTS_OF_SLOTS,
                        NotificationManager.IMPORTANCE_HIGH
                )
        val audioAttributes =
                AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
        lotsOfSlotsChannel.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
        notificationManager.createNotificationChannel(lotsOfSlotsChannel)

        val slotsAvailableChannel =
                NotificationChannel(
                        CHANNEL_ID_SLOTS_AVAILABLE,
                        CHANNEL_NAME_SLOTS_AVAILABLE,
                        NotificationManager.IMPORTANCE_DEFAULT
                )
        notificationManager.createNotificationChannel(slotsAvailableChannel)
    }

    private fun sendLotsOfSlotsNotification(message: String) {
        Log.d(TAG, "Sending 'lots of slots available' notification.")
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existingText =
                getActiveNotificationText(notificationManager, NOTIFICATION_ID_LOTS_OF_SLOTS)
        val combinedText = if (existingText.isNotEmpty()) "$message\n$existingText" else message

        val notification =
                NotificationCompat.Builder(this, CHANNEL_ID_LOTS_OF_SLOTS)
                        .setContentTitle("!! Lots of slots available !!")
                        .setContentText(message) // Show newest message in collapsed view
                        .setStyle(NotificationCompat.BigTextStyle().bigText(combinedText))
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .build()

        notification.flags = notification.flags or Notification.FLAG_INSISTENT

        notificationManager.notify(NOTIFICATION_ID_LOTS_OF_SLOTS, notification)
    }

    private fun sendSlotsAvailableNotification(message: String) {
        Log.d(TAG, "Sending 'slots available' notification.")
        val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val existingText =
                getActiveNotificationText(notificationManager, NOTIFICATION_ID_SLOTS_AVAILABLE)
        val combinedText = if (existingText.isNotEmpty()) "$message\n$existingText" else message

        val isPriorityActive = isPriorityNotificationActive(notificationManager)
        Log.d(TAG, "Priority notification active: $isPriorityActive")

        val builder =
                NotificationCompat.Builder(this, CHANNEL_ID_SLOTS_AVAILABLE)
                        .setContentTitle("Slots may be available!")
                        .setContentText(message) // Show newest message in collapsed view
                        .setStyle(NotificationCompat.BigTextStyle().bigText(combinedText))
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (isPriorityActive) {
            builder.setSilent(true)
        }

        notificationManager.notify(NOTIFICATION_ID_SLOTS_AVAILABLE, builder.build())
    }

    private fun isPriorityNotificationActive(notificationManager: NotificationManager): Boolean {
        return try {
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.any { it.id == NOTIFICATION_ID_LOTS_OF_SLOTS }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active notifications", e)
            false
        }
    }

    private fun getActiveNotificationText(
            notificationManager: NotificationManager,
            notificationId: Int
    ): String {
        return try {
            val activeNotifications = notificationManager.activeNotifications
            val existingNotification = activeNotifications.find { it.id == notificationId }

            val extras = existingNotification?.notification?.extras
            val bigText = extras?.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            if (!bigText.isNullOrEmpty()) bigText else (text ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving active notification text", e)
            ""
        }
    }
}
