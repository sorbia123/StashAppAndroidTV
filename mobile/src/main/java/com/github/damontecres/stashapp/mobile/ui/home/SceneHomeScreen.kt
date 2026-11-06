package com.github.damontecres.stashapp.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum

@Composable
fun PinIcon(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.35f, h * 0.15f)
            lineTo(w * 0.65f, h * 0.15f)
            lineTo(w * 0.6f, h * 0.45f)
            lineTo(w * 0.75f, h * 0.65f)
            lineTo(w * 0.25f, h * 0.65f)
            lineTo(w * 0.4f, h * 0.45f)
            close()
        }
        drawPath(path, color = color)
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.65f),
            end = Offset(w * 0.5f, h * 0.9f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SceneHomeScreen(
    serverUrl: String,
    apiKey: String,
    username: String = "",
    password: String = "",
    onBack: () -> Unit = {},
    onPlaylistSelected: (PlaylistUiItem) -> Unit,
    viewModel: SceneHomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(serverUrl, apiKey) {
        viewModel.init(context, serverUrl, apiKey, username, password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scene Playlists & Filters") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                },
                actions = {
                    // Direction toggle arrow icon
                    IconButton(
                        onClick = { viewModel.toggleSortDirection() }
                    ) {
                        DirectionArrowIcon(isDescending = state.activeDirection == SortDirectionEnum.DESC)
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            MoreVertIcon()
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sort By Name") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setSortField(PlaylistSortField.NAME)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort By Scene Count") },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.setSortField(PlaylistSortField.SCENE_COUNT)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading && state.playlists.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistSelected(playlist) },
                            onTogglePin = { viewModel.togglePin(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: PlaylistUiItem,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (!playlist.isPermanentPinned) {
                            showMenu = true
                        }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (playlist.isPinned)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = if (playlist.isPinned)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (playlist.sceneCount != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "${playlist.sceneCount} scenes",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (playlist.isPinned) {
                        PinIcon(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (playlist.isUserPinned) "Unpin Playlist" else "Pin to Top") },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
        }
    }
}
