@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.github.damontecres.stashapp.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.util.fixStashMediaUrl
import com.github.damontecres.stashapp.mobile.util.formatDuration
import com.github.damontecres.stashapp.mobile.util.formatResolution
import com.github.damontecres.stashapp.mobile.util.titleOrFilename
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

fun formatSceneDate(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    return try {
        val inputStr = dateString.trim()
        if (inputStr.contains("T")) {
            val instant = Instant.parse(inputStr)
            val zdt = instant.atZone(ZoneId.systemDefault())
            zdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } else if (inputStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val localDate = LocalDate.parse(inputStr)
            localDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        } else {
            inputStr
        }
    } catch (e: Exception) {
        dateString.take(10)
    }
}

@Composable
fun BackArrowIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 3.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.65f, h * 0.2f)
            lineTo(w * 0.35f, h * 0.5f)
            lineTo(w * 0.65f, h * 0.8f)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun DirectionArrowIcon(
    isDescending: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 2.5f.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (isDescending) {
                moveTo(w * 0.5f, h * 0.2f)
                lineTo(w * 0.5f, h * 0.8f)
                moveTo(w * 0.25f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.8f)
                lineTo(w * 0.75f, h * 0.55f)
            } else {
                moveTo(w * 0.5f, h * 0.8f)
                lineTo(w * 0.5f, h * 0.2f)
                moveTo(w * 0.25f, h * 0.45f)
                lineTo(w * 0.5f, h * 0.2f)
                lineTo(w * 0.75f, h * 0.45f)
            }
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun EyeIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, h * 0.5f)
            quadraticTo(w * 0.5f, -h * 0.2f, w, h * 0.5f)
            quadraticTo(w * 0.5f, h * 1.2f, 0f, h * 0.5f)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth))
        drawCircle(color = color, radius = 2.5f.dp.toPx(), center = Offset(w * 0.5f, h * 0.5f))
    }
}

