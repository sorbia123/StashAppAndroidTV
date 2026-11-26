package com.github.damontecres.stashapp.mobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.CreatePerformerMutation
import com.github.damontecres.stashapp.mobile.api.FindPerformersQuery
import com.github.damontecres.stashapp.mobile.api.PerformerDestroyMutation
import com.github.damontecres.stashapp.mobile.api.PerformerUpdateMutation
import com.github.damontecres.stashapp.mobile.api.type.PerformerCreateInput
import com.github.damontecres.stashapp.mobile.api.type.PerformerUpdateInput
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformerManagerScreen(
    serverUrl: String,
    apiKey: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    onPerformerClick: (performerId: String, performerName: String) -> Unit,
    viewModel: PerformerManagerViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    var searchQuery by remember { mutableStateOf(state.searchQuery) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPerformerNameInput by remember { mutableStateOf("") }

    var editPerformerTarget by remember { mutableStateOf<FindPerformersQuery.Performer?>(null) }
    var editPerformerNameInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refresh(serverUrl, apiKey, username, password)
    }

    // Debounced search
    var isInitialSearch by remember { mutableStateOf(true) }
    LaunchedEffect(searchQuery) {
        if (isInitialSearch) {
            isInitialSearch = false
            return@LaunchedEffect
        }
        delay(400.milliseconds)
        viewModel.updateSearchQuery(searchQuery, serverUrl, apiKey, username, password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performer Manager (${state.totalCount})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        MoreVertIcon()
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (state.activeDirection == SortDirectionEnum.DESC) "Direction: Descending" else "Direction: Ascending") },
                            onClick = {
                                showSortMenu = false
                                viewModel.updateSort(state.activeSort, serverUrl, apiKey, username, password)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Sort By", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
                            onClick = {}
                        )
                        val sortOptions = listOf(
                            "name" to "Name",
                            "created_at" to "Date Added",
                            "updated_at" to "Updated At",
                            "scene_count" to "Scene Count",
                            "random" to "Random"
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
                                    showSortMenu = false
                                    viewModel.updateSort(key, serverUrl, apiKey, username, password)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    newPerformerNameInput = ""
                    showCreateDialog = true
                }
            ) {
                Text("+ New")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search all performers...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                state.isLoading && state.performers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.performers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No performers found", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.performers) { index, performer ->
                            if (index >= state.performers.size - 3 && state.canLoadMore && !state.isFetchingNextPage) {
                                LaunchedEffect(index) {
                                    viewModel.loadNextPage(serverUrl, apiKey, username, password)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPerformerClick(performer.id, performer.name) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = performer.name,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${performer.scene_count} scenes • Tap to view",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                editPerformerTarget = performer
                                                editPerformerNameInput = performer.name
                                            }
                                        ) {
                                            Text("Edit")
                                        }

                                        TextButton(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    try {
                                                        val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                                        client.mutation(PerformerDestroyMutation(input = com.github.damontecres.stashapp.mobile.api.type.PerformerDestroyInput(id = performer.id))).execute()
                                                        viewModel.refresh(serverUrl, apiKey, username, password)
                                                    } catch (e: Exception) {
                                                        Timber.e(e, "Failed to delete performer")
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Delete", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.isFetchingNextPage) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Performer Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Performer") },
            text = {
                OutlinedTextField(
                    value = newPerformerNameInput,
                    onValueChange = { newPerformerNameInput = it },
                    label = { Text("Performer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPerformerNameInput.trim()
                        if (name.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                    client.mutation(CreatePerformerMutation(input = PerformerCreateInput(name = name))).execute()
                                    showCreateDialog = false
                                    viewModel.refresh(serverUrl, apiKey, username, password)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to create performer")
                                }
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Performer Dialog
    if (editPerformerTarget != null) {
        AlertDialog(
            onDismissRequest = { editPerformerTarget = null },
            title = { Text("Edit Performer") },
            text = {
                OutlinedTextField(
                    value = editPerformerNameInput,
                    onValueChange = { editPerformerNameInput = it },
                    label = { Text("Performer Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = editPerformerNameInput.trim()
                        val target = editPerformerTarget
                        if (newName.isNotBlank() && target != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                    client.mutation(
                                        PerformerUpdateMutation(
                                            input = PerformerUpdateInput(
                                                id = target.id,
                                                name = Optional.present(newName)
                                            )
                                        )
                                    ).execute()
                                    editPerformerTarget = null
                                    viewModel.refresh(serverUrl, apiKey, username, password)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to update performer")
                                }
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editPerformerTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
