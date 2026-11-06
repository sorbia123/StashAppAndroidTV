package com.github.damontecres.stashapp.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.FindScenesQuery
import com.github.damontecres.stashapp.mobile.api.type.CriterionModifier
import com.github.damontecres.stashapp.mobile.api.type.FindFilterType
import com.github.damontecres.stashapp.mobile.api.type.IntCriterionInput
import com.github.damontecres.stashapp.mobile.api.type.SceneFilterType
import com.github.damontecres.stashapp.mobile.net.StashMobileClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class OAnalyticsState(
    val isLoading: Boolean = false,
    val totalOCount: Int = 0,
    val monthlyCounts: Map<String, Int> = emptyMap(),
    val timelineEvents: List<Pair<LocalDate, Int>> = emptyList(),
    val gapEvents: List<Pair<String, Int>> = emptyList(),
    val longestGapDays: Long = 0L,
    val error: String? = null
)

class OCountDashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(OAnalyticsState(isLoading = true))
    val state: StateFlow<OAnalyticsState> = _state.asStateFlow()

    fun loadAnalytics(
        serverUrl: String,
        apiKey: String,
        username: String = "",
        password: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val apolloClient = StashMobileClient.createApolloClient(serverUrl, apiKey, username, password)

                // Filter scenes where o_counter > 0
                val sceneFilter = SceneFilterType(
                    o_counter = Optional.present(
                        IntCriterionInput(
                            modifier = CriterionModifier.GREATER_THAN,
                            value = 0
                        )
                    )
                )

                val findFilter = FindFilterType(
                    per_page = Optional.present(-1)
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

                val scenes = response.data?.findScenes?.scenes?.map { it.slimSceneData }.orEmpty()

                var totalO = 0
                val monthlyMap = mutableMapOf<String, Int>()
                val eventDates = mutableListOf<LocalDate>()

                val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

                for (scene in scenes) {
                    val history = scene.o_history
                    if (history.isNotEmpty()) {
                        for (timestampObj in history) {
                            val timestampStr = timestampObj.toString()
                            val eventDate = try {
                                ZonedDateTime.parse(timestampStr)
                                    .withZoneSameInstant(ZoneId.systemDefault())
                                    .toLocalDate()
                            } catch (e: Exception) {
                                try {
                                    LocalDate.parse(timestampStr.take(10), dateFormatter)
                                } catch (e2: Exception) {
                                    null
                                }
                            }

                            if (eventDate != null) {
                                eventDates.add(eventDate)
                                totalO += 1

                                val yearMonth = String.format(Locale.US, "%d-%02d", eventDate.year, eventDate.monthValue)
                                monthlyMap[yearMonth] = (monthlyMap[yearMonth] ?: 0) + 1
                            }
                        }
                    } else {
                        // Fallback to scene.date if o_history is unavailable
                        val count = scene.o_counter ?: 0
                        if (count > 0 && !scene.date.isNullOrBlank()) {
                            try {
                                val sceneDate = LocalDate.parse(scene.date.take(10), dateFormatter)
                                totalO += count
                                val yearMonth = String.format(Locale.US, "%d-%02d", sceneDate.year, sceneDate.monthValue)
                                monthlyMap[yearMonth] = (monthlyMap[yearMonth] ?: 0) + count
                                repeat(count) {
                                    eventDates.add(sceneDate)
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }

                // Sort event dates chronologically for gap calculation
                eventDates.sort()

                // Generate continuous calendar months from first event month to current month
                val continuousMonthlyMap = mutableMapOf<String, Int>()
                if (eventDates.isNotEmpty()) {
                    var currentYM = YearMonth.from(eventDates.first())
                    val endYM = YearMonth.from(LocalDate.now())
                    while (!currentYM.isAfter(endYM)) {
                        val key = String.format(Locale.US, "%d-%02d", currentYM.year, currentYM.monthValue)
                        continuousMonthlyMap[key] = monthlyMap[key] ?: 0
                        currentYM = currentYM.plusMonths(1)
                    }
                }

                val sortedMonthly = continuousMonthlyMap.entries.sortedByDescending { it.key }.associate { it.key to it.value }

                // Calculate gaps (in days) between consecutive O event dates
                val uniqueDates = eventDates.distinct()
                val gapList = mutableListOf<Pair<String, Int>>()
                var maxGap = 0L

                for (i in uniqueDates.indices) {
                    val gap = if (i == 0) 0L else ChronoUnit.DAYS.between(uniqueDates[i - 1], uniqueDates[i])
                    if (gap > maxGap) {
                        maxGap = gap
                    }
                    if (i > 0) {
                        gapList.add(uniqueDates[i].toString() to gap.toInt())
                    }
                }

                val sortedGapEvents = gapList.sortedByDescending { it.first }

                val timelineGrouped = eventDates.groupBy { it }.mapValues { entry -> entry.value.size }
                val timelineList = timelineGrouped.entries.sortedByDescending { it.key }.map { it.key to it.value }

                _state.value = OAnalyticsState(
                    isLoading = false,
                    totalOCount = totalO,
                    monthlyCounts = sortedMonthly,
                    timelineEvents = timelineList,
                    gapEvents = sortedGapEvents,
                    longestGapDays = maxGap
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load O analytics")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.localizedMessage ?: "Unknown error occurred"
                )
            }
        }
    }
}
