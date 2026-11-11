package com.github.damontecres.stashapp.mobile.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.CreatePerformerMutation
import com.github.damontecres.stashapp.mobile.api.CreateTagMutation
import com.github.damontecres.stashapp.mobile.api.FindPerformersQuery
import com.github.damontecres.stashapp.mobile.api.FindTagsQuery
import com.github.damontecres.stashapp.mobile.api.SceneAddPlayCountMutation
import com.github.damontecres.stashapp.mobile.api.SceneSaveActivityMutation
import com.github.damontecres.stashapp.mobile.api.UpdateSceneMutation
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.PerformerCreateInput
import com.github.damontecres.stashapp.mobile.api.type.SceneUpdateInput
import com.github.damontecres.stashapp.mobile.api.type.TagCreateInput
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ScenePlayerState(
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
    val tagSuggestions: List<FindTagsQuery.Tag> = emptyList(),
    val performerSuggestions: List<FindPerformersQuery.Performer> = emptyList()
)

class ScenePlayerViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScenePlayerState())
    val state: StateFlow<ScenePlayerState> = _state.asStateFlow()

    fun recordSceneActivity(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        sceneId: String,
        positionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val resumeTimeSec = (positionMs / 1000.0).coerceAtLeast(0.0)
                val playDurationSec = if (durationMs > 0) durationMs / 1000.0 else null

                apolloClient.mutation(
                    SceneSaveActivityMutation(
                        scene_id = sceneId,
                        resume_time = resumeTimeSec,
                        play_duration = playDurationSec
                    )
                ).execute()
            } catch (e: Exception) {
                Timber.e(e, "Failed to record scene activity")
            }
        }
    }

    fun incrementPlayCount(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        sceneId: String,
        onSuccess: (Int) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val response = apolloClient.mutation(
                    SceneAddPlayCountMutation(
                        scene_id = sceneId,
                        times = null
                    )
                ).execute()

                val newCount = response.data?.sceneAddPlay?.count
                if (newCount != null) {
                    withContext(Dispatchers.Main) {
                        onSuccess(newCount)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to increment play count")
            }
        }
    }

    fun searchTags(serverUrl: String, apiKey: String, username: String, password: String, query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(tagSuggestions = emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val response = apolloClient.query(
                    FindTagsQuery(
                        filter = FindFilterType(q = Optional.present(query)),
                        tag_filter = null,
                        ids = null
                    )
                ).execute()
                val tags = response.data?.findTags?.tags.orEmpty()
                _state.value = _state.value.copy(tagSuggestions = tags)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search tags")
            }
        }
    }

    fun searchPerformers(serverUrl: String, apiKey: String, username: String, password: String, query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(performerSuggestions = emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)
                val response = apolloClient.query(
                    FindPerformersQuery(
                        filter = FindFilterType(q = Optional.present(query)),
                        performer_filter = null,
                        ids = null
                    )
                ).execute()
                val performers = response.data?.findPerformers?.performers.orEmpty()
                _state.value = _state.value.copy(performerSuggestions = performers)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search performers")
            }
        }
    }

    fun getOrCreateTag(
        serverUrl: String,
        apiKey: String,
        username: String,
        password: String,
        tagName: String,
        onResult: (String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmed = tagName.trim()
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                // First search to check if tag exists
                val searchResponse = apolloClient.query(
                    FindTagsQuery(
                        filter = FindFilterType(q = Optional.present(trimmed)),
                        tag_filter = null,
                        ids = null
                    )
                ).execute()

                val existingTags = searchResponse.data?.findTags?.tags.orEmpty()
                val match = existingTags.find { it.name.equals(trimmed, ignoreCase = true) }

                if (match != null) {
                    onResult(match.id, match.name)
                    return@launch
                }

                // Create new tag only when there is no existing tag of the entered name
                val mutationResponse = apolloClient.mutation(
                    CreateTagMutation(
                        input = TagCreateInput(name = trimmed)
                    )
                ).execute()

                if (mutationResponse.hasErrors()) {
                    val searchAgainResponse = apolloClient.query(
                        FindTagsQuery(
                            filter = FindFilterType(q = Optional.present(trimmed)),
                            tag_filter = null,
                            ids = null
                        )
                    ).execute()
                    val found = searchAgainResponse.data?.findTags?.tags.orEmpty()
                        .find { it.name.equals(trimmed, ignoreCase = true) }
                    if (found != null) {
                        onResult(found.id, found.name)
                        return@launch
                    }
                    val err = mutationResponse.errors?.firstOrNull()?.message ?: "Failed to create tag"
                    throw Exception(err)
                }

                val created = mutationResponse.data?.tagCreate
                if (created != null) {
                    onResult(created.id, created.name)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get or create tag")
                _state.value = _state.value.copy(saveError = e.localizedMessage ?: "Failed to create tag")
            }
        }
    }

    fun getOrCreatePerformer(
        serverUrl: String,
        apiKey: String,
        username: String,
        password: String,
        performerName: String,
        onResult: (String, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val trimmed = performerName.trim()
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                // First search to check if performer exists
                val searchResponse = apolloClient.query(
                    FindPerformersQuery(
                        filter = FindFilterType(q = Optional.present(trimmed)),
                        performer_filter = null,
                        ids = null
                    )
                ).execute()

                val existingPerformers = searchResponse.data?.findPerformers?.performers.orEmpty()
                val match = existingPerformers.find { it.name.equals(trimmed, ignoreCase = true) }

                if (match != null) {
                    onResult(match.id, match.name)
                    return@launch
                }

                // Create new performer when no existing performer matches
                val mutationResponse = apolloClient.mutation(
                    CreatePerformerMutation(
                        input = PerformerCreateInput(name = trimmed)
                    )
                ).execute()

                if (mutationResponse.hasErrors()) {
                    val searchAgainResponse = apolloClient.query(
                        FindPerformersQuery(
                            filter = FindFilterType(q = Optional.present(trimmed)),
                            performer_filter = null,
                            ids = null
                        )
                    ).execute()
                    val found = searchAgainResponse.data?.findPerformers?.performers.orEmpty()
                        .find { it.name.equals(trimmed, ignoreCase = true) }
                    if (found != null) {
                        onResult(found.id, found.name)
                        return@launch
                    }
                    val err = mutationResponse.errors?.firstOrNull()?.message ?: "Failed to create performer"
                    throw Exception(err)
                }

                val created = mutationResponse.data?.performerCreate
                if (created != null) {
                    onResult(created.id, created.name)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get or create performer")
                _state.value = _state.value.copy(saveError = e.localizedMessage ?: "Failed to create performer")
            }
        }
    }

    fun updateScene(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = "",
        sceneId: String,
        title: String?,
        details: String?,
        rating100: Int?,
        tagIds: List<String>?,
        performerIds: List<String>?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isSaving = true, saveError = null, saveSuccess = false)
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                val input = SceneUpdateInput(
                    id = sceneId,
                    title = Optional.presentIfNotNull(title),
                    details = Optional.presentIfNotNull(details),
                    rating100 = Optional.presentIfNotNull(rating100),
                    tag_ids = Optional.presentIfNotNull(tagIds),
                    performer_ids = Optional.presentIfNotNull(performerIds)
                )

                val response = apolloClient.mutation(UpdateSceneMutation(input)).execute()

                if (response.hasErrors()) {
                    val err = response.errors?.firstOrNull()?.message ?: "GraphQL error"
                    throw Exception(err)
                }

                _state.value = _state.value.copy(isSaving = false, saveSuccess = true)
                onSuccess()
            } catch (e: Exception) {
                Timber.e(e, "Failed to update scene")
                _state.value = _state.value.copy(isSaving = false, saveError = e.localizedMessage ?: "Failed to save scene")
            }
        }
    }
}
