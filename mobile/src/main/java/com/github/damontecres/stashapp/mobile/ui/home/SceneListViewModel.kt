package com.github.damontecres.stashapp.mobile.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.FindSavedFiltersQuery
import com.github.damontecres.stashapp.mobile.api.FindScenesQuery
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import com.github.damontecres.stashapp.mobile.util.FilterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class SceneListState(
    val isLoading: Boolean = false,
    val isFetchingNextPage: Boolean = false,
    val canLoadMore: Boolean = true,
    val currentPage: Int = 1,
    val totalCount: Int = 0,
    val activeSort: String = "date",
    val activeDirection: SortDirectionEnum = SortDirectionEnum.DESC,
    val searchQuery: String = "",
    val scenes: List<SlimSceneData> = emptyList(),
    val error: String? = null
)

class SceneListViewModel : ViewModel() {
    private val _state = MutableStateFlow(SceneListState(isLoading = true))
    val state: StateFlow<SceneListState> = _state.asStateFlow()

    fun refresh(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
    ) {
        val savedFind = savedFilter?.savedFilter?.find_filter
        val initialSort = savedFind?.sort ?: "date"
        val initialDirection = savedFind?.direction ?: SortDirectionEnum.DESC
        val initialQuery = savedFind?.q ?: ""

        _state.value = SceneListState(
            isLoading = true,
            currentPage = 1,
            canLoadMore = true,
            totalCount = 0,
            activeSort = initialSort,
            activeDirection = initialDirection,
            searchQuery = initialQuery,
            scenes = emptyList()
        )
        loadNextPage(serverUrl, apiKey, username, password, savedFilter)
    }

    fun refreshSingleScene(
        sceneId: String,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val response = apolloClient.query(
                    FindScenesQuery(
                        filter = FindFilterType(per_page = Optional.present(1)),
                        scene_filter = null,
                        ids = listOf(sceneId)
                    )
                ).execute()

                val fetched = response.data?.findScenes?.scenes?.firstOrNull()?.slimSceneData
                if (fetched != null) {
                    val currentList = _state.value.scenes.toMutableList()
                    val idx = currentList.indexOfFirst { it.id == fetched.id }
                    if (idx != -1) {
                        currentList[idx] = fetched
                        _state.value = _state.value.copy(scenes = currentList)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh single scene $sceneId")
            }
        }
    }

    fun selectSortField(
        sortField: String,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
    ) {
        _state.value = _state.value.copy(
            activeSort = sortField,
            currentPage = 1,
            canLoadMore = true,
            scenes = emptyList()
        )
        loadNextPage(serverUrl, apiKey, username, password, savedFilter)
    }

    fun toggleSortDirection(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
    ) {
        val newDirection = if (_state.value.activeDirection == SortDirectionEnum.DESC) SortDirectionEnum.ASC else SortDirectionEnum.DESC
        _state.value = _state.value.copy(
            activeDirection = newDirection,
            currentPage = 1,
            canLoadMore = true,
            scenes = emptyList()
        )
        loadNextPage(serverUrl, apiKey, username, password, savedFilter)
    }

    fun searchScenes(
        query: String,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
    ) {
        _state.value = _state.value.copy(
            searchQuery = query,
            currentPage = 1,
            canLoadMore = true,
            scenes = emptyList()
        )
        loadNextPage(serverUrl, apiKey, username, password, savedFilter)
    }

    fun fetchAllMatchingScenes(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null,
        onReady: (List<SlimSceneData>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val savedFind = savedFilter?.savedFilter?.find_filter
                val objectFilter = savedFilter?.savedFilter?.object_filter
                val sceneFilter = FilterParser.convertSceneFilterType(objectFilter)

                val effectiveSort = _state.value.activeSort
                val effectiveDirection = _state.value.activeDirection
                val effectiveQuery = _state.value.searchQuery.ifBlank { savedFind?.q }

                val findFilter = FindFilterType(
                    q = if (!effectiveQuery.isNullOrBlank()) Optional.present(effectiveQuery) else Optional.absent(),
                    sort = Optional.present(effectiveSort),
                    direction = Optional.present(effectiveDirection),
                    per_page = Optional.present(-1)
                )

                val response = apolloClient.query(
                    FindScenesQuery(
                        filter = findFilter,
                        scene_filter = sceneFilter,
                        ids = null
                    )
                ).execute()

                val allScenes = response.data?.findScenes?.scenes?.map { it.slimSceneData }.orEmpty()
                withContext(Dispatchers.Main) {
                    onReady(allScenes)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch all matching scenes for playback queue")
                withContext(Dispatchers.Main) {
                    onReady(_state.value.scenes)
                }
            }
        }
    }

    fun loadNextPage(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        savedFilter: FindSavedFiltersQuery.FindSavedFilter? = null
    ) {
        if (_state.value.isFetchingNextPage || !_state.value.canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isFetchingNextPage = true)
                val nextPage = _state.value.currentPage
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                val savedFind = savedFilter?.savedFilter?.find_filter
                val objectFilter = savedFilter?.savedFilter?.object_filter
                val sceneFilter = FilterParser.convertSceneFilterType(objectFilter)

                val effectiveSort = _state.value.activeSort
                val effectiveDirection = _state.value.activeDirection
                val effectiveQuery = _state.value.searchQuery.ifBlank { savedFind?.q }

                val findFilter = FindFilterType(
                    q = if (!effectiveQuery.isNullOrBlank()) Optional.present(effectiveQuery) else Optional.absent(),
                    sort = Optional.present(effectiveSort),
                    direction = Optional.present(effectiveDirection),
                    page = Optional.present(nextPage),
                    per_page = Optional.present(20)
                )

                val response = apolloClient.query(
                    FindScenesQuery(
                        filter = findFilter,
                        scene_filter = sceneFilter,
                        ids = null
                    )
                ).execute()

                if (response.hasErrors()) {
                    val err = response.errors?.firstOrNull()?.message ?: "GraphQL error"
                    throw Exception(err)
                }

                val newScenes = response.data?.findScenes?.scenes?.map { it.slimSceneData }.orEmpty()
                val totalCount = response.data?.findScenes?.count ?: 0

                val combined = _state.value.scenes + newScenes

                _state.value = _state.value.copy(
                    isLoading = false,
                    isFetchingNextPage = false,
                    currentPage = nextPage + 1,
                    canLoadMore = newScenes.isNotEmpty() && combined.size < totalCount,
                    totalCount = totalCount,
                    scenes = combined
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load scenes page")
                _state.value = _state.value.copy(
                    isLoading = false,
                    isFetchingNextPage = false,
                    error = e.localizedMessage ?: "Failed to load scenes"
                )
            }
        }
    }
}
