package com.github.damontecres.stashapp.mobile.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun formatMonthYearLabel(yearMonthKey: String): String {
    return try {
        val parts = yearMonthKey.split("-")
        if (parts.size == 2) {
            val year = parts[0]
            val monthNum = parts[1].toIntOrNull() ?: 1
            val monthName = Month.of(monthNum).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            "$monthName '${year.takeLast(2)}"
        } else {
            yearMonthKey
        }
    } catch (e: Exception) {
        yearMonthKey
    }
}

fun formatFullMonthYearLabel(yearMonthKey: String): String {
    return try {
        val parts = yearMonthKey.split("-")
        if (parts.size == 2) {
            val year = parts[0]
            val monthNum = parts[1].toIntOrNull() ?: 1
            val monthName = Month.of(monthNum).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            "$monthName $year"
        } else {
            yearMonthKey
        }
    } catch (e: Exception) {
        yearMonthKey
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OCountDashboardScreen(
    serverUrl: String,
    apiKey: String,
    username: String = "",
    password: String = "",
    onNavigateBack: () -> Unit,
    onSceneClick: (SlimSceneData) -> Unit = {},
    viewModel: OCountDashboardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var activeTab by remember { mutableStateOf(0) } // 0: Monthly Trend, 1: 12-Month Comparison

    LaunchedEffect(serverUrl, apiKey) {
        viewModel.loadAnalytics(serverUrl, apiKey, username, password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scene 'O' Count Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        BackArrowIcon()
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
                        Button(onClick = { viewModel.loadAnalytics(serverUrl, apiKey, username, password) }) {
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

                        // Tab Selection for All 3 Analytics Charts
                        item {
                            PrimaryTabRow(selectedTabIndex = activeTab) {
                                Tab(
                                    selected = activeTab == 0,
                                    onClick = { activeTab = 0 },
                                    text = { Text("Monthly Trend") }
                                )
                                Tab(
                                    selected = activeTab == 1,
                                    onClick = { activeTab = 1 },
                                    text = { Text("M-o-M") }
                                )
                                Tab(
                                    selected = activeTab == 2,
                                    onClick = { activeTab = 2 },
                                    text = { Text("Event Gaps") }
                                )
                            }
                        }

                        when (activeTab) {
                            0 -> {
                                if (state.monthlyCounts.isEmpty()) {
                                    item {
                                        Text("No monthly data available", style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    item {
                                        StockStyleLineChart(
                                            data = state.monthlyCounts.entries.map { it.key to it.value }
                                        )
                                    }
                                }
                            }
                            1 -> {
                                if (state.yearlyMonthlyCounts.isEmpty()) {
                                    item {
                                        Text("No yearly comparison data available", style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    item {
                                        YearlyComparisonBarChart(
                                            yearlyData = state.yearlyMonthlyCounts
                                        )
                                    }
                                }
                            }
                            2 -> {
                                if (state.gapEvents.isEmpty()) {
                                    item {
                                        Text("No gap data available", style = MaterialTheme.typography.bodyMedium)
                                    }
                                } else {
                                    item {
                                        StockStyleGapLineChart(
                                            items = state.gapEvents,
                                            onSceneClick = onSceneClick
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

@Composable
fun StashFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        )
    )
}

// Stock-Price Style Line Chart (Chronological, Fits Screen Width, Y-Axis Labels, Tappable Nodes & Tooltip)
@Composable
fun StockStyleLineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val chronData = remember(data) { data.sortedBy { it.first } }

    var selectedTimeframe by remember { mutableStateOf("All") }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val availableYears = remember(chronData) {
        val extracted = chronData.mapNotNull { it.first.split("-").firstOrNull()?.toIntOrNull() }.distinct().sorted()
        (extracted + listOf(2024, 2025, 2026)).distinct().sorted()
    }

    val timeframeOptions = remember(availableYears) {
        val list = mutableListOf("All", "12 M")
        availableYears.forEach { list.add(it.toString()) }
        list
    }

    val sortedData = remember(chronData, selectedTimeframe) {
        when (selectedTimeframe) {
            "All" -> chronData
            "12 M" -> {
                val cutoffYM = java.time.YearMonth.from(java.time.LocalDate.now().minusMonths(12))
                val filtered = chronData.filter { (key, _) ->
                    try {
                        val parts = key.split("-")
                        if (parts.size >= 2) {
                            val ym = java.time.YearMonth.of(parts[0].toInt(), parts[1].toInt())
                            !ym.isBefore(cutoffYM)
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }
                if (filtered.isNotEmpty()) filtered else chronData.takeLast(12)
            }
            else -> {
                val targetYear = selectedTimeframe.toIntOrNull()
                if (targetYear != null) {
                    val filtered = chronData.filter { it.first.startsWith("$targetYear-") }
                    if (filtered.isNotEmpty()) filtered else chronData
                } else {
                    chronData
                }
            }
        }
    }

    val timeframeTotal = remember(sortedData) { sortedData.sumOf { it.second } }
    val dataMax = remember(sortedData) { sortedData.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1 }
    val step = 2
    val maxVal = remember(dataMax) {
        val rounded = if (dataMax % step == 0) dataMax + step else ((dataMax / step) + 1) * step
        rounded.coerceAtLeast(step)
    }
    val gridSteps = maxVal / step

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Selected Data Header Tooltip (Stock Style)
            val activeItem = selectedIndex?.let { sortedData.getOrNull(it) } ?: sortedData.lastOrNull()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activeItem?.let { "${formatFullMonthYearLabel(it.first)} (${it.second} O's)" } ?: "Monthly Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$timeframeTotal O's",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { constraints.maxWidth.toFloat() }
                val heightPx = with(density) { 240.dp.toPx() }

                val yAxisWidth = 36f
                val rightPadding = 24f
                val topPadding = 32f
                val bottomPadding = 40f

                val chartLeft = yAxisWidth + 8f
                val chartRight = widthPx - rightPadding
                val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
                val chartTop = topPadding
                val chartBottom = heightPx - bottomPadding
                val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

                val points = remember(sortedData, chartWidth, chartHeight, maxVal) {
                    val count = sortedData.size
                    sortedData.indices.map { i ->
                        val stepX = if (count > 1) i.toFloat() / (count - 1) else 0.5f
                        val x = chartLeft + stepX * chartWidth
                        val ratioY = (sortedData[i].second.toFloat() / maxVal).coerceIn(0f, 1f)
                        val y = chartBottom - ratioY * chartHeight
                        Offset(x, y)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sortedData, chartLeft, chartRight, chartWidth) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val count = sortedData.size
                                    if (count > 0 && offset.x in chartLeft..chartRight) {
                                        val ratio = (offset.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                }
                            )
                        }
                        .pointerInput(sortedData, chartLeft, chartRight, chartWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val count = sortedData.size
                                    if (count > 0 && offset.x in chartLeft..chartRight) {
                                        val ratio = (offset.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val count = sortedData.size
                                    if (count > 0) {
                                        val ratio = (change.position.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    // Draw Y-Axis Horizontal Gridlines
                    for (g in 0..gridSteps) {
                        val gridY = chartBottom - (g.toFloat() / gridSteps) * chartHeight
                        drawLine(
                            color = outlineColor,
                            start = Offset(chartLeft, gridY),
                            end = Offset(chartRight, gridY),
                            strokeWidth = 1f
                        )
                    }

                    // Stock Line Area Fill
                    if (points.size > 1) {
                        val fillPath = Path().apply {
                            moveTo(points.first().x, chartBottom)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, chartBottom)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = chartTop,
                                endY = chartBottom
                            )
                        )

                        // Smooth Line Path
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Active Tapped Node / Cursor Highlight
                    selectedIndex?.let { idx ->
                        if (idx in points.indices) {
                            val activePt = points[idx]

                            // Vertical Guide Line
                            drawLine(
                                color = primaryColor.copy(alpha = 0.5f),
                                start = Offset(activePt.x, chartTop),
                                end = Offset(activePt.x, chartBottom),
                                strokeWidth = 2f
                            )

                            // Outer Pulse Circle
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.3f),
                                radius = 14f,
                                center = activePt
                            )
                            // Solid Node Circle
                            drawCircle(
                                color = primaryColor,
                                radius = 7f,
                                center = activePt
                            )
                        }
                    }
                }

                // Y-Axis Labels overlay on left
                Box(
                    modifier = Modifier
                        .width(with(density) { yAxisWidth.toDp() })
                        .fillMaxHeight()
                ) {
                    for (g in gridSteps downTo 0) {
                        val ratio = g.toFloat() / gridSteps
                        val yPx = chartBottom - ratio * chartHeight
                        Text(
                            text = (g * step).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor.copy(alpha = 0.6f),
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val textY = (yPx - placeable.height / 2f).roundToInt()
                                    placeable.placeRelative(0, textY)
                                }
                            }
                        )
                    }
                }

                // X-Axis Optimized Sampled Labels at Bottom
                val labelSampleIndices = remember(sortedData) {
                    val count = sortedData.size
                    if (count <= 5) {
                        sortedData.indices.toList()
                    } else {
                        val step = (count - 1) / 4f
                        listOf(
                            0,
                            (step * 1).toInt(),
                            (step * 2).toInt(),
                            (step * 3).toInt(),
                            count - 1
                        ).distinct()
                    }
                }

                labelSampleIndices.forEach { idx ->
                    val item = sortedData.getOrNull(idx) ?: return@forEach
                    val pt = points.getOrNull(idx) ?: return@forEach
                    val labelWidthDp = 52.dp
                    val labelWidthPx = with(density) { labelWidthDp.toPx() }
                    val labelCenterX = pt.x - labelWidthPx / 2f
                    val maxLeftPx = widthPx - labelWidthPx
                    val clampedLeftPx = labelCenterX.coerceIn(0f, maxLeftPx)

                    Text(
                        text = formatMonthYearLabel(item.first),
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .offset(
                                x = with(density) { clampedLeftPx.toDp() },
                                y = with(density) { (chartBottom + 6f).toDp() }
                            )
                            .width(labelWidthDp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timeline Filter Selector Chips (All (Default), 12 M, 2024, 2025, 2026)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                timeframeOptions.forEach { option ->
                    StashFilterChip(
                        selected = (selectedTimeframe == option),
                        onClick = {
                            selectedTimeframe = option
                            selectedIndex = null
                        },
                        label = { Text(option) }
                    )
                }
            }
        }
    }
}

// 12-Month Comparison Bar Chart (Comparing 12 Months Jan-Dec for 2 Selected Years)
@Composable
fun YearlyComparisonBarChart(
    yearlyData: Map<Int, Map<Int, Int>>,
    modifier: Modifier = Modifier
) {
    val currentYear = remember { java.time.LocalDate.now().year }
    val dataYears = yearlyData.keys.sorted()

    val availableYears = remember(dataYears, currentYear) {
        val set = mutableSetOf(2024)
        for (y in 2024..currentYear) set.add(y)
        set.addAll(dataYears.filter { it >= 2024 })
        set.sorted()
    }

    if (availableYears.isEmpty()) return

    var selectedYears by remember(availableYears) {
        mutableStateOf(availableYears.takeLast(2).toSet())
    }

    val activeCompareYears = remember(selectedYears) { selectedYears.sorted() }

    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val yearColors = remember(activeCompareYears) {
        if (activeCompareYears.size > 1) {
            listOf(
                onSurfaceColor.copy(alpha = 0.35f), // Left bar (Subtle)
                primaryColor                        // Right bar (Emphasized)
            )
        } else {
            listOf(primaryColor)
        }
    }

    val step = 2
    val maxVal = remember(yearlyData, activeCompareYears) {
        val actualMax = activeCompareYears.maxOfOrNull { yr -> yearlyData[yr]?.values?.maxOrNull() ?: 0 } ?: 1
        val targeted = maxOf(14, actualMax)
        val rounded = if (targeted % step == 0) targeted else ((targeted / step) + 1) * step
        rounded.coerceAtLeast(14)
    }
    val gridSteps = maxVal / step
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Year Legend & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "M-o-M Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    activeCompareYears.forEachIndexed { idx, year ->
                        val color = yearColors[idx % yearColors.size]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(modifier = Modifier.size(12.dp), color = color, shape = MaterialTheme.shapes.extraSmall) {}
                            Text(text = year.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val chartHeight = 220.dp

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { constraints.maxWidth.toFloat() }
                val heightPx = with(density) { 220.dp.toPx() }

                val yAxisWidth = 36f
                val rightPadding = 24f
                val topPadding = 16f
                val bottomPadding = 44f

                val chartLeft = yAxisWidth + 8f
                val chartRight = widthPx - rightPadding
                val chartTop = topPadding
                val chartBottom = heightPx - bottomPadding
                val chartHeightVal = (chartBottom - chartTop).coerceAtLeast(1f)

                // Draw Y-Axis Horizontal Gridlines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (g in 0..gridSteps) {
                        val gridY = chartBottom - (g.toFloat() / gridSteps) * chartHeightVal
                        drawLine(
                            color = outlineColor,
                            start = Offset(chartLeft, gridY),
                            end = Offset(chartRight, gridY),
                            strokeWidth = 1f
                        )
                    }
                }

                // Y-Axis Labels overlay on left
                Box(
                    modifier = Modifier
                        .width(with(density) { yAxisWidth.toDp() })
                        .fillMaxHeight()
                ) {
                    for (g in gridSteps downTo 0) {
                        val ratio = g.toFloat() / gridSteps
                        val yPx = chartBottom - ratio * chartHeightVal
                        Text(
                            text = (g * step).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor.copy(alpha = 0.6f),
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val textY = (yPx - placeable.height / 2f).roundToInt()
                                    placeable.placeRelative(0, textY)
                                }
                            }
                        )
                    }
                }

                // Scrollable LazyRow for 12 Months
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = with(density) { chartLeft.toDp() })
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        item {
                            val itemSpacing = 64.dp
                            val totalWidthDp = itemSpacing * 12 + 32.dp

                            Box(
                                modifier = Modifier
                                    .width(totalWidthDp)
                                    .fillMaxHeight()
                            ) {
                                (1..12).forEachIndexed { mIdx, monthNum ->
                                    val xOffset = itemSpacing * mIdx
                                    Box(
                                        modifier = Modifier
                                            .offset(x = xOffset)
                                            .width(56.dp)
                                            .fillMaxHeight()
                                    ) {
                                        // Bars anchored from topPadding down to chartBottom
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(with(density) { chartHeightVal.toDp() })
                                                .offset(y = with(density) { topPadding.toDp() }),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.Bottom
                                        ) {
                                            activeCompareYears.forEachIndexed { yIdx, yr ->
                                                val count = yearlyData[yr]?.get(monthNum) ?: 0
                                                val barHeightFraction = if (count > 0) (count.toFloat() / maxVal).coerceIn(0.08f, 1f) else 0f
                                                val color = yearColors[yIdx % yearColors.size]
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .then(
                                                            if (barHeightFraction > 0f) {
                                                                Modifier.fillMaxHeight(barHeightFraction)
                                                            } else {
                                                                Modifier.height(0.dp)
                                                            }
                                                        )
                                                        .padding(horizontal = 2.dp)
                                                        .background(color, shape = MaterialTheme.shapes.extraSmall)
                                                )
                                            }
                                        }

                                        // Month label placed explicitly below chartBottom (the 0 line)
                                        Text(
                                            text = monthNames[mIdx],
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            color = onSurfaceColor.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .width(56.dp)
                                                .offset(y = with(density) { (chartBottom + 6f).toDp() })
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom Year Filter Chips (2024 to current year) - Select 2 years
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableYears.forEach { yr ->
                    val isSelected = selectedYears.contains(yr)
                    StashFilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                if (selectedYears.size > 1) {
                                    selectedYears = selectedYears - yr
                                }
                            } else {
                                if (selectedYears.size >= 2) {
                                    val first = selectedYears.sorted().first()
                                    selectedYears = (selectedYears - first) + yr
                                } else {
                                    selectedYears = selectedYears + yr
                                }
                            }
                        },
                        label = { Text(yr.toString()) }
                    )
                }
            }
        }
    }
}

// Stock-Style Line Chart for Event Gaps with Draggable Data Points Selection & Filter Chips
@Composable
fun StockStyleGapLineChart(
    items: List<OEventGapItem>,
    onSceneClick: (SlimSceneData) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    var selectedTimeframe by remember { mutableStateOf("All") }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val availableYears = remember(items) {
        val extracted = items.mapNotNull { item ->
            val match = Regex("\\d{4}").find(item.dateLabel)
            match?.value?.toIntOrNull()
        }.distinct().sorted()
        (extracted + listOf(2024, 2025, 2026)).distinct().sorted()
    }

    val sortedData = remember(items, selectedTimeframe) {
        fun filterByMonths(months: Long): List<OEventGapItem> {
            val cutoff = java.time.LocalDate.now().minusMonths(months)
            return items.filter { item ->
                !item.date.isBefore(cutoff)
            }
        }

        when (selectedTimeframe) {
            "All" -> items
            "12 M" -> filterByMonths(12)
            "6 M" -> filterByMonths(6)
            "3 M" -> filterByMonths(3)
            "1 M" -> filterByMonths(1)
            "Last 26" -> items.takeLast(26)
            "Last 13" -> items.takeLast(13)
            "Last 6" -> items.takeLast(6)
            else -> {
                val targetYear = selectedTimeframe.toIntOrNull()
                if (targetYear != null) {
                    val filtered = items.filter { it.dateLabel.contains(targetYear.toString()) }
                    if (filtered.isNotEmpty()) filtered else items
                } else {
                    items
                }
            }
        }
    }

    val dataMax = remember(sortedData) { sortedData.maxOfOrNull { it.gapDays }?.coerceAtLeast(1) ?: 1 }
    val step = maxOf(1, dataMax / 5)
    val maxVal = remember(dataMax) {
        val rounded = if (dataMax % step == 0) dataMax + step else ((dataMax / step) + 1) * step
        rounded.coerceAtLeast(step)
    }
    val gridSteps = maxVal / step

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val activeItem = selectedIndex?.let { sortedData.getOrNull(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeItem?.let { 
                            if (it.isToday) "Event Upcoming: ${it.gapDays} days" 
                            else "Event #${it.index}: ${it.gapDays} days" 
                        } ?: "Event Gaps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeItem?.dateLabel ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceColor.copy(alpha = 0.7f)
                    )
                }

                if (activeItem?.scene != null && activeItem.isToday != true) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.clickable { onSceneClick(activeItem.scene) }
                    ) {
                        Text(
                            text = "View Scene",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${sortedData.size} Gaps",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { constraints.maxWidth.toFloat() }
                val heightPx = with(density) { 240.dp.toPx() }

                val yAxisWidth = 64f
                val rightPadding = 24f
                val topPadding = 32f
                val bottomPadding = 40f

                val chartLeft = yAxisWidth + 8f
                val chartRight = widthPx - rightPadding
                val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
                val chartTop = topPadding
                val chartBottom = heightPx - bottomPadding
                val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

                val points = remember(sortedData, chartWidth, chartHeight, maxVal) {
                    val count = sortedData.size
                    sortedData.indices.map { i ->
                        val stepX = if (count > 1) i.toFloat() / (count - 1) else 0.5f
                        val x = chartLeft + stepX * chartWidth
                        val ratioY = (sortedData[i].gapDays.toFloat() / maxVal).coerceIn(0f, 1f)
                        val y = chartBottom - ratioY * chartHeight
                        Offset(x, y)
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sortedData, chartLeft, chartRight, chartWidth) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val count = sortedData.size
                                    if (count > 0 && offset.x in chartLeft..chartRight) {
                                        val ratio = (offset.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                }
                            )
                        }
                        .pointerInput(sortedData, chartLeft, chartRight, chartWidth) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val count = sortedData.size
                                    if (count > 0 && offset.x in chartLeft..chartRight) {
                                        val ratio = (offset.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val count = sortedData.size
                                    if (count > 0) {
                                        val ratio = (change.position.x - chartLeft) / chartWidth
                                        val idx = (ratio * (count - 1)).roundToInt().coerceIn(0, count - 1)
                                        selectedIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    // Draw Y-Axis Horizontal Gridlines
                    for (g in 0..gridSteps) {
                        val gridY = chartBottom - (g.toFloat() / gridSteps) * chartHeight
                        drawLine(
                            color = outlineColor,
                            start = Offset(chartLeft, gridY),
                            end = Offset(chartRight, gridY),
                            strokeWidth = 1f
                        )
                    }

                    // Stock Line Area Fill
                    if (points.size > 1) {
                        val fillPath = Path().apply {
                            moveTo(points.first().x, chartBottom)
                            points.forEach { lineTo(it.x, it.y) }
                            lineTo(points.last().x, chartBottom)
                            close()
                        }
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0.02f)
                                ),
                                startY = chartTop,
                                endY = chartBottom
                            )
                        )

                        // Smooth Line Path
                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // Active Tapped Node / Cursor Highlight
                    selectedIndex?.let { idx ->
                        if (idx in points.indices) {
                            val activePt = points[idx]

                            // Vertical Guide Line
                            drawLine(
                                color = primaryColor.copy(alpha = 0.5f),
                                start = Offset(activePt.x, chartTop),
                                end = Offset(activePt.x, chartBottom),
                                strokeWidth = 2f
                            )

                            // Outer Pulse Circle
                            drawCircle(
                                color = primaryColor.copy(alpha = 0.3f),
                                radius = 14f,
                                center = activePt
                            )
                            // Solid Node Circle
                            drawCircle(
                                color = primaryColor,
                                radius = 7f,
                                center = activePt
                            )
                        }
                    }
                }

                // Y-Axis Labels overlay on left
                Box(
                    modifier = Modifier
                        .width(with(density) { yAxisWidth.toDp() })
                        .fillMaxHeight()
                ) {
                    for (g in gridSteps downTo 0) {
                        val ratio = g.toFloat() / gridSteps
                        val yPx = chartBottom - ratio * chartHeight
                        Text(
                            text = "${g * step}d",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor.copy(alpha = 0.6f),
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val textY = (yPx - placeable.height / 2f).roundToInt()
                                    placeable.placeRelative(0, textY)
                                }
                            }
                        )
                    }
                }

                // X-Axis Optimized Sampled Labels at Bottom
                val labelSampleIndices = remember(sortedData) {
                    val count = sortedData.size
                    if (count <= 5) {
                        sortedData.indices.toList()
                    } else {
                        val step = (count - 1) / 4f
                        listOf(
                            0,
                            (step * 1).toInt(),
                            (step * 2).toInt(),
                            (step * 3).toInt(),
                            count - 1
                        ).distinct()
                    }
                }

                labelSampleIndices.forEach { idx ->
                    val item = sortedData.getOrNull(idx) ?: return@forEach
                    val pt = points.getOrNull(idx) ?: return@forEach
                    val labelWidthDp = 52.dp
                    val labelWidthPx = with(density) { labelWidthDp.toPx() }
                    val labelCenterX = pt.x - labelWidthPx / 2f
                    val maxLeftPx = widthPx - labelWidthPx
                    val clampedLeftPx = labelCenterX.coerceIn(0f, maxLeftPx)

                    Text(
                        text = if (item.isToday) "Upcoming" else "#${item.index}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurfaceColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .offset(
                                x = with(density) { clampedLeftPx.toDp() },
                                y = with(density) { (chartBottom + 6f).toDp() }
                            )
                            .width(labelWidthDp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-line Timeline Filter Selector Chips
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Line 1 - All, 12M, 6M, 3M, 1M
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All", "12 M", "6 M", "3 M", "1 M").forEach { option ->
                        StashFilterChip(
                            selected = (selectedTimeframe == option),
                            onClick = {
                                selectedTimeframe = option
                                selectedIndex = null
                            },
                            label = { Text(option) }
                        )
                    }
                }

                // Line 2 - Last 26, Last 13, Last 6
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Last 26", "Last 13", "Last 6").forEach { option ->
                        StashFilterChip(
                            selected = (selectedTimeframe == option),
                            onClick = {
                                selectedTimeframe = option
                                selectedIndex = null
                            },
                            label = { Text(option) }
                        )
                    }
                }

                // Line 3 - 2024 till current year
                if (availableYears.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableYears.forEach { yr ->
                            val option = yr.toString()
                            StashFilterChip(
                                selected = (selectedTimeframe == option),
                                onClick = {
                                    selectedTimeframe = option
                                    selectedIndex = null
                                },
                                label = { Text(option) }
                            )
                        }
                    }
                }
            }
        }
    }
}
