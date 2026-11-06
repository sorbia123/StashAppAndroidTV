@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.github.damontecres.stashapp.mobile.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import com.github.damontecres.stashapp.mobile.ui.scene.SceneHistoryDialog
import com.github.damontecres.stashapp.mobile.ui.scene.parseTimeToString
import com.github.damontecres.stashapp.mobile.util.fixStashMediaUrl
import com.github.damontecres.stashapp.mobile.util.titleOrFilename
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

fun formatBytes(bytes: Any?): String {
    val b = when (bytes) {
        is Number -> bytes.toLong()
        is String -> bytes.toLongOrNull() ?: 0L
        else -> 0L
    }
    if (b <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var digitGroups = (Math.log10(b.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) digitGroups = units.size - 1
    return String.format(java.util.Locale.getDefault(), "%.1f %s", b / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun FullscreenIcon(
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val strokeWidth = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (isFullscreen) {
                moveTo(w * 0.35f, h * 0.15f); lineTo(w * 0.35f, h * 0.35f); lineTo(w * 0.15f, h * 0.35f)
                moveTo(w * 0.65f, h * 0.15f); lineTo(w * 0.65f, h * 0.35f); lineTo(w * 0.85f, h * 0.35f)
                moveTo(w * 0.35f, h * 0.85f); lineTo(w * 0.35f, h * 0.65f); lineTo(w * 0.15f, h * 0.65f)
                moveTo(w * 0.65f, h * 0.85f); lineTo(w * 0.65f, h * 0.65f); lineTo(w * 0.85f, h * 0.65f)
            } else {
                moveTo(w * 0.15f, h * 0.35f); lineTo(w * 0.15f, h * 0.15f); lineTo(w * 0.35f, h * 0.15f)
                moveTo(w * 0.85f, h * 0.35f); lineTo(w * 0.85f, h * 0.15f); lineTo(w * 0.65f, h * 0.15f)
                moveTo(w * 0.15f, h * 0.65f); lineTo(w * 0.15f, h * 0.85f); lineTo(w * 0.35f, h * 0.85f)
                moveTo(w * 0.85f, h * 0.65f); lineTo(w * 0.85f, h * 0.85f); lineTo(w * 0.65f, h * 0.85f)
            }
        }
        drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ScenePlayerScreen(
    serverUrl: String,
    apiKey: String,
    username: String = "",
    password: String = "",
    scene: SlimSceneData,
    playQueue: List<SlimSceneData> = emptyList(),
    initialIndex: Int = 0,
    onBack: () -> Unit,
    viewModel: ScenePlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.state.collectAsState()

    val effectiveQueue = remember(playQueue, scene) {
        if (playQueue.isNotEmpty()) playQueue else listOf(scene)
    }

    var currentIndex by remember(initialIndex, effectiveQueue) {
        mutableStateOf(initialIndex.coerceIn(0, effectiveQueue.size - 1))
    }

    val activeScene = effectiveQueue[currentIndex]

    var tagsList by remember(activeScene.id) {
        mutableStateOf(activeScene.tags.map { it.slimTagData.id to it.slimTagData.name }.toMutableStateList())
    }
    var performersList by remember(activeScene.id) {
        mutableStateOf(activeScene.performers.map { it.id to it.name }.toMutableStateList())
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var showOHistoryDialog by remember { mutableStateOf(false) }
    var showPlayHistoryDialog by remember { mutableStateOf(false) }
    var showAddTagSheet by remember { mutableStateOf(false) }
    var showAddPerformerSheet by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val videoFile = activeScene.files.firstOrNull()?.videoFile
    val isPortraitVideo = (videoFile?.height ?: 0) > (videoFile?.width ?: 0)

    val toggleFullscreen: () -> Unit = {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            if (isPortraitVideo) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val rawStreamPath = activeScene.paths.stream ?: ""
    val streamUrl = fixStashMediaUrl(rawStreamPath, serverUrl, apiKey)

    val exoPlayer = remember(context, effectiveQueue) {
        ExoPlayer.Builder(context).build().apply {
            val okHttpClient = StashMobileClient.createOkHttpClient(apiKey, username, password)
            val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

            val mediaSources = effectiveQueue.map { s ->
                val url = fixStashMediaUrl(s.paths.stream ?: "", serverUrl, apiKey)
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
            }

            setMediaSources(mediaSources)
            seekTo(initialIndex.coerceIn(0, effectiveQueue.size - 1), 0)
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (newIndex in effectiveQueue.indices && newIndex != currentIndex) {
                    currentIndex = newIndex
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler(enabled = true) {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            exoPlayer.release()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = activeScene.titleOrFilename ?: "Scene Player",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            exoPlayer.release()
                            onBack()
                        }) {
                            BackArrowIcon()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else paddingValues)
        ) {
            if (isFullscreen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Queue Position Badge (Top Left in Fullscreen)
                    if (effectiveQueue.size > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "${currentIndex + 1} / ${effectiveQueue.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { toggleFullscreen() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.65f), shape = MaterialTheme.shapes.small)
                    ) {
                        FullscreenIcon(isFullscreen = true)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Queue Position Badge (Top Left)
                        if (effectiveQueue.size > 1) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp),
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "${currentIndex + 1} / ${effectiveQueue.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { toggleFullscreen() },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.65f), shape = MaterialTheme.shapes.small)
                        ) {
                            FullscreenIcon(isFullscreen = false)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = activeScene.titleOrFilename ?: "Untitled Scene",
                            style = MaterialTheme.typography.titleLarge
                        )

                        if (!activeScene.details.isNullOrBlank()) {
                            Card(
                                onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = activeScene.details,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isDescriptionExpanded) "Show Less ▲" else "More Details ▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                onClick = { showOHistoryDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("O Count", style = MaterialTheme.typography.labelSmall)
                                    Text("${activeScene.o_counter ?: activeScene.o_history.size}", style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                            Card(
                                onClick = { showPlayHistoryDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Play Count", style = MaterialTheme.typography.labelSmall)
                                    Text("${activeScene.play_count ?: activeScene.play_history.size}", style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }

                        Text(text = "Tags", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tagsList.forEach { (id, name) ->
                                InputChip(
                                    selected = true,
                                    onClick = { tagsList.removeIf { it.first == id } },
                                    label = { Text(name) },
                                    trailingIcon = { Text("✕") }
                                )
                            }
                            FilledIconButton(
                                onClick = { showAddTagSheet = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        Text(text = "Performers", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            performersList.forEach { (id, name) ->
                                InputChip(
                                    selected = true,
                                    onClick = { performersList.removeIf { it.first == id } },
                                    label = { Text(name) },
                                    trailingIcon = { Text("✕") }
                                )
                            }
                            FilledIconButton(
                                onClick = { showAddPerformerSheet = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("+", style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        if (videoFile != null) {
                            HorizontalDivider()
                            Text(text = "File Metadata", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Path: ${videoFile.path}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Size: ${formatBytes(videoFile.size)}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Modified: ${parseTimeToString(videoFile.mod_time) ?: videoFile.mod_time.toString()}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Codec: ${videoFile.video_codec ?: "N/A"} / ${videoFile.audio_codec ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Resolution: ${videoFile.width}x${videoFile.height}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                viewModel.updateScene(
                                    serverUrl = serverUrl,
                                    apiKey = apiKey,
                                    username = username,
                                    password = password,
                                    sceneId = activeScene.id,
                                    title = activeScene.title,
                                    details = activeScene.details,
                                    rating100 = activeScene.rating100,
                                    tagIds = tagsList.map { it.first },
                                    performerIds = performersList.map { it.first },
                                    onSuccess = {
                                        // Saved successfully
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSaving
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save Changes")
                            }
                        }

                        if (state.saveError != null) {
                            Text(
                                text = "Error: ${state.saveError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (state.saveSuccess) {
                            Text(
                                text = "Scene saved successfully!",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        SceneHistoryDialog(
            show = showOHistoryDialog,
            title = "O History (${activeScene.o_history.size})",
            history = activeScene.o_history,
            onDismissRequest = { showOHistoryDialog = false }
        )

        SceneHistoryDialog(
            show = showPlayHistoryDialog,
            title = "Play History (${activeScene.play_history.size})",
            history = activeScene.play_history,
            onDismissRequest = { showPlayHistoryDialog = false }
        )

        if (showAddTagSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddTagSheet = false }
            ) {
                var searchInput by remember { mutableStateOf("") }
                LaunchedEffect(searchInput) {
                    delay(300.milliseconds)
                    viewModel.searchTags(serverUrl, apiKey, username, password, searchInput)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Add Tag", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Tag Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.tagSuggestions.isNotEmpty()) {
                        Text("Suggestions", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.tagSuggestions.forEach { suggestion ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!tagsList.any { it.first == suggestion.id }) {
                                                tagsList.add(suggestion.id to suggestion.name)
                                            }
                                            searchInput = ""
                                            showAddTagSheet = false
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = suggestion.name,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (searchInput.isNotBlank()) {
                                viewModel.getOrCreateTag(
                                    serverUrl = serverUrl,
                                    apiKey = apiKey,
                                    username = username,
                                    password = password,
                                    tagName = searchInput,
                                    onResult = { id, name ->
                                        if (!tagsList.any { it.first == id }) {
                                            tagsList.add(id to name)
                                        }
                                        searchInput = ""
                                        showAddTagSheet = false
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Tag")
                    }
                }
            }
        }

        if (showAddPerformerSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddPerformerSheet = false }
            ) {
                var searchInput by remember { mutableStateOf("") }
                LaunchedEffect(searchInput) {
                    delay(300.milliseconds)
                    viewModel.searchPerformers(serverUrl, apiKey, username, password, searchInput)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Add Performer", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        label = { Text("Performer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.performerSuggestions.isNotEmpty()) {
                        Text("Suggestions", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.performerSuggestions.forEach { suggestion ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!performersList.any { it.first == suggestion.id }) {
                                                performersList.add(suggestion.id to suggestion.name)
                                            }
                                            searchInput = ""
                                            showAddPerformerSheet = false
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = suggestion.name,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (searchInput.isNotBlank()) {
                                viewModel.getOrCreatePerformer(
                                    serverUrl = serverUrl,
                                    apiKey = apiKey,
                                    username = username,
                                    password = password,
                                    performerName = searchInput,
                                    onResult = { id, name ->
                                        if (!performersList.any { it.first == id }) {
                                            performersList.add(id to name)
                                        }
                                        searchInput = ""
                                        showAddPerformerSheet = false
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Performer")
                    }
                }
            }
        }
    }
}
