package com.example.notificationinterceptor.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    // Load settings from DataStore
    LaunchedEffect(Unit) {
        context.dataStore.data.map {
            it[stringPreferencesKey("target_package_name")] ?: "org.telegram.messenger"
        }.collect { targetPackageName = it }

        context.dataStore.data.map {
            it[stringPreferencesKey("target_group_name")] ?: ""
        }.collect { targetGroupName = it }

        context.dataStore.data.map {
            it[booleanPreferencesKey("service_enabled")] ?: false
        }.collect { serviceEnabled = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Service Enabled")
            Switch(
                checked = serviceEnabled,
                onCheckedChange = {
                    serviceEnabled = it
                    coroutineScope.launch {
                        saveServiceState(context, it)
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
            label = { Text("Target Package Name") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Enter the exact name of the Telegram group to filter notifications from.")
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = targetGroupName,
            onValueChange = { targetGroupName = it },
            label = { Text("Target Telegram Group Name") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                saveSettings(context, targetPackageName, targetGroupName)
            }
        }) {
            Text("Save Settings")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            context.startActivity(intent)
        }) {
            Text("Grant Notification Access")
        }
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