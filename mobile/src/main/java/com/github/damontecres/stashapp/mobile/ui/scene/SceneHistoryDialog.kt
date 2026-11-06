package com.github.damontecres.stashapp.mobile.ui.scene

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun parseTimeToString(ts: Any?): String? =
    if (ts == null) {
        null
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val dateTimeFormatter = DateTimeFormatter.ofPattern("eee, MMMM d, yyyy h:mm a")
            val dateTime = ZonedDateTime.parse(
                ts.toString(),
                DateTimeFormatter.ISO_DATE_TIME
            ).withZoneSameInstant(ZoneId.systemDefault())
            dateTime.format(dateTimeFormatter)
        } catch (ex: DateTimeParseException) {
            ts.toString()
        }
    } else {
        ts.toString()
    }

@Composable
fun SceneHistoryDialog(
    show: Boolean,
    title: String,
    history: List<Any> = emptyList(),
    onDismissRequest: () -> Unit
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (history.isEmpty()) {
                        Text("No recorded timestamps", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        history.sortedByDescending { it.toString() }.forEach { item ->
                            Text(
                                text = "• ${parseTimeToString(item) ?: item.toString()}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text("Close")
                }
            }
        )
    }
}
