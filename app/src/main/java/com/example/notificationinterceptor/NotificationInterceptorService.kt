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
import com.example.notificationinterceptor.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "NotificationInterceptor"

private const val FOREGROUND_CHANNEL_ID = "foreground_service_channel"
private const val FOREGROUND_CHANNEL_NAME = "monitoring slot messages..."
private const val FOREGROUND_NOTIFICATION_ID = 1

private const val CHANNEL_ID_LOTS_OF_SLOTS = "lots_of_slots_channel"
private const val CHANNEL_NAME_LOTS_OF_SLOTS = "Lots of Slots Notifications"
private const val NOTIFICATION_ID_LOTS_OF_SLOTS = 2

private const val CHANNEL_ID_SLOTS_AVAILABLE = "slots_available_channel"
private const val CHANNEL_NAME_SLOTS_AVAILABLE = "Slots Available Notifications"
private const val NOTIFICATION_ID_SLOTS_AVAILABLE = 3

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class NotificationInterceptorService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var settingsCollectorJob: Job? = null

    // Cached settings
    @Volatile
    private var cachedServiceEnabled = false
    @Volatile
    private var cachedTargetPackageName = ""

    private val lotsOfSlotsRegexes = listOf(
        Regex(".*many slots.*", RegexOption.IGNORE_CASE),
        Regex(".*tons of slots.*", RegexOption.IGNORE_CASE)
    )
    private val naRegexes = listOf(
        Regex(".*NA.*", RegexOption.IGNORE_CASE),
        Regex(".*Not Available.*", RegexOption.IGNORE_CASE)
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationInterceptorService onCreate")
        createNotificationChannels()
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification())
        startSettingsCollector()
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsCollectorJob?.cancel()
        Log.d(TAG, "NotificationInterceptorService onDestroy")
    }

    private fun startSettingsCollector() {
        settingsCollectorJob = serviceScope.launch {
            dataStore.data.collect { preferences ->
                cachedServiceEnabled = preferences[booleanPreferencesKey("service_enabled")] ?: false
                cachedTargetPackageName = preferences[stringPreferencesKey("target_package_name")] ?: "org.telegram.messenger"
                Log.d(TAG, "Settings updated: enabled=$cachedServiceEnabled, package=$cachedTargetPackageName")
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
                val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                Log.d(TAG, "Notification Title: '$title', Text: '$text'")

                val matchesLotsOfSlots = lotsOfSlotsRegexes.any { it.containsMatchIn(text) }
                val matchesNA = naRegexes.any { it.containsMatchIn(text) }

                if (matchesLotsOfSlots) {
                    Log.d(TAG, "Lots of slots regex matched! Sending high priority notification.")
                    sendLotsOfSlotsNotification()
                } else if (!matchesNA) {
                    Log.d(TAG, "Text does not match NA. Sending notification.")
                    sendSlotsAvailableNotification()
                } else {
                    Log.d(TAG, "Matched NA pattern. Match found in text: '$text'. No notification sent.")
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val foregroundChannel = NotificationChannel(
            FOREGROUND_CHANNEL_ID,
            FOREGROUND_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
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
            .setContentTitle("!! Lots of slots available !!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIFICATION_ID_LOTS_OF_SLOTS, notification)
    }

    private fun sendSlotsAvailableNotification() {
        Log.d(TAG, "Sending 'slots available' notification.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SLOTS_AVAILABLE)
            .setContentTitle("Slots may be available!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(NOTIFICATION_ID_SLOTS_AVAILABLE, notification)
    }
}
