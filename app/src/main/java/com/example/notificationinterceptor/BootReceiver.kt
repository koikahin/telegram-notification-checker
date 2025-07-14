package com.example.notificationinterceptor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed intent received")
            val serviceEnabled = runBlocking { // Using runBlocking here as onReceive is synchronous
                context.dataStore.data.map {
                    it[booleanPreferencesKey("service_enabled")] ?: false
                }.first()
            }

            if (serviceEnabled) {
                Log.d(TAG, "Service is enabled, starting NotificationInterceptorService")
                val serviceIntent = Intent(context, NotificationInterceptorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d(TAG, "Service is disabled, not starting.")
            }
        }
    }
}