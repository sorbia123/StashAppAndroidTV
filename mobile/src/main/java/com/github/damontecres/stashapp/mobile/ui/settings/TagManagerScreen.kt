package com.github.damontecres.stashapp.mobile.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.CreateTagMutation
import com.github.damontecres.stashapp.mobile.api.FindTagsQuery
import com.github.damontecres.stashapp.mobile.api.TagDestroyMutation
import com.github.damontecres.stashapp.mobile.api.TagUpdateMutation
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.TagCreateInput
import com.github.damontecres.stashapp.mobile.api.type.TagUpdateInput
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.ui.home.BackArrowIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagerScreen(
    serverUrl: String,
    apiKey: String,
    username: String,
    password: String,
    onBack: () -> Unit,
    onTagClick: (tagId: String, tagName: String) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var tags by remember { mutableStateOf<List<FindTagsQuery.Tag>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    var quickAddTagsString by remember {
        mutableStateOf(sharedPrefs.getString("quick_add_tags", "archive,desi,foreigner") ?: "archive,desi,foreigner")
    }
    val quickAddTags = remember(quickAddTagsString) {
        quickAddTagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableStateList()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newTagNameInput by remember { mutableStateOf("") }

    var editTagTarget by remember { mutableStateOf<FindTagsQuery.Tag?>(null) }
    var editTagNameInput by remember { mutableStateOf("") }

    fun loadTags(query: String = "") {
        scope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val response = client.query(
                    FindTagsQuery(
                        filter = if (query.isNotBlank()) FindFilterType(q = Optional.present(query)) else null,
                        tag_filter = null,
                        ids = null
                    )
                ).execute()
                val list = response.data?.findTags?.tags.orEmpty()
                withContext(Dispatchers.Main) {
                    tags = list
                    isLoading = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load tags")
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTags()
    }

    fun saveQuickAddTags(updatedList: List<String>) {
        val str = updatedList.joinToString(",")
        quickAddTagsString = str
        sharedPrefs.edit().putString("quick_add_tags", str).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tag Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            newTagNameInput = ""
                            showCreateDialog = true
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("New Tag")
                    }
                }
            )
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
                onValueChange = { query ->
                    searchQuery = query
                    loadTags(query)
                },
                label = { Text("Search tags...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (tags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tags found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tags) { tag ->
                        val isQuickAdd = quickAddTags.contains(tag.name)
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
                                        text = if (isQuickAdd) "★ Quick-Add Enabled" else "Tap to view scenes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isQuickAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Toggle Quick-Add button
                                    TextButton(
                                        onClick = {
                                            if (isQuickAdd) {
                                                quickAddTags.remove(tag.name)
                                            } else {
                                                quickAddTags.add(tag.name)
                                            }
                                            saveQuickAddTags(quickAddTags)
                                        }
                                    ) {
                                        Text(if (isQuickAdd) "Unstar" else "Star Quick-Add")
                                    }

                                    // Edit Tag
                                    TextButton(
                                        onClick = {
                                            editTagTarget = tag
                                            editTagNameInput = tag.name
                                        }
                                    ) {
                                        Text("Edit")
                                    }

                                    // Delete Tag
                                    TextButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val client = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                                                    client.mutation(TagDestroyMutation(input = com.github.damontecres.stashapp.mobile.api.type.TagDestroyInput(id = tag.id))).execute()
                                                    loadTags(searchQuery)
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
                                    loadTags(searchQuery)
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
                                    loadTags(searchQuery)
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
