@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.github.damontecres.stashapp.mobile.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.api.fragment.VideoFile
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import com.github.damontecres.stashapp.mobile.ui.home.MoreVertIcon
import com.github.damontecres.stashapp.mobile.ui.scene.SceneHistoryDialog
import com.github.damontecres.stashapp.mobile.ui.scene.parseTimeToString
import com.github.damontecres.stashapp.mobile.util.fixStashMediaUrl
import com.github.damontecres.stashapp.mobile.util.titleOrFilename
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
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
    return String.format(
        Locale.getDefault(),
        "%.1f %s",
        b / Math.pow(1024.0, digitGroups.toDouble()),
        units[digitGroups]
    )
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
        drawPath(
            path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun VideoPlayerContainer(
    exoPlayer: ExoPlayer,
    effectiveQueue: List<SlimSceneData>,
    currentIndex: Int,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rewindSeconds by remember { mutableStateOf(0) }
    var fastForwardSeconds by remember { mutableStateOf(0) }
    var seekJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.pointerInput(isFullscreen) {
            var totalDragY = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDragY = 0f },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragY += dragAmount
                },
                onDragEnd = {
                    if (totalDragY < -60f && !isFullscreen) {
                        onToggleFullscreen()
                    } else if (totalDragY > 60f && isFullscreen) {
                        onToggleFullscreen()
                    }
                }
            )
        }
    ) {
        AndroidView(
            factory = { ctx ->
                val playerView = PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (playerView.isControllerFullyVisible) {
                            playerView.hideController()
                        } else {
                            playerView.showController()
                        }
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        val width = playerView.width
                        val isLeft = e.x < width / 2f
                        val currentPos = exoPlayer.currentPosition
                        if (isLeft) {
                            val newPos = (currentPos - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            rewindSeconds += 10
                            fastForwardSeconds = 0
                            seekJob?.cancel()
                            seekJob = scope.launch {
                                delay(1000)
                                rewindSeconds = 0
                            }
                        } else {
                            val duration = if (exoPlayer.duration > 0) exoPlayer.duration else Long.MAX_VALUE
                            val newPos = (currentPos + 10000L).coerceAtMost(duration)
                            exoPlayer.seekTo(newPos)
                            fastForwardSeconds += 10
                            rewindSeconds = 0
                            seekJob?.cancel()
                            seekJob = scope.launch {
                                delay(1000)
                                fastForwardSeconds = 0
                            }
                        }
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (e1 != null) {
                            val diffY = e2.y - e1.y
                            val diffX = e2.x - e1.x
                            if (Math.abs(diffY) > Math.abs(diffX) && Math.abs(diffY) > 50 && Math.abs(velocityY) > 50) {
                                if (diffY < 0 && !isFullscreen) {
                                    onToggleFullscreen()
                                    return true
                                } else if (diffY > 0 && isFullscreen) {
                                    onToggleFullscreen()
                                    return true
                                }
                            }
                        }
                        return false
                    }
                })

                val touchListener = android.view.View.OnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                }

                fun attachTouchListener(v: android.view.View) {
                    if (v !is android.widget.ImageButton && v !is androidx.media3.ui.DefaultTimeBar) {
                        v.setOnTouchListener(touchListener)
                    }
                    if (v is ViewGroup) {
                        for (i in 0 until v.childCount) {
                            attachTouchListener(v.getChildAt(i))
                        }
                    }
                }

                playerView.post {
                    attachTouchListener(playerView)
                }
                playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { _ ->
                    playerView.post {
                        attachTouchListener(playerView)
                    }
                })

                playerView
            },
            update = { pv ->
                pv.player = exoPlayer
            },
            modifier = Modifier.fillMaxSize()
        )

        // Polished YouTube-style Seek Ripple / Badge Indicator
        if (rewindSeconds > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterStart)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("◀◀", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "-${rewindSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        if (fastForwardSeconds > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
                    .align(Alignment.CenterEnd)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "+${fastForwardSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("▶▶", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

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
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.65f), shape = MaterialTheme.shapes.small)
        ) {
            FullscreenIcon(isFullscreen = isFullscreen)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GroupedMetadataChipsBar(
    videoFile: VideoFile?,
    oCount: Int,
    playCount: Int,
    onOCountClick: () -> Unit,
    onPlayCountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (videoFile != null) {
                val fps = "${String.format(Locale.getDefault(), "%.0f", videoFile.frame_rate)} fps"
                if (videoFile.width > 0 && videoFile.height > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${videoFile.width}x${videoFile.height} @ $fps") }
                    )
                }
                SuggestionChip(
                    onClick = {},
                    label = { Text(formatBytes(videoFile.size)) }
                )
            }

            AssistChip(
                onClick = onOCountClick,
                label = { Text("$oCount Os") }
            )

            AssistChip(
                onClick = onPlayCountClick,
                label = { Text("$playCount Views") }
            )
        }
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
        mutableStateOf(activeScene.tags.map { it.slimTagData.id to it.slimTagData.name }
            .toMutableStateList())
    }
    var performersList by remember(activeScene.id) {
        mutableStateOf(activeScene.performers.map { it.id to it.name }.toMutableStateList())
    }

    var isFullscreen by remember { mutableStateOf(false) }
    var showOHistoryDialog by remember { mutableStateOf(false) }
    var showPlayHistoryDialog by remember { mutableStateOf(false) }
    var showAddTagSheet by remember { mutableStateOf(false) }
    var showAddPerformerSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }

    val videoFile = activeScene.files.firstOrNull()?.videoFile
    val isPortraitVideo = (videoFile?.height ?: 0) > (videoFile?.width ?: 0)

    val stickyTagDefaults = listOf("archive", "desi", "foreigner")

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
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
            }

            setMediaSources(mediaSources)
            seekTo(initialIndex.coerceIn(0, effectiveQueue.size - 1), 0)
            prepare()
            playWhenReady = true
        }
    }

    var hasIncrementedPlayCount by remember(activeScene.id) { mutableStateOf(false) }
    var showPlayRecordedNotice by remember { mutableStateOf(false) }
    var recordedPlayCount by remember { mutableStateOf(0) }
    var localPlayCount by remember(activeScene.id) {
        mutableStateOf(activeScene.play_count ?: activeScene.play_history.size)
    }
    var playHistoryList by remember(activeScene.id) {
        mutableStateOf(activeScene.play_history.toMutableStateList())
    }

    LaunchedEffect(showPlayRecordedNotice) {
        if (showPlayRecordedNotice) {
            delay(2500)
            showPlayRecordedNotice = false
        }
    }

    LaunchedEffect(exoPlayer, activeScene.id) {
        var accumulatedPlayTime = 0L
        val sceneDurationMs = (activeScene.files.firstOrNull()?.videoFile?.duration ?: 0.0) * 1000.0

        while (true) {
            delay(10000.milliseconds)
            if (exoPlayer.isPlaying) {
                accumulatedPlayTime += 10000L
                val currentPos = exoPlayer.currentPosition
                viewModel.recordSceneActivity(
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    username = username,
                    password = password,
                    sceneId = activeScene.id,
                    positionMs = currentPos,
                    durationMs = 10000L
                )

                val minThreshold = if (sceneDurationMs > 0) (sceneDurationMs * 0.15).toLong().coerceAtMost(30000L) else 15000L
                if (!hasIncrementedPlayCount && accumulatedPlayTime >= minThreshold) {
                    hasIncrementedPlayCount = true
                    viewModel.incrementPlayCount(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        username = username,
                        password = password,
                        sceneId = activeScene.id,
                        onSuccess = { count ->
                            recordedPlayCount = count
                            localPlayCount = count
                            playHistoryList.add(Instant.now().toString())
                            showPlayRecordedNotice = true
                        }
                    )
                }
            }
        }
    }

    DisposableEffect(exoPlayer, activeScene.id) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newIndex = exoPlayer.currentMediaItemIndex
                if (newIndex in effectiveQueue.indices && newIndex != currentIndex) {
                    currentIndex = newIndex
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val currentPos = exoPlayer.currentPosition
                    viewModel.recordSceneActivity(
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                        username = username,
                        password = password,
                        sceneId = activeScene.id,
                        positionMs = currentPos,
                        durationMs = 0L
                    )
                    if (!hasIncrementedPlayCount) {
                        hasIncrementedPlayCount = true
                        viewModel.incrementPlayCount(
                            serverUrl = serverUrl,
                            apiKey = apiKey,
                            username = username,
                            password = password,
                            sceneId = activeScene.id,
                            onSuccess = { count ->
                                recordedPlayCount = count
                                localPlayCount = count
                                showPlayRecordedNotice = true
                            }
                        )
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            viewModel.recordSceneActivity(
                serverUrl = serverUrl,
                apiKey = apiKey,
                username = username,
                password = password,
                sceneId = activeScene.id,
                positionMs = exoPlayer.currentPosition,
                durationMs = 0L
            )
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
                VideoPlayerContainer(
                    exoPlayer = exoPlayer,
                    effectiveQueue = effectiveQueue,
                    currentIndex = currentIndex,
                    isFullscreen = true,
                    onToggleFullscreen = toggleFullscreen,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        VideoPlayerContainer(
                            exoPlayer = exoPlayer,
                            effectiveQueue = effectiveQueue,
                            currentIndex = currentIndex,
                            isFullscreen = false,
                            onToggleFullscreen = toggleFullscreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Title Row (Clickable -> Opens Description Sheet)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDescriptionSheet = true },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = activeScene.titleOrFilename ?: "Untitled Scene",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                IconButton(onClick = { showDescriptionSheet = true }) {
                                    MoreVertIcon()
                                }
                            }

                            // Grouped Chips Bar (Resolution, Size, O Count, Play Count in FlowRow)
                            GroupedMetadataChipsBar(
                                videoFile = videoFile,
                                oCount = activeScene.o_counter ?: activeScene.o_history.size,
                                playCount = localPlayCount,
                                onOCountClick = { showOHistoryDialog = true },
                                onPlayCountClick = { showPlayHistoryDialog = true }
                            )

                            // Tags Section with Sticky Quick-Add Chips
                            Column() {
                                Text(text = "Tags", style = MaterialTheme.typography.titleMedium)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Quick Add:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    stickyTagDefaults.forEach { stickyTagName ->
                                        val isAlreadyAdded =
                                            tagsList.any { it.second.equals(stickyTagName, ignoreCase = true) }
                                        FilterChip(
                                            selected = isAlreadyAdded,
                                            onClick = {
                                                if (!isAlreadyAdded) {
                                                    viewModel.getOrCreateTag(
                                                        serverUrl = serverUrl,
                                                        apiKey = apiKey,
                                                        username = username,
                                                        password = password,
                                                        tagName = stickyTagName,
                                                        onResult = { id, name ->
                                                            if (!tagsList.any { it.first == id }) {
                                                                tagsList.add(id to name)
                                                            }
                                                        }
                                                    )
                                                }
                                            },
                                            label = { Text("+ $stickyTagName") }
                                        )
                                    }
                                }

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
                            }

                            // Performers Section with Sticky Quick-Add Chips
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            }
                        }
                    }

                    // Sticky Bottom Save Button Container
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Save Changes")
                                }
                            }

                            if (state.saveError != null) {
                                Text(
                                    text = "Error: ${state.saveError}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (state.saveSuccess) {
                                Text(
                                    text = "Scene saved successfully!",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Visual Cue Toast / Snackbar Notice when Play Count is Recorded
            if (showPlayRecordedNotice) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Play recorded! Total plays: $recordedPlayCount", style = MaterialTheme.typography.bodyMedium)
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
            title = "Play History (${playHistoryList.size})",
            history = playHistoryList,
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

        if (showDescriptionSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDescriptionSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = activeScene.titleOrFilename ?: "Scene Details",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (!activeScene.details.isNullOrBlank()) {
                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeScene.details,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider()
                    }

                    if (videoFile != null) {
                        Text(
                            text = "File Metadata",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Path: ${videoFile.path}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Size: ${formatBytes(videoFile.size)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Modified: ${parseTimeToString(videoFile.mod_time) ?: videoFile.mod_time.toString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Codec: ${videoFile.video_codec ?: "N/A"} / ${videoFile.audio_codec ?: "N/A"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Resolution: ${videoFile.width}x${videoFile.height}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