@Composable
fun DownloadIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
) {
    Canvas(modifier = modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 1.8f.dp.toPx()
        drawLine(color = color, start = Offset(w * 0.5f, 0f), end = Offset(w * 0.5f, h * 0.7f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.75f)
            lineTo(w * 0.8f, h * 0.45f)
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(color = color, start = Offset(w * 0.15f, h * 0.9f), end = Offset(w * 0.85f, h * 0.9f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

@Composable
fun MoreVertIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val radius = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        drawCircle(color = color, radius = radius, center = Offset(cx, h * 0.25f))
        drawCircle(color = color, radius = radius, center = Offset(cx, h * 0.5f))
        drawCircle(color = color, radius = radius, center = Offset(cx, h * 0.75f))
    }
}

@Composable
fun SceneListScreen(
    serverUrl: String,
    apiKey: String,
    username: String = "",
    password: String = "",
    playlist: PlaylistUiItem,
    refreshSceneId: String? = null,
    onBack: () -> Unit,
    onSceneClick: (SlimSceneData) -> Unit,
    onPlayAll: (List<SlimSceneData>) -> Unit,
    onPlayRandom: (List<SlimSceneData>) -> Unit,
    viewModel: SceneListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val firstVisibleIndex = if (state.scenes.isNotEmpty()) listState.firstVisibleItemIndex + 1 else 0
    var showOverflowMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(playlist.id) {
        viewModel.refresh(serverUrl, apiKey, username, password, playlist.savedFilter)
    }

    LaunchedEffect(refreshSceneId) {
        if (!refreshSceneId.isNullOrBlank()) {
            viewModel.refreshSingleScene(refreshSceneId, serverUrl, apiKey, username, password)
        }
    }

    // Debounced server search query
    var isInitialSearch by remember { mutableStateOf(true) }
    LaunchedEffect(searchQuery) {
        if (isInitialSearch) {
            isInitialSearch = false
            return@LaunchedEffect
        }
        delay(400.milliseconds)
        viewModel.searchScenes(searchQuery, serverUrl, apiKey, username, password, playlist.savedFilter)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlist.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.toggleSortDirection(serverUrl, apiKey, username, password, playlist.savedFilter)
                        }
                    ) {
                        DirectionArrowIcon(isDescending = state.activeDirection == SortDirectionEnum.DESC)
                    }

                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            MoreVertIcon()
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play All") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.fetchAllMatchingScenes(serverUrl, apiKey, username, password, playlist.savedFilter) { allScenes ->
                                        onPlayAll(allScenes)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.fetchAllMatchingScenes(serverUrl, apiKey, username, password, playlist.savedFilter) { allScenes ->
                                        onPlayRandom(allScenes)
                                    }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sort By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
                                onClick = {}
                            )
                            val sortOptions = listOf(
                                "date" to "Date",
                                "file_mod_time" to "File Modification Time",
                                "updated_at" to "Updated At",
                                "created_at" to "Date Added",
                                "title" to "Title",
                                "rating100" to "Rating",
                                "o_counter" to "O Count",
                                "duration" to "Duration",
                                "filesize" to "File Size",
                                "play_count" to "Play Count",
                                "last_played_at" to "Last Played",
                                "path" to "File Path",
                                "random" to "Random Order"
                            )
                            sortOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(label)
                                            if (state.activeSort == key) {
                                                Text("✓", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        viewModel.selectSortField(key, serverUrl, apiKey, username, password, playlist.savedFilter)
                                    }
                                )
                            }
                        }
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
                state.isLoading && state.scenes.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null && state.scenes.isEmpty() -> {
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
                        Button(onClick = { viewModel.refresh(serverUrl, apiKey, username, password, playlist.savedFilter) }) {
                            Text("Retry")
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search scenes...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (state.scenes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No scenes found", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 60.dp)
                            ) {
                                itemsIndexed(state.scenes) { index, scene ->
                                    if (index >= state.scenes.size - 3 && state.canLoadMore && !state.isFetchingNextPage) {
                                        LaunchedEffect(index) {
                                            viewModel.loadNextPage(serverUrl, apiKey, username, password, playlist.savedFilter)
                                        }
                                    }

                                    SceneCard(
                                        scene = scene,
                                        serverUrl = serverUrl,
                                        apiKey = apiKey,
                                        username = username,
                                        password = password,
                                        onClick = { onSceneClick(scene) }
                                    )
                                }

                                if (state.isFetchingNextPage) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(32.dp),
                                                strokeWidth = 3.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.scenes.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Item $firstVisibleIndex of ${state.totalCount}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SceneCard(
    scene: SlimSceneData,
    serverUrl: String,
    apiKey: String = "",
    username: String = "",
    password: String = "",
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var showCardMenu by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }

    val primaryPath = when {
        !scene.paths.screenshot.isNullOrBlank() -> scene.paths.screenshot
        !scene.paths.preview.isNullOrBlank() -> scene.paths.preview
        else -> "/scene/${scene.id}/screenshot"
    }
    val mediaUrlToLoad = fixStashMediaUrl(primaryPath, serverUrl, apiKey)

    val imageLoader = remember(serverUrl, apiKey, username, password) {
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { StashMobileClient.createOkHttpClient(apiKey, username, password) }
                    )
                )
            }
            .build()
    }

    val videoFile = scene.files.firstOrNull()?.videoFile
    val durationText = formatDuration(videoFile?.duration)
    val resolutionText = formatResolution(videoFile?.height)
    val formattedDate = formatSceneDate(scene.date ?: videoFile?.mod_time?.toString())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (mediaUrlToLoad.isNotBlank()) {
                    val request = ImageRequest.Builder(context)
                        .data(mediaUrlToLoad)
                        .crossfade(true)
                        .build()

                    AsyncImage(
                        model = request,
                        imageLoader = imageLoader,
                        contentDescription = scene.titleOrFilename,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (resolutionText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = resolutionText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (durationText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = durationText,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (scene.play_count != null && scene.play_count > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            EyeIcon(color = Color.White)
                            Text(
                                text = "${scene.play_count}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (scene.rating100 != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "★ ${scene.rating100}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scene.titleOrFilename,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showCardMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            MoreVertIcon(modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showCardMenu,
                            onDismissRequest = { showCardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Metadata") },
                                onClick = {
                                    showCardMenu = false
                                    showMetadataSheet = true
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val studioName = scene.studio?.name
                    if (!studioName.isNullOrBlank()) {
                        Text(
                            text = studioName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (formattedDate.isNotBlank()) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (scene.performers.isNotEmpty() || scene.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        scene.performers.forEach { performer ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(performer.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        scene.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(tag.slimTagData.name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if (showMetadataSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMetadataSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "Scene Metadata", style = MaterialTheme.typography.titleLarge)
                    Text(text = "Title: ${scene.titleOrFilename}", style = MaterialTheme.typography.bodyMedium)
                    if (videoFile != null) {
                        Text(text = "Path: ${videoFile.path}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Resolution: ${videoFile.width}x${videoFile.height}", style = MaterialTheme.typography.bodySmall)
                        Text(text = "Codec: ${videoFile.video_codec} / ${videoFile.audio_codec}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(text = "Date: ${formattedDate.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Play Count: ${scene.play_count ?: 0} | O Count: ${scene.o_counter ?: 0}", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { showMetadataSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
