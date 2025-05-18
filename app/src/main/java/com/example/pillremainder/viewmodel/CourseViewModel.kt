package com.example.pillremainder.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CourseViewModel(
    private val repository: CourseRepository,
    private val courseId: String = "",
    private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(CourseUiState(isEditMode = courseId.isNotEmpty(), isLoading = courseId.isNotEmpty()))
    val uiState: StateFlow<CourseUiState> = _uiState

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        if (courseId.isNotEmpty()) {
            loadCourse(courseId)
        }
    }

    private fun loadCourse(courseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = repository.getCourse(courseId)
            _uiState.update { currentState ->
                if (result.isSuccess) {
                    val course = result.getOrNull()!!
                    currentState.copy(
                        courseName = TextFieldValue(course.name),
                        selectedSchedule = if (course.days.isEmpty()) "Каждый день" else "Свой график",
                        selectedDays = course.days,
                        dosePerIntake = course.dosePerIntake.toString(),
                        availablePills = course.availablePills.toString(),
                        notificationsEnabled = course.notificationsEnabled,
                        selectedTimes = course.intakeTime.ifEmpty { listOf("00:00") },
                        courseId = course.courseId,
                        isEditMode = true,
                        errorMessage = null,
                        isLoading = false
                    )
                } else {
                    currentState.copy(
                        errorMessage = result.exceptionOrNull()?.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateCourseName(name: TextFieldValue) {
        _uiState.update { it.copy(courseName = name) }
    }

    fun updateSchedule(schedule: String) {
        _uiState.update {
            it.copy(
                selectedSchedule = schedule,
                selectedDays = if (schedule != "Свой график") emptyList() else it.selectedDays
            )
        }
    }

    fun toggleDay(day: String) {
        _uiState.update {
            val currentDays = it.selectedDays
            it.copy(
                selectedDays = if (currentDays.contains(day)) currentDays - day else currentDays + day
            )
        }
    }

    fun updateDosePerIntake(dose: String) {
        _uiState.update { it.copy(dosePerIntake = dose) }
    }

    fun incrementDosePerIntake() {
        _uiState.update {
            val currentDose = it.dosePerIntake.toDoubleOrNull() ?: 0.0
            it.copy(dosePerIntake = (currentDose + 0.25).coerceAtLeast(0.0).toString())
        }
    }

    fun decrementDosePerIntake() {
        _uiState.update {
            val currentDose = it.dosePerIntake.toDoubleOrNull() ?: 0.0
            it.copy(dosePerIntake = (currentDose - 0.25).coerceAtLeast(0.0).toString())
        }
    }

    fun updateAvailablePills(pills: String) {
        _uiState.update { it.copy(availablePills = pills) }
    }

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
        viewModelScope.launch {
            repository.updateNotificationsEnabled(_uiState.value.courseId, _uiState.value.notificationsEnabled)
        }
    }

    fun updateTime(index: Int, time: String) {
        _uiState.update {
            val newTimes = it.selectedTimes.toMutableList().also { it[index] = time }
            it.copy(
                selectedTimes = newTimes.sortedBy { LocalTime.parse(it, timeFormatter) }
            )
        }
    }

    fun updateTimeCount(count: Int) {
        _uiState.update { currentState ->
            val currentTimes = currentState.selectedTimes
            val newTimes = if (count > currentTimes.size) {
                (currentTimes + List(count - currentTimes.size) { "00:00" }).sortedBy { LocalTime.parse(it, timeFormatter) }
            } else {
                currentTimes.take(count).sortedBy { LocalTime.parse(it, timeFormatter) }
            }
            currentState.copy(selectedTimes = newTimes)
        }
    }

    fun saveCourse() {
        _uiState.update { currentState ->
            if (currentState.isSaving) return@update currentState
            if (currentState.courseName.text.isBlank()) {
                return@update currentState.copy(errorMessage = "Введите название курса")
            }
            if (currentState.selectedSchedule == "Свой график" && currentState.selectedDays.isEmpty()) {
                return@update currentState.copy(errorMessage = "Выберите хотя бы один день")
            }
            if (currentState.selectedTimes.any { it == "00:00" }) {
                return@update currentState.copy(errorMessage = "Выберите корректное время приёма")
            }
            val dosePerIntake = currentState.dosePerIntake.toDoubleOrNull()
            if (dosePerIntake == null || dosePerIntake <= 0.0) {
                return@update currentState.copy(errorMessage = "Введите корректную дозировку (больше 0)")
            }
            val availablePills = currentState.availablePills.toIntOrNull()
            if (availablePills == null || availablePills < 0) {
                return@update currentState.copy(errorMessage = "Введите корректное количество таблеток")
            }

            viewModelScope.launch {
                val newCourseId = if (currentState.isEditMode) currentState.courseId else UUID.randomUUID().toString()
                val course = MedicineCourse(
                    name = currentState.courseName.text,
                    dosePerIntake = dosePerIntake,
                    intakeTime = currentState.selectedTimes,
                    days = if (currentState.selectedSchedule == "Свой график") currentState.selectedDays else emptyList(),
                    courseId = newCourseId,
                    availablePills = availablePills,
                    notificationsEnabled = currentState.notificationsEnabled
                )
                Log.d("CourseViewModel", "saveCourse: Сохранение курса: ${course.name}, courseId: ${course.courseId}")
                val result = if (currentState.isEditMode) {
                    repository.updateCourse(course)
                } else {
                    repository.saveCourse(course)
                }
                _uiState.update {
                    if (result.isSuccess) {
                        it.copy(errorMessage = null, isSaved = true, isSaving = false)
                    } else {
                        it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Ошибка сохранения", isSaving = false)
                    }
                }
            }
            currentState.copy(errorMessage = null, isSaving = true)
        }
    }

    fun deleteCourse() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repository.deleteCourse(_uiState.value.courseId)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSaved = true, isSaving = false)
                } else {
                    it.copy(errorMessage = result.exceptionOrNull()?.message ?: "Ошибка удаления", isSaving = false)
                }
            }
        }
    }
}

data class CourseUiState(
    val courseName: TextFieldValue = TextFieldValue(""),
    val selectedSchedule: String = "Каждый день",
    val selectedDays: List<String> = emptyList(),
    val dosePerIntake: String = "1.0",
    val availablePills: String = "0",
    val notificationsEnabled: Boolean = true,
    val selectedTimes: List<String> = listOf("00:00"),
    val courseId: String = "",
    val isEditMode: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
)