package com.example.pillremainder.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.data.repository.IntakeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ScreenMode {
    TODAY, LIBRARY
}

data class MedicationsUiState(
    val medications: List<MedicineCourse> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val mode: ScreenMode = ScreenMode.TODAY,
    val intakeStatuses: Map<String, String?> = emptyMap() // courseId-time -> status (null, "taken", "refused")
)

class MedicationsViewModel(
    private val repository: CourseRepository = CourseRepository(),
    private val mode: ScreenMode
) : ViewModel() {
    private val _uiState = MutableStateFlow(MedicationsUiState(mode = mode))
    val uiState: StateFlow<MedicationsUiState> = _uiState

    init {
        loadMedications()
    }

    fun loadMedications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getCourses()
            _uiState.update { currentState ->
                if (result.isSuccess) {
                    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE")).replaceFirstChar { it.uppercaseChar() }
                    val todayAbbreviation = when (today) {
                        "Mon" -> "Пн"
                        "Tue" -> "Вт"
                        "Wed" -> "Ср"
                        "Thu" -> "Чт"
                        "Fri" -> "Пт"
                        "Sat" -> "Сб"
                        "Sun" -> "Вс"
                        else -> ""
                    }
                    Log.d("MedicationsViewModel", "Сегодня: $today, Аббревиатура: $todayAbbreviation")

                    val medications = when (currentState.mode) {
                        ScreenMode.TODAY -> result.getOrNull()!!.filter {
                            val dayr = it.days
                            val included = it.days.isEmpty() || it.days.contains(todayAbbreviation) || it.days.contains(today)
                            Log.d("MedicationsViewModel", "Препарат: ${it.name}, courseId: ${it.courseId}, Дни: $dayr, Включён: $included")
                            included
                        }
                        ScreenMode.LIBRARY -> result.getOrNull()!!
                    }

                    // Загружаем статусы приёма для режима TODAY
                    val intakeStatuses = mutableMapOf<String, String?>()
                    if (currentState.mode == ScreenMode.TODAY) {
                        medications.forEach { medication ->
                            medication.intakeTime.forEach { time ->
                                val key = "${medication.courseId}-$time"
                                val statusResult = repository.checkIntakeStatus(medication.courseId, time)
                                val status = if (statusResult.isSuccess) statusResult.getOrNull() else null
                                intakeStatuses[key] = status
                                Log.d("MedicationsViewModel", "Статус для $key: $status")
                            }
                        }
                    }

                    Log.d("MedicationsViewModel", "Загружено препаратов: ${medications.size}, Статусы: $intakeStatuses")
                    currentState.copy(
                        medications = medications,
                        intakeStatuses = intakeStatuses,
                        isLoading = false,
                        errorMessage = null
                    )
                } else {
                    Log.e("MedicationsViewModel", "Ошибка загрузки курсов: ${result.exceptionOrNull()?.message}")
                    currentState.copy(
                        errorMessage = result.exceptionOrNull()?.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun markIntake(courseId: String, time: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = repository.markIntake(courseId, time)
            _uiState.update {
                val newStatuses = it.intakeStatuses + ("$courseId-$time" to "taken")
                Log.d("MedicationsViewModel", "Обновлён статус для $courseId-$time: ${newStatuses["$courseId-$time"]}")
                it.copy(
                    errorMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                    isLoading = false,
                    intakeStatuses = newStatuses
                )
            }
        }
    }

    fun markIntakeRefusal(courseId: String, time: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = repository.markIntakeRefusal(courseId, time)
            _uiState.update {
                val newStatuses = it.intakeStatuses + ("$courseId-$time" to "refused")
                Log.d("MedicationsViewModel", "Обновлён статус для $courseId-$time: ${newStatuses["$courseId-$time"]}")
                it.copy(
                    errorMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                    isLoading = false,
                    intakeStatuses = newStatuses
                )
            }
        }
    }
}