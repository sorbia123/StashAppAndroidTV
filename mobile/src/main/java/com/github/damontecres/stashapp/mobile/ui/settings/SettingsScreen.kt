package com.github.damontecres.stashapp.mobile.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon

enum class PinDialogState {
    NONE,
    SET,
    CHANGE_CURRENT,
    CHANGE_NEW,
    REMOVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String,
    apiKey: String,
    username: String,
    password: String,
    currentTheme: String = "system",
    onThemeSelected: (String) -> Unit = {},
    onBack: () -> Unit,
    onServerReconfigured: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE) }
    
    var currentPin by remember { mutableStateOf(sharedPrefs.getString("pin_code", null)) }
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    var dialogState by remember { mutableStateOf(PinDialogState.NONE) }

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var tempCurrentPinToVerify by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Theme Card (Light / Dark / System Default)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Appearance Theme",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val themeOptions = listOf(
                        "system" to "System Default",
                        "light" to "Light Mode",
                        "dark" to "Dark Mode"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        themeOptions.forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTheme = mode
                                        sharedPrefs.edit().putString("app_theme", mode).apply()
                                        onThemeSelected(mode)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (selectedTheme == mode),
                                    onClick = {
                                        selectedTheme = mode
                                        sharedPrefs.edit().putString("app_theme", mode).apply()
                                        onThemeSelected(mode)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Security Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "App Security (PIN)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (!currentPin.isNullOrBlank()) "PIN protection is enabled" else "PIN protection is disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentPin.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    pinInput = ""
                                    confirmPinInput = ""
                                    errorMessage = null
                                    dialogState = PinDialogState.SET
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Set PIN")
                            }
                        } else {
                            Button(
                                onClick = {
                                    pinInput = ""
                                    errorMessage = null
                                    dialogState = PinDialogState.CHANGE_CURRENT
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Change PIN")
                            }
                            OutlinedButton(
                                onClick = {
                                    pinInput = ""
                                    errorMessage = null
                                    dialogState = PinDialogState.REMOVE
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remove PIN")
                            }
                        }
                    }
                }
            }

            // Server Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Stash Server",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "URL: $serverUrl",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            onServerReconfigured(serverUrl, apiKey, username, password)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Re-configure Server")
                    }
                }
            }
        }
    }

    // Dialogs for PIN management
    when (dialogState) {
        PinDialogState.SET -> {
            AlertDialog(
                onDismissRequest = { dialogState = PinDialogState.NONE },
                title = { Text("Set New PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it; errorMessage = null },
                            label = { Text("New PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { confirmPinInput = it; errorMessage = null },
                            label = { Text("Confirm PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput.isBlank()) {
                            errorMessage = "PIN cannot be blank"
                        } else if (pinInput != confirmPinInput) {
                            errorMessage = "PIN codes do not match"
                        } else {
                            sharedPrefs.edit().putString("pin_code", pinInput.trim()).apply()
                            currentPin = pinInput.trim()
                            dialogState = PinDialogState.NONE
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = PinDialogState.NONE }) {
                        Text("Cancel")
                    }
                }
            )
        }
        PinDialogState.CHANGE_CURRENT -> {
            AlertDialog(
                onDismissRequest = { dialogState = PinDialogState.NONE },
                title = { Text("Verify Current PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it; errorMessage = null },
                            label = { Text("Current PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput == currentPin) {
                            tempCurrentPinToVerify = pinInput
                            pinInput = ""
                            confirmPinInput = ""
                            errorMessage = null
                            dialogState = PinDialogState.CHANGE_NEW
                        } else {
                            errorMessage = "Incorrect PIN"
                        }
                    }) {
                        Text("Next")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = PinDialogState.NONE }) {
                        Text("Cancel")
                    }
                }
            )
        }
        PinDialogState.CHANGE_NEW -> {
            AlertDialog(
                onDismissRequest = { dialogState = PinDialogState.NONE },
                title = { Text("Set New PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it; errorMessage = null },
                            label = { Text("New PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirmPinInput,
                            onValueChange = { confirmPinInput = it; errorMessage = null },
                            label = { Text("Confirm New PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput.isBlank()) {
                            errorMessage = "PIN cannot be blank"
                        } else if (pinInput != confirmPinInput) {
                            errorMessage = "PIN codes do not match"
                        } else {
                            sharedPrefs.edit().putString("pin_code", pinInput.trim()).apply()
                            currentPin = pinInput.trim()
                            dialogState = PinDialogState.NONE
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = PinDialogState.NONE }) {
                        Text("Cancel")
                    }
                }
            )
        }
        PinDialogState.REMOVE -> {
            AlertDialog(
                onDismissRequest = { dialogState = PinDialogState.NONE },
                title = { Text("Confirm PIN to Remove") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it; errorMessage = null },
                            label = { Text("Current PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (errorMessage != null) {
                            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (pinInput == currentPin) {
                            sharedPrefs.edit().remove("pin_code").apply()
                            currentPin = null
                            dialogState = PinDialogState.NONE
                        } else {
                            errorMessage = "Incorrect PIN"
                        }
                    }) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dialogState = PinDialogState.NONE }) {
                        Text("Cancel")
                    }
                }
            )
        }
        PinDialogState.NONE -> {}
    }
}
