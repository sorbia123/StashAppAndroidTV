package com.github.damontecres.stashapp.mobile.ui.settings

import android.content.Context
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
import com.github.damontecres.stashapp.mobile.api.FindTagsQuery
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var quickAddTagsString by remember {
        mutableStateOf(sharedPrefs.getString("quick_add_tags", "archive,desi,foreigner") ?: "archive,desi,foreigner")
    }

    val quickAddTags = remember(quickAddTagsString) {
        quickAddTagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableStateList()
    }

    var newTagInput by remember { mutableStateOf("") }
    var tagSuggestions by remember { mutableStateOf<List<FindTagsQuery.Tag>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }

    fun saveTags(updatedList: List<String>) {
        val str = updatedList.joinToString(",")
        quickAddTagsString = str
        sharedPrefs.edit().putString("quick_add_tags", str).apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tag Manager (Quick-Add Tags)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
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
            Text(
                text = "Configure Quick-Add Tags used in the Scene Player",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { query ->
                        newTagInput = query
                        showSuggestions = query.isNotBlank()
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
                                        tagSuggestions = tags
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to search tags")
                                }
                            }
                        } else {
                            tagSuggestions = emptyList()
                        }
                    },
                    label = { Text("Add Quick-Add Tag Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        val trimmed = newTagInput.trim()
                        if (trimmed.isNotBlank() && !quickAddTags.contains(trimmed)) {
                            quickAddTags.add(trimmed)
                            saveTags(quickAddTags)
                            newTagInput = ""
                            showSuggestions = false
                        }
                    }
                ) {
                    Text("Add")
                }
            }

            if (showSuggestions && tagSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        tagSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion.name) },
                                onClick = {
                                    if (!quickAddTags.contains(suggestion.name)) {
                                        quickAddTags.add(suggestion.name)
                                        saveTags(quickAddTags)
                                    }
                                    newTagInput = ""
                                    showSuggestions = false
                                    tagSuggestions = emptyList()
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Configured Quick-Add Tags (${quickAddTags.size}):",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickAddTags) { tagName ->
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
                                    quickAddTags.remove(tagName)
                                    saveTags(quickAddTags)
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
