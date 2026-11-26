package com.github.damontecres.stashapp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.FindTagsQuery
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.SortDirectionEnum
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class TagManagerState(
    val isLoading: Boolean = false,
    val isFetchingNextPage: Boolean = false,
    val canLoadMore: Boolean = true,
    val currentPage: Int = 1,
    val totalCount: Int = 0,
    val activeSort: String = "name",
    val activeDirection: SortDirectionEnum = SortDirectionEnum.ASC,
    val searchQuery: String = "",
    val tags: List<FindTagsQuery.Tag> = emptyList(),
    val error: String? = null
)

class TagManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(TagManagerState())
    val state: StateFlow<TagManagerState> = _state.asStateFlow()

    fun refresh(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        query: String = _state.value.searchQuery,
        sort: String = _state.value.activeSort,
        direction: SortDirectionEnum = _state.value.activeDirection
    ) {
        _state.value = TagManagerState(
            isLoading = true,
            currentPage = 1,
            canLoadMore = true,
            totalCount = 0,
            activeSort = sort,
            activeDirection = direction,
            searchQuery = query,
            tags = emptyList()
        )
        loadNextPage(serverUrl, apiKey, username, password)
    }

    fun updateSearchQuery(
        query: String,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        refresh(serverUrl, apiKey, username, password, query = query)
    }

    fun updateSort(
        sort: String,
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        val newDirection = if (_state.value.activeSort == sort && _state.value.activeDirection == SortDirectionEnum.ASC) {
            SortDirectionEnum.DESC
        } else {
            SortDirectionEnum.ASC
        }
        refresh(serverUrl, apiKey, username, password, query = _state.value.searchQuery, sort = sort, direction = newDirection)
    }

    fun loadNextPage(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        if (_state.value.isFetchingNextPage || !_state.value.canLoadMore) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isFetchingNextPage = true)
                val nextPage = _state.value.currentPage
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                val findFilter = FindFilterType(
                    q = if (_state.value.searchQuery.isNotBlank()) Optional.present(_state.value.searchQuery) else Optional.absent(),
                    sort = Optional.present(_state.value.activeSort),
                    direction = Optional.present(_state.value.activeDirection),
                    page = Optional.present(nextPage),
                    per_page = Optional.present(30)
                )

                val response = apolloClient.query(
                    FindTagsQuery(
                        filter = findFilter,
                        tag_filter = null,
                        ids = null
                    )
                ).execute()

                val newTags = response.data?.findTags?.tags.orEmpty()
                val totalCount = response.data?.findTags?.count ?: 0

                val combined = _state.value.tags + newTags

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isFetchingNextPage = false,
                        currentPage = nextPage + 1,
                        canLoadMore = newTags.isNotEmpty() && combined.size < totalCount,
                        totalCount = totalCount,
                        tags = combined
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load tags page")
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isFetchingNextPage = false,
                        error = e.localizedMessage ?: "Failed to load tags"
                    )
                }
            }
        }
    }
}
