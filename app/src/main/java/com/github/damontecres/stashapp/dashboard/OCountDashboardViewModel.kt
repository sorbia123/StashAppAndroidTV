package com.github.damontecres.stashapp.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.api.Optional
import com.github.damontecres.stashapp.api.type.CriterionModifier
import com.github.damontecres.stashapp.api.type.FindFilterType
import com.github.damontecres.stashapp.api.type.IntCriterionInput
import com.github.damontecres.stashapp.api.type.SceneFilterType
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
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

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val server = StashServer.requireCurrentServer()
                val queryEngine = QueryEngine(server)

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

                val scenes = queryEngine.findScenes(
                    findFilter = findFilter,
                    sceneFilter = sceneFilter
                )

                var totalO = 0
                val monthlyMap = mutableMapOf<String, Int>()
                val eventDates = mutableListOf<LocalDate>()

                val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

                for (scene in scenes) {
                    val history = scene.o_history
                    if (!history.isNullOrEmpty()) {
                        for (timestampObj in history) {
                            if (timestampObj == null) continue
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

                                val yearMonth = "${eventDate.year}-${String.format("%02d", eventDate.monthValue)}"
                                monthlyMap[yearMonth] = (monthlyMap[yearMonth] ?: 0) + 1
                            }
                        }
                    } else {
                        // Fallback to scene.date if o_history is unavailable
                        val count = scene.o_counter ?: 0
                        if (count > 0 && !scene.date.isNullOrBlank()) {
                            try {
                                val sceneDate = LocalDate.parse(scene.date!!.take(10), dateFormatter)
                                totalO += count
                                val yearMonth = "${sceneDate.year}-${String.format("%02d", sceneDate.monthValue)}"
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
                        val key = "${currentYM.year}-${String.format("%02d", currentYM.monthValue)}"
                        continuousMonthlyMap[key] = monthlyMap[key] ?: 0
                        currentYM = currentYM.plusMonths(1)
                    }
                }

                // Sort descending so latest date/month is on the left and oldest on the right
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

                // Sort gap events chronologically (oldest on the left, newest on the right)
                val sortedGapEvents = gapList.sortedBy { it.first }

                // Build timeline events descending (latest on the left)
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
