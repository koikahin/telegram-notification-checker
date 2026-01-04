package com.example.notificationinterceptor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.example.notificationinterceptor.NotificationInterceptorService
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.notificationinterceptor.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var targetPackageName by remember { mutableStateOf("org.telegram.messenger") }
    var targetGroupName by remember { mutableStateOf("") }
    var serviceEnabled by remember { mutableStateOf(false) }
    var isListenerEnabled by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Check if NotificationListenerService is enabled
    fun checkListenerEnabled(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledListeners.contains(context.packageName)
    }

    // Load settings from DataStore
    LaunchedEffect(Unit) {
        context.dataStore.data.collect { preferences ->
            targetPackageName = preferences[stringPreferencesKey("target_package_name")] ?: "org.telegram.messenger"
            targetGroupName = preferences[stringPreferencesKey("target_group_name")] ?: ""
            serviceEnabled = preferences[booleanPreferencesKey("service_enabled")] ?: false
        }
    }

    // Check listener status periodically
    LaunchedEffect(Unit) {
        while (true) {
            isListenerEnabled = checkListenerEnabled()
            kotlinx.coroutines.delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Service status indicator
        if (!isListenerEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFEBEE))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        "⚠️ Notification Access Required",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "The app cannot intercept notifications without permission. Please grant access below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC62828)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9))
                    .padding(12.dp)
            ) {
                Text(
                    "✓ Notification Access Granted",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Service Enabled")
            Switch(
                checked = serviceEnabled,
                enabled = isListenerEnabled,
                onCheckedChange = {
                    serviceEnabled = it
                    coroutineScope.launch {
                        saveServiceState(context, it)
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            requestServiceRebind(context)
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Enter the package name of the app to intercept notifications from.")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = targetPackageName,
            onValueChange = { targetPackageName = it },
            label = { Text("Target Package Name") },
            isError = targetPackageName.isBlank()
        )
        if (targetPackageName.isBlank()) {
            Text(
                "Package name cannot be empty",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Enter the exact name of the Telegram group to filter notifications from.")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = targetGroupName,
            onValueChange = { targetGroupName = it },
            label = { Text("Target Telegram Group Name") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (targetPackageName.isBlank()) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Package name cannot be empty")
                    }
                } else {
                    coroutineScope.launch {
                        saveSettings(context, targetPackageName, targetGroupName)
                        snackbarHostState.showSnackbar("Settings saved successfully")
                    }
                }
            },
            enabled = targetPackageName.isNotBlank()
        ) {
            Text("Save Settings")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Grant Notification Access")
        }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private suspend fun saveServiceState(context: Context, enabled: Boolean) {
    context.dataStore.edit {
        it[booleanPreferencesKey("service_enabled")] = enabled
    }
}

private suspend fun saveSettings(context: Context, targetPackageName: String, targetGroupName: String) {
    context.dataStore.edit {
        it[stringPreferencesKey("target_package_name")] = targetPackageName
        it[stringPreferencesKey("target_group_name")] = targetGroupName
    }
}