package com.example.notificationinterceptor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed intent received")
            
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val serviceEnabled = context.dataStore.data.map {
                        it[booleanPreferencesKey("service_enabled")] ?: false
                    }.first()

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
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading service state", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}