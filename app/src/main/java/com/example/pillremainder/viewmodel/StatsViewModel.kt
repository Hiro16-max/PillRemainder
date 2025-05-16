package com.example.pillremainder.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.data.repository.IntakeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class StatsUiState(
    val complianceRate: Double = 0.0,
    val pieChartData: PieChartData = PieChartData(),
    val courses: List<CourseItem> = emptyList(),
    val selectedCourseId: String? = null,
    val period: String = "week",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class CourseItem(
    val name: String,
    val courseId: String
)

data class PieChartData(
    val taken: Int = 0,
    val refused: Int = 0,
    val missed: Int = 0
) {
    val total: Int
        get() = taken + refused + missed
    val takenPercent: Float
        get() = if (total > 0) taken.toFloat() / total * 100f else 0f
    val refusedPercent: Float
        get() = if (total > 0) refused.toFloat() / total * 100f else 0f
    val missedPercent: Float
        get() = if (total > 0) missed.toFloat() / total * 100f else 0f
}

class StatsViewModel(
    private val repository: CourseRepository = CourseRepository()
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats(courseId: String? = null, period: String = "week") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val startTime = System.currentTimeMillis()
                val coursesResult = repository.getCourses()
                if (!coursesResult.isSuccess) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Ошибка загрузки курсов") }
                    return@launch
                }
                val courses = coursesResult.getOrNull()!!
                val courseItems = courses.map { CourseItem(it.name, it.courseId) }
                Log.d("StatsViewModel", "Loaded ${courses.size} courses: ${courseItems.map { it.name }}")

                val startDate = when (period) {
                    "week" -> LocalDate.now().minusDays(6)
                    "month" -> LocalDate.now().minusDays(29)
                    else -> LocalDate.now().minusDays(6)
                }
                val endDate = LocalDate.now()
                val dateRange = generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(endDate) }
                    .map { it.toString() }
                    .toList()

                val targetCourses = if (courseId == null) courses else courses.filter { it.courseId == courseId }
                if (targetCourses.isEmpty() && courseId != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Курс с ID $courseId не найден",
                            courses = courseItems,
                            selectedCourseId = courseId,
                            period = period
                        )
                    }
                    return@launch
                }
                Log.d("StatsViewModel", "Target courses: ${targetCourses.map { it.name }}")

                val historyResult = repository.getAllIntakeHistories(
                    courseIds = targetCourses.map { it.courseId },
                    startDate = startDate.toString(),
                    endDate = endDate.toString()
                )
                val intakeHistories = if (historyResult.isSuccess) {
                    historyResult.getOrNull() ?: emptyMap()
                } else {
                    Log.e("StatsViewModel", "Error fetching histories: ${historyResult.exceptionOrNull()?.message}")
                    emptyMap()
                }

                val historyByDate = mutableMapOf<String, MutableList<IntakeRecord>>()
                intakeHistories.forEach { (courseId, records) ->
                    records.forEach { record ->
                        historyByDate.getOrPut(record.date) { mutableListOf() }.add(record)
                    }
                }

                var totalTaken = 0
                var totalPlanned = 0
                var pieTaken = 0
                var pieRefused = 0
                var pieMissed = 0

                for (date in dateRange) {
                    val dateRecords = historyByDate[date] ?: emptyList()

                    for (course in targetCourses) {
                        val dayOfWeek = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE"))
                            .replaceFirstChar { it.uppercaseChar() }.let {
                                when (it) {
                                    "Mon" -> "Пн"
                                    "Tue" -> "Вт"
                                    "Wed" -> "Ср"
                                    "Thu" -> "Чт"
                                    "Fri" -> "Пт"
                                    "Sat" -> "Сб"
                                    "Sun" -> "Вс"
                                    else -> ""
                                }
                            }
                        val isScheduled = course.days.isEmpty() || course.days.contains(dayOfWeek)
                        if (!isScheduled) {
                            Log.d("StatsViewModel", "Course ${course.name} not scheduled on $date ($dayOfWeek)")
                            continue
                        }

                        val intakeTimes = course.intakeTime.takeIf { it.isNotEmpty() } ?: when (course.timesPerDay) {
                            1 -> listOf("08:00")
                            2 -> listOf("08:00", "20:00")
                            else -> emptyList()
                        }
                        Log.d("StatsViewModel", "Course ${course.name}: intakeTimes=$intakeTimes on $date")

                        for (time in intakeTimes) {
                            totalPlanned++
                            val record = dateRecords.find {
                                it.courseId == course.courseId && it.time == time
                            }
                            when (record?.status) {
                                "taken" -> {
                                    totalTaken++
                                    pieTaken++
                                }
                                "refused" -> pieRefused++
                                else -> pieMissed++
                            }
                        }
                    }
                }

                val complianceRate = if (totalPlanned > 0) (totalTaken.toDouble() / totalPlanned * 100) else 0.0
                val loadTime = System.currentTimeMillis() - startTime
                Log.d("StatsViewModel", "Stats loaded in ${loadTime}ms: complianceRate=$complianceRate, totalPlanned=$totalPlanned, pieTaken=$pieTaken, pieRefused=$pieRefused, pieMissed=$pieMissed")

                _uiState.update {
                    it.copy(
                        complianceRate = complianceRate,
                        pieChartData = PieChartData(pieTaken, pieRefused, pieMissed),
                        courses = courseItems,
                        selectedCourseId = courseId,
                        period = period,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("StatsViewModel", "Error loading stats: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun updateCourseFilter(courseId: String?) {
        loadStats(courseId, _uiState.value.period)
    }

    fun updatePeriod(period: String) {
        loadStats(_uiState.value.selectedCourseId, period)
    }
}