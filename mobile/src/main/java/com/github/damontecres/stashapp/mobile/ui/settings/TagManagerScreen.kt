package com.github.damontecres.stashapp.mobile.ui.settings

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.CreateTagMutation
import com.github.damontecres.stashapp.mobile.api.FindTagsQuery
import com.github.damontecres.stashapp.mobile.api.TagDestroyMutation
import com.github.damontecres.stashapp.mobile.api.TagUpdateMutation
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.TagCreateInput
import com.github.damontecres.stashapp.mobile.api.type.TagUpdateInput
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerScreen(
    serverUrl: String,
    apiKey: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    onTagClick: (tagId: String, tagName: String) -> Unit,
    viewModel: TagManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: All Tags, 1: Quick-Add Tags

    // Quick-Add Tags state
    var quickAddTagsString by remember {
        mutableStateOf(sharedPrefs.getString("quick_add_tags", "archive,desi,foreigner") ?: "archive,desi,foreigner")
    }
    val quickAddTags = remember(quickAddTagsString) {
        quickAddTagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableStateList()
    }
    var quickAddInput by remember { mutableStateOf("") }
    var quickAddSuggestions by remember { mutableStateOf<List<FindTagsQuery.Tag>>(emptyList()) }
    var showQuickAddSuggestions by remember { mutableStateOf(false) }

    // All Tags search & sort state
    var searchQuery by remember { mutableStateOf(state.searchQuery) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTagNameInput by remember { mutableStateOf("") }

    var editTagTarget by remember { mutableStateOf<FindTagsQuery.Tag?>(null) }
    var editTagNameInput by remember { mutableStateOf("") }

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

    fun saveQuickAddTags(updatedList: List<String>) {
        val str = updatedList.joinToString(",")
        quickAddTagsString = str
        sharedPrefs.edit().putString("quick_add_tags", str).apply()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Tag Manager") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            BackArrowIcon()
                        }
                    },
                    actions = {
                        if (selectedTab == 0) {
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
                                    "scenes_count" to "Scene Count",
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
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("All Tags (${state.totalCount})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Quick-Add Tags (${quickAddTags.size})") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        newTagNameInput = ""
                        showCreateDialog = true
                    }
                ) {
                    Text("+ New")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // All Tags Tab with pagination & sorting
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search all Stash tags...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        when {
                            state.isLoading && state.tags.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            state.tags.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No tags found", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(state.tags) { index, tag ->
                                        if (index >= state.tags.size - 3 && state.canLoadMore && !state.isFetchingNextPage) {
                                            LaunchedEffect(index) {
                                                viewModel.loadNextPage(serverUrl, apiKey, username, password)
                                            }
                                        }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onTagClick(tag.id, tag.name) },
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
                                                        text = tag.name,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    Text(
                                                        text = "${tag.scene_count} scenes • Tap to view",
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
                                                            editTagTarget = tag
                                                            editTagNameInput = tag.name
                                                        }
                                                    ) {
                                                        Text("Edit")
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            scope.launch(Dispatchers.IO) {
                                                                try {
                                                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                                                    client.mutation(TagDestroyMutation(input = com.github.damontecres.stashapp.mobile.api.type.TagDestroyInput(id = tag.id))).execute()
                                                                    viewModel.refresh(serverUrl, apiKey, username, password)
                                                                } catch (e: Exception) {
                                                                    Timber.e(e, "Failed to delete tag")
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
                1 -> {
                    // Quick-Add Tab
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Configure sticky quick-add tags for the Scene Player",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quickAddInput,
                                onValueChange = { query ->
                                    quickAddInput = query
                                    showQuickAddSuggestions = query.isNotBlank()
                                    if (query.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                                val response = client.query(
                                                    FindTagsQuery(
                                                        filter = FindFilterType(q = Optional.present(query)),
                                                        tag_filter = null,
                                                        ids = null
                                                    )
                                                ).execute()
                                                val tags = response.data?.findTags?.tags.orEmpty()
                                                withContext(Dispatchers.Main) {
                                                    quickAddSuggestions = tags
                                                }
                                            } catch (e: Exception) {
                                                Timber.e(e, "Failed to search tags")
                                            }
                                        }
                                    } else {
                                        quickAddSuggestions = emptyList()
                                    }
                                },
                                label = { Text("Add Quick-Add Tag") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    val trimmed = quickAddInput.trim()
                                    if (trimmed.isNotBlank() && !quickAddTags.contains(trimmed)) {
                                        quickAddTags.add(trimmed)
                                        saveQuickAddTags(quickAddTags)
                                        quickAddInput = ""
                                        showQuickAddSuggestions = false
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        }

                        if (showQuickAddSuggestions && quickAddSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    quickAddSuggestions.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = { Text(suggestion.name) },
                                            onClick = {
                                                if (!quickAddTags.contains(suggestion.name)) {
                                                    quickAddTags.add(suggestion.name)
                                                    saveQuickAddTags(quickAddTags)
                                                }
                                                quickAddInput = ""
                                                showQuickAddSuggestions = false
                                                quickAddSuggestions = emptyList()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(count = quickAddTags.size) { index ->
                                val tagName = quickAddTags[index]
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tagName,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        TextButton(
                                            onClick = {
                                                quickAddTags.remove(tagName as Any)
                                                saveQuickAddTags(quickAddTags)
                                            }
                                        ) {
                                            Text("Remove", color = MaterialTheme.colorScheme.error)
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

    // Create Tag Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Tag") },
            text = {
                OutlinedTextField(
                    value = newTagNameInput,
                    onValueChange = { newTagNameInput = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newTagNameInput.trim()
                        if (name.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                    client.mutation(CreateTagMutation(input = TagCreateInput(name = name))).execute()
                                    showCreateDialog = false
                                    viewModel.refresh(serverUrl, apiKey, username, password)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to create tag")
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

    // Edit Tag Dialog
    if (editTagTarget != null) {
        AlertDialog(
            onDismissRequest = { editTagTarget = null },
            title = { Text("Edit Tag") },
            text = {
                OutlinedTextField(
                    value = editTagNameInput,
                    onValueChange = { editTagNameInput = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = editTagNameInput.trim()
                        val target = editTagTarget
                        if (newName.isNotBlank() && target != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                    client.mutation(
                                        TagUpdateMutation(
                                            input = TagUpdateInput(
                                                id = target.id,
                                                name = Optional.present(newName)
                                            )
                                        )
                                    ).execute()
                                    editTagTarget = null
                                    viewModel.refresh(serverUrl, apiKey, username, password)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to update tag")
                                }
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTagTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
