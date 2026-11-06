package com.github.damontecres.stashapp.mobile.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.damontecres.stashapp.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onPinConfigured: (pinCode: String?) -> Unit
) {
    var pinCode by remember { mutableStateOf("") }
    var confirmPinCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val savePinAction = {
        if (pinCode.isBlank()) {
            errorMessage = "Please enter a PIN code or tap Skip"
        } else if (pinCode != confirmPinCode) {
            errorMessage = "PIN codes do not match"
        } else {
            onPinConfigured(pinCode.trim())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pin_setup_title)) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = {
                        pinCode = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.pin_code_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = confirmPinCode,
                    onValueChange = {
                        confirmPinCode = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.confirm_pin_code_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { savePinAction() }
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onPinConfigured(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.skip_button))
                    }

                    Button(
                        onClick = savePinAction,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.save_pin_button))
                    }
                }
            }
        }
    }
}
