package com.github.damontecres.stashapp.mobile.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.FindSavedFiltersQuery
import com.github.damontecres.stashapp.mobile.api.FindScenesQuery
import com.github.damontecres.stashapp.mobile.api.type.FilterMode
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.util.FilterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

enum class PlaylistSortField {
    NAME,
    SCENE_COUNT
}

data class PlaylistUiItem(
    val id: String,
    val name: String,
    val isPinned: Boolean,
    val isPermanentPinned: Boolean = false,
    val isUserPinned: Boolean = false,
    val sceneCount: Int? = null,
    val savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
)

data class SceneHomeState(
    val isLoading: Boolean = false,
    val playlists: List<PlaylistUiItem> = emptyList(),
    val activeSortField: PlaylistSortField = PlaylistSortField.NAME,
    val activeDirection: SortDirectionEnum = SortDirectionEnum.ASC,
    val error: String? = null
)

class SceneHomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(SceneHomeState(isLoading = true))
    val state: StateFlow<SceneHomeState> = _state.asStateFlow()

    private var currentServerUrl = ""
    private var currentApiKey = ""
    private var currentUsername = ""
    private var currentPassword = ""
    private var sharedPrefsContext: Context? = null

    fun init(
        context: Context,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        sharedPrefsContext = context.applicationContext
        val serverChanged = currentServerUrl != serverUrl || currentApiKey != apiKey
        currentServerUrl = serverUrl
        currentApiKey = apiKey
        currentUsername = username
        currentPassword = password

        if (serverChanged || _state.value.playlists.isEmpty()) {
            loadPlaylists()
        }
    }

    fun refresh() {
        loadPlaylists()
    }

    fun setSortField(field: PlaylistSortField) {
        _state.value = _state.value.copy(
            activeSortField = field,
            playlists = sortPlaylists(_state.value.playlists, field, _state.value.activeDirection)
        )
    }

    fun toggleSortDirection() {
        val newDirection = if (_state.value.activeDirection == SortDirectionEnum.DESC) SortDirectionEnum.ASC else SortDirectionEnum.DESC
        _state.value = _state.value.copy(
            activeDirection = newDirection,
            playlists = sortPlaylists(_state.value.playlists, _state.value.activeSortField, newDirection)
        )
    }

    private fun sortPlaylists(
        items: List<PlaylistUiItem>,
        field: PlaylistSortField,
        direction: SortDirectionEnum
    ): List<PlaylistUiItem> {
        val comparator = when (field) {
            PlaylistSortField.NAME -> {
                if (direction == SortDirectionEnum.ASC) {
                    compareBy<PlaylistUiItem> { it.name.lowercase() }
                } else {
                    compareByDescending<PlaylistUiItem> { it.name.lowercase() }
                }
            }
            PlaylistSortField.SCENE_COUNT -> {
                if (direction == SortDirectionEnum.DESC) {
                    compareByDescending<PlaylistUiItem> { it.sceneCount ?: 0 }
                        .thenBy { it.name.lowercase() }
                } else {
                    compareBy<PlaylistUiItem> { it.sceneCount ?: 0 }
                        .thenBy { it.name.lowercase() }
                }
            }
        }

        // Pinned items ALWAYS stay on top
        return items.sortedWith(
            compareByDescending<PlaylistUiItem> { it.isPinned }
                .thenComparing(comparator)
        )
    }

    private fun getPinnedIds(): Set<String> {
        val ctx = sharedPrefsContext ?: return emptySet()
        val prefs = ctx.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("pinned_playlists", emptySet()) ?: emptySet()
    }

    fun togglePin(playlistId: String) {
        val ctx = sharedPrefsContext ?: return
        val prefs = ctx.getSharedPreferences("stash_mobile_prefs", Context.MODE_PRIVATE)
        val currentPinned = getPinnedIds().toMutableSet()

        if (currentPinned.contains(playlistId)) {
            currentPinned.remove(playlistId)
        } else {
            currentPinned.add(playlistId)
        }

        prefs.edit().putStringSet("pinned_playlists", currentPinned).apply()

        val updated = _state.value.playlists.map { item ->
            if (item.id == playlistId) {
                val isUserPinned = currentPinned.contains(playlistId)
                item.copy(
                    isUserPinned = isUserPinned,
                    isPinned = item.isPermanentPinned || isUserPinned
                )
            } else {
                item
            }
        }

        _state.value = _state.value.copy(
            playlists = sortPlaylists(updated, _state.value.activeSortField, _state.value.activeDirection)
        )
    }

    private fun loadPlaylists() {
        if (currentServerUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val apolloClient = StashMobileClient.createApolloClient(
                    currentServerUrl,
                    currentApiKey,
                    currentUsername,
                    currentPassword
                )

                val response = apolloClient.query(
                    FindSavedFiltersQuery(mode = FilterMode.SCENES)
                ).execute()

                val savedFilters = response.data?.findSavedFilters.orEmpty()
                val pinnedSet = getPinnedIds()

                val allScenesItem = PlaylistUiItem(
                    id = "all_scenes",
                    name = "All Scenes",
                    isPinned = true,
                    isPermanentPinned = true,
                    savedFilter = null
                )

                val filterItems = savedFilters.map { saved ->
                    val isUserPinned = pinnedSet.contains(saved.savedFilter.id)
                    PlaylistUiItem(
                        id = saved.savedFilter.id,
                        name = saved.savedFilter.name,
                        isPinned = isUserPinned,
                        isUserPinned = isUserPinned,
                        savedFilter = saved
                    )
                }

                val combined = listOf(allScenesItem) + filterItems
                val sortedList = sortPlaylists(combined, _state.value.activeSortField, _state.value.activeDirection)

                _state.value = _state.value.copy(
                    isLoading = false,
                    playlists = sortedList
                )

                // Fetch total scene counts for playlists in background
                fetchSceneCounts(apolloClient, combined)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlists")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Failed to load playlists"
                )
            }
        }
    }

    private fun fetchSceneCounts(
        apolloClient: com.apollographql.apollo.ApolloClient,
        items: List<PlaylistUiItem>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedItems = items.map { item ->
                try {
                    val savedFind = item.savedFilter?.savedFilter?.find_filter
                    val objectFilter = item.savedFilter?.savedFilter?.object_filter
                    val sceneFilter = FilterParser.convertSceneFilterType(objectFilter)

                    val findFilter = FindFilterType(
                        q = if (!savedFind?.q.isNullOrBlank()) Optional.present(savedFind.q) else Optional.absent(),
                        sort = if (!savedFind?.sort.isNullOrBlank()) Optional.present(savedFind.sort) else Optional.absent(),
                        direction = if (savedFind?.direction != null) Optional.present(savedFind.direction) else Optional.absent(),
                        per_page = Optional.present(1)
                    )

                    val response = apolloClient.query(
                        FindScenesQuery(
                            filter = findFilter,
                            scene_filter = sceneFilter,
                            ids = null
                        )
                    ).execute()

                    val count = response.data?.findScenes?.count ?: 0
                    item.copy(sceneCount = count)
                } catch (e: Exception) {
                    item
                }
            }

            _state.value = _state.value.copy(
                playlists = sortPlaylists(updatedItems, _state.value.activeSortField, _state.value.activeDirection)
            )
        }
    }
}
