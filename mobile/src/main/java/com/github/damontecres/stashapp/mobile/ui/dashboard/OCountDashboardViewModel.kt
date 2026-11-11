package com.github.damontecres.stashapp.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.mobile.api.FindScenesQuery
import com.github.damontecres.stashapp.mobile.api.fragment.SlimSceneData
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

data class OEventGapItem(
    val index: Int,
    val dateLabel: String,
    val date: LocalDate,
    val gapDays: Int,
    val scene: SlimSceneData? = null,
    val isToday: Boolean = false
)

data class OAnalyticsState(
    val isLoading: Boolean = false,
    val totalOCount: Int = 0,
    val monthlyCounts: Map<String, Int> = emptyMap(),
    val yearlyMonthlyCounts: Map<Int, Map<Int, Int>> = emptyMap(),
    val timelineEvents: List<Pair<LocalDate, Int>> = emptyList(),
    val gapEvents: List<OEventGapItem> = emptyList(),
    val longestGapDays: Long = 0L,
    val error: String? = null
)

private data class InternalOEvent(
    val date: LocalDate,
    val scene: SlimSceneData
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
                val yearlyMonthlyMap = mutableMapOf<Int, MutableMap<Int, Int>>()
                val internalEvents = mutableListOf<InternalOEvent>()

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
                                internalEvents.add(InternalOEvent(eventDate, scene))
                                totalO += 1

                                val yearMonth = String.format(Locale.US, "%d-%02d", eventDate.year, eventDate.monthValue)
                                monthlyMap[yearMonth] = (monthlyMap[yearMonth] ?: 0) + 1

                                val yearSubMap = yearlyMonthlyMap.getOrPut(eventDate.year) { mutableMapOf() }
                                yearSubMap[eventDate.monthValue] = (yearSubMap[eventDate.monthValue] ?: 0) + 1
                            }
                        }
                    } else {
                        val count = scene.o_counter ?: 0
                        if (count > 0 && !scene.date.isNullOrBlank()) {
                            try {
                                val sceneDate = LocalDate.parse(scene.date.take(10), dateFormatter)
                                totalO += count
                                val yearMonth = String.format(Locale.US, "%d-%02d", sceneDate.year, sceneDate.monthValue)
                                monthlyMap[yearMonth] = (monthlyMap[yearMonth] ?: 0) + count

                                val yearSubMap = yearlyMonthlyMap.getOrPut(sceneDate.year) { mutableMapOf() }
                                yearSubMap[sceneDate.monthValue] = (yearSubMap[sceneDate.monthValue] ?: 0) + count

                                repeat(count) {
                                    internalEvents.add(InternalOEvent(sceneDate, scene))
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        }
                    }
                }

                // Sort chronologically
                internalEvents.sortBy { it.date }

                // Continuous monthly map
                val continuousMonthlyMap = mutableMapOf<String, Int>()
                if (internalEvents.isNotEmpty()) {
                    var currentYM = YearMonth.from(internalEvents.first().date)
                    val endYM = YearMonth.from(LocalDate.now())
                    while (!currentYM.isAfter(endYM)) {
                        val key = String.format(Locale.US, "%d-%02d", currentYM.year, currentYM.monthValue)
                        continuousMonthlyMap[key] = monthlyMap[key] ?: 0
                        currentYM = currentYM.plusMonths(1)
                    }
                }

                val sortedMonthly = continuousMonthlyMap.entries.sortedBy { it.key }.associate { it.key to it.value }

                // Calculate gap items starting with first event (0 days gap) and ending with Today (last event + 1 count)
                val gapItems = mutableListOf<OEventGapItem>()
                var maxGap = 0L

                val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

                if (internalEvents.isNotEmpty()) {
                    // Point 1: First event with 0 days gap
                    val firstEvent = internalEvents.first()
                    gapItems.add(
                        OEventGapItem(
                            index = 1,
                            dateLabel = firstEvent.date.format(displayFormatter),
                            date = firstEvent.date,
                            gapDays = 0,
                            scene = firstEvent.scene
                        )
                    )

                    // Subsequent events and their gaps from previous event
                    for (i in 1 until internalEvents.size) {
                        val prev = internalEvents[i - 1]
                        val curr = internalEvents[i]
                        val gap = ChronoUnit.DAYS.between(prev.date, curr.date)
                        if (gap > maxGap) maxGap = gap
                        gapItems.add(
                            OEventGapItem(
                                index = i + 1,
                                dateLabel = curr.date.format(displayFormatter),
                                date = curr.date,
                                gapDays = gap.toInt(),
                                scene = curr.scene
                            )
                        )
                    }

                    // Point Today: Last event + 1 count, days since last event till today
                    val lastEvent = internalEvents.last()
                    val todayGap = ChronoUnit.DAYS.between(lastEvent.date, LocalDate.now())
                    if (todayGap > maxGap) maxGap = todayGap
                    gapItems.add(
                        OEventGapItem(
                            index = internalEvents.size + 1,
                            dateLabel = "Today (${LocalDate.now().format(displayFormatter)})",
                            date = LocalDate.now(),
                            gapDays = todayGap.toInt(),
                            scene = lastEvent.scene,
                            isToday = true
                        )
                    )
                }

                val timelineGrouped = internalEvents.groupBy { it.date }.mapValues { entry -> entry.value.size }
                val timelineList = timelineGrouped.entries.sortedByDescending { it.key }.map { it.key to it.value }

                _state.value = OAnalyticsState(
                    isLoading = false,
                    totalOCount = totalO,
                    monthlyCounts = sortedMonthly,
                    yearlyMonthlyCounts = yearlyMonthlyMap,
                    timelineEvents = timelineList,
                    gapEvents = gapItems,
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
