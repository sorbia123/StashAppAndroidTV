package com.github.damontecres.stashapp.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AnalyticsIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        drawRect(color = color, topLeft = Offset(w * 0.1f, h * 0.5f), size = Size(w * 0.2f, h * 0.4f))
        drawRect(color = color, topLeft = Offset(w * 0.4f, h * 0.2f), size = Size(w * 0.2f, h * 0.7f))
        drawRect(color = color, topLeft = Offset(w * 0.7f, h * 0.35f), size = Size(w * 0.2f, h * 0.55f))
    }
}

@Composable
fun TagIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5f.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.15f)
            lineTo(w * 0.55f, h * 0.15f)
            lineTo(w * 0.85f, h * 0.45f)
            lineTo(w * 0.45f, h * 0.85f)
            lineTo(w * 0.15f, h * 0.55f)
            close()
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = color, radius = 3f.dp.toPx(), center = Offset(w * 0.3f, h * 0.3f))
    }
}

@Composable
fun PerformerIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5f.dp.toPx()
        drawCircle(color = color, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.3f), style = Stroke(width = strokeWidth))
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.85f)
            cubicTo(w * 0.2f, h * 0.6f, w * 0.8f, h * 0.6f, w * 0.8f, h * 0.85f)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun PlaylistsIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5f.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.15f, h * 0.25f), end = Offset(w * 0.85f, h * 0.25f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.15f, h * 0.5f), end = Offset(w * 0.85f, h * 0.5f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.15f, h * 0.75f), end = Offset(w * 0.55f, h * 0.75f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier = modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.5f.dp.toPx()
        drawCircle(color = color, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = strokeWidth))
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.1f), end = Offset(w * 0.5f, h * 0.3f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.5f, h * 0.7f), end = Offset(w * 0.5f, h * 0.9f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.1f, h * 0.5f), end = Offset(w * 0.3f, h * 0.5f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        drawLine(color = color, start = Offset(w * 0.7f, h * 0.5f), end = Offset(w * 0.9f, h * 0.5f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHomeScreen(
    serverUrl: String,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToOAnalytics: () -> Unit,
    onNavigateToTags: () -> Unit,
    onNavigateToPerformers: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stash") }
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
            // Row 1: 1. Scene Playlists, 2. O Analytics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GridFeatureCard(
                    title = "Scene Playlists",
                    subtitle = "Saved Filters & Pins",
                    icon = { color -> PlaylistsIcon(color = color) },
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPlaylists
                )

                GridFeatureCard(
                    title = "O Analytics",
                    subtitle = "Trends & Gaps",
                    icon = { color -> AnalyticsIcon(color = color) },
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToOAnalytics
                )
            }

            // Row 2: 3. Tags, 4. Performers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GridFeatureCard(
                    title = "Tags",
                    subtitle = "Tag Manager",
                    icon = { color -> TagIcon(color = color) },
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTags
                )

                GridFeatureCard(
                    title = "Performers",
                    subtitle = "Performer List",
                    icon = { color -> PerformerIcon(color = color) },
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToPerformers
                )
            }

            // Row 3: 5. Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GridFeatureCard(
                    title = "Settings",
                    subtitle = "Security & Server",
                    icon = { color -> SettingsIcon(color = color) },
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSettings
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GridFeatureCard(
    title: String,
    subtitle: String,
    icon: @Composable (Color) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            icon(MaterialTheme.colorScheme.primary)

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
