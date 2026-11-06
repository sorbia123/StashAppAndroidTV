package com.github.damontecres.stashapp.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OCountDashboardScreen(
    viewModel: OCountDashboardViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scene 'O' Count Analytics") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error: ${state.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadAnalytics() }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary Cards Row
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SummaryCard(
                                    title = "Total O Count",
                                    value = "${state.totalOCount}",
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryCard(
                                    title = "Longest Gap",
                                    value = "${state.longestGapDays} days",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Monthly Breakdown Header
                        item {
                            Text(
                                text = "Monthly 'O' Count Trend",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (state.monthlyCounts.isEmpty()) {
                            item {
                                Text("No monthly data available", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            item {
                                ProperLineChart(
                                    data = state.monthlyCounts.entries.map { it.key to it.value }
                                )
                            }
                        }

                        // Gap Between Events Header
                        item {
                            Text(
                                text = "Gap Between 'O' Events (in Days)",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }

                        if (state.gapEvents.isEmpty()) {
                            item {
                                Text("No gap data available", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            item {
                                GapBarChart(
                                    data = state.gapEvents.take(30)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun formatXAxisLabel(key: String): String {
    return try {
        val parts = key.split("-")
        if (parts.size >= 2) {
            val year = parts[0]
            val monthNum = parts[1].toIntOrNull() ?: 1
            val monthName = Month.of(monthNum).getDisplayName(
                TextStyle.SHORT,
                Locale.ENGLISH
            )
            if (parts.size == 3) {
                val day = parts[2].toIntOrNull() ?: 1
                "$monthName $day\n$year"
            } else {
                "$monthName\n$year"
            }
        } else {
            key
        }
    } catch (e: Exception) {
        key
    }
}

@Composable
fun ProperLineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxVal = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val pointSpacing = 80.dp
            val chartHeight = 260.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    item {
                        val density = LocalDensity.current
                        val pointSpacingPx = with(density) { pointSpacing.toPx() }
                        val totalWidthDp = pointSpacing * (data.size - 1).coerceAtLeast(1) + 56.dp

                        Box(
                            modifier = Modifier
                                .width(totalWidthDp)
                                .fillMaxHeight()
                        ) {
                            // Canvas to draw connected lines and gradient fill
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val height = size.height
                                val topPadding = 36f
                                val bottomPadding = 80f
                                val drawHeight = height - topPadding - bottomPadding

                                val points = data.indices.map { i ->
                                    val x = i * pointSpacingPx + 28f
                                    val y = height - bottomPadding - ((data[i].second.toFloat() / maxVal) * drawHeight)
                                    Offset(x, y)
                                }

                                if (points.size > 1) {
                                    // Gradient fill under the line
                                    val fillPath = Path().apply {
                                        moveTo(points.first().x, height - bottomPadding)
                                        points.forEach { point -> lineTo(point.x, point.y) }
                                        lineTo(points.last().x, height - bottomPadding)
                                        close()
                                    }
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.35f),
                                                primaryColor.copy(alpha = 0.05f)
                                            ),
                                            startY = topPadding,
                                            endY = height - bottomPadding
                                        )
                                    )

                                    // Connected Line Path
                                    val strokePath = Path().apply {
                                        moveTo(points.first().x, points.first().y)
                                        for (i in 1 until points.size) {
                                            lineTo(points[i].x, points[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = strokePath,
                                        color = primaryColor,
                                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                                    )
                                }

                                // Draw Dots
                                points.forEach { point ->
                                    drawCircle(
                                        color = primaryColor,
                                        radius = 8f,
                                        center = point
                                    )
                                }
                            }

                            // Overlay labels above and below points
                            data.forEachIndexed { i, (label, value) ->
                                val xOffset = pointSpacing * i
                                Column(
                                    modifier = Modifier
                                        .offset(x = xOffset)
                                        .width(56.dp)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = value.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = formatXAxisLabel(label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GapBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxVal = data.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val pointSpacing = 80.dp
            val chartHeight = 260.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    item {
                        val density = LocalDensity.current
                        val pointSpacingPx = with(density) { pointSpacing.toPx() }
                        val totalWidthDp = pointSpacing * (data.size - 1).coerceAtLeast(1) + 56.dp

                        Box(
                            modifier = Modifier
                                .width(totalWidthDp)
                                .fillMaxHeight()
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val height = size.height
                                val topPadding = 36f
                                val bottomPadding = 80f
                                val drawHeight = height - topPadding - bottomPadding

                                val points = data.indices.map { i ->
                                    val x = i * pointSpacingPx + 28f
                                    val y = height - bottomPadding - ((data[i].second.toFloat() / maxVal) * drawHeight)
                                    Offset(x, y)
                                }

                                points.forEachIndexed { _, point ->
                                    val barTop = point.y
                                    val barBottom = height - bottomPadding
                                    drawRect(
                                        color = tertiaryColor.copy(alpha = 0.7f),
                                        topLeft = Offset(point.x - 12f, barTop),
                                        size = Size(24f, (barBottom - barTop).coerceAtLeast(6f))
                                    )
                                }
                            }

                            data.forEachIndexed { i, (label, daysGap) ->
                                val xOffset = pointSpacing * i
                                Column(
                                    modifier = Modifier
                                        .offset(x = xOffset)
                                        .width(56.dp)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${daysGap}d",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tertiaryColor
                                    )
                                    Text(
                                        text = formatXAxisLabel(label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onSurfaceColor,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
