package com.github.damontecres.stashapp.ui.components.scene

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.stashapp.R
import com.github.damontecres.stashapp.ui.components.ScrollableDialog
import com.github.damontecres.stashapp.views.parseTimeToString

@Composable
fun SceneHistoryDialog(
    show: Boolean,
    playHistory: List<Any>,
    oHistory: List<Any>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (show) {
        ScrollableDialog(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
        ) {
            item {
                Text(
                    text = stringResource(R.string.history),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(
                    text = stringResource(R.string.stashapp_play_count),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (playHistory.isEmpty()) {
                item {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(playHistory.sortedByDescending { it.toString() }) { item ->
                    Text(
                        text = parseTimeToString(item) ?: item.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.stashapp_o_count),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (oHistory.isEmpty()) {
                item {
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(oHistory.sortedByDescending { it.toString() }) { item ->
                    Text(
                        text = parseTimeToString(item) ?: item.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
