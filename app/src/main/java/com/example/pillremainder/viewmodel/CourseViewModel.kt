package com.example.pillremainder.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class CourseViewModel(
    private val repository: CourseRepository,
    courseId: String = "",
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
                        errorMessages = emptyMap(),
                        isLoading = false
                    )
                } else {
                    currentState.copy(
                        errorMessages = mapOf("general" to (result.exceptionOrNull()?.message ?: "Ошибка загрузки")),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateCourseName(name: TextFieldValue) {
        _uiState.update { currentState ->
            val errors = currentState.errorMessages.toMutableMap()
            if (name.text.isBlank()) {
                errors["courseName"] = "Введите название курса"
            } else {
                errors.remove("courseName")
            }
            currentState.copy(courseName = name, errorMessages = errors)
        }
    }

    fun updateSchedule(schedule: String) {
        _uiState.update { currentState ->
            val errors = currentState.errorMessages.toMutableMap()
            if (schedule == "Свой график" && currentState.selectedDays.isEmpty()) {
                errors["schedule"] = "Выберите хотя бы один день"
            } else {
                errors.remove("schedule")
            }
            currentState.copy(
                selectedSchedule = schedule,
                selectedDays = if (schedule != "Свой график") emptyList() else currentState.selectedDays,
                errorMessages = errors
            )
        }
    }

    fun toggleDay(day: String) {
        _uiState.update { currentState ->
            val currentDays = currentState.selectedDays
            val newDays = if (currentDays.contains(day)) currentDays - day else currentDays + day
            val errors = currentState.errorMessages.toMutableMap()
            if (currentState.selectedSchedule == "Свой график" && newDays.isEmpty()) {
                errors["schedule"] = "Выберите хотя бы один день"
            } else {
                errors.remove("schedule")
            }
            currentState.copy(
                selectedDays = newDays,
                errorMessages = errors
            )
        }
    }

    fun updateDosePerIntake(dose: String) {
        _uiState.update { currentState ->
            val errors = currentState.errorMessages.toMutableMap()
            val doseValue = dose.toDoubleOrNull()
            if (doseValue == null || doseValue <= 0.0) {
                errors["dose"] = "Введите корректную дозировку (больше 0)"
            } else {
                errors.remove("dose")
            }
            currentState.copy(dosePerIntake = dose, errorMessages = errors)
        }
    }

    fun incrementDosePerIntake() {
        _uiState.update {
            val currentDose = it.dosePerIntake.toDoubleOrNull() ?: 0.0
            val newDose = (currentDose + 0.25).coerceAtLeast(0.0)
            val errors = it.errorMessages.toMutableMap()
            if (newDose <= 0.0) {
                errors["dose"] = "Введите корректную дозировку (больше 0)"
            } else {
                errors.remove("dose")
            }
            it.copy(dosePerIntake = newDose.toString(), errorMessages = errors)
        }
    }

    fun decrementDosePerIntake() {
        _uiState.update {
            val currentDose = it.dosePerIntake.toDoubleOrNull() ?: 0.0
            val newDose = (currentDose - 0.25).coerceAtLeast(0.0)
            val errors = it.errorMessages.toMutableMap()
            if (newDose <= 0.0) {
                errors["dose"] = "Введите корректную дозировку (больше 0)"
            } else {
                errors.remove("dose")
            }
            it.copy(dosePerIntake = newDose.toString(), errorMessages = errors)
        }
    }

    fun updateAvailablePills(pills: String) {
        _uiState.update { currentState ->
            val errors = currentState.errorMessages.toMutableMap()
            val pillsValue = pills.toIntOrNull()
            if (pillsValue == null || pillsValue < 0) {
                errors["pills"] = "Введите корректное количество таблеток"
            } else {
                errors.remove("pills")
            }
            currentState.copy(availablePills = pills, errorMessages = errors)
        }
    }

    fun toggleNotifications() {
        _uiState.update { it.copy(notificationsEnabled = !it.notificationsEnabled) }
        viewModelScope.launch {
            repository.updateNotificationsEnabled(_uiState.value.courseId, _uiState.value.notificationsEnabled)
        }
    }

    fun updateTime(index: Int, time: String) {
        _uiState.update { currentState ->
            val newTimes = currentState.selectedTimes.toMutableList().also { it[index] = time }
            val errors = currentState.errorMessages.toMutableMap()
            // Проверяем, есть ли дубликаты времени
            if (newTimes.distinct().size != newTimes.size) {
                errors["times"] = "Все времена приёма должны быть разными"
            } else {
                errors.remove("times")
            }
            currentState.copy(
                selectedTimes = newTimes.sortedBy { LocalTime.parse(it, timeFormatter) },
                errorMessages = errors
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
            val errors = currentState.errorMessages.toMutableMap()
            // Проверяем, есть ли дубликаты времени
            if (newTimes.distinct().size != newTimes.size) {
                errors["times"] = "Все времена приёма должны быть разными"
            } else {
                errors.remove("times")
            }
            currentState.copy(selectedTimes = newTimes, errorMessages = errors)
        }
    }

    fun saveCourse() {
        _uiState.update { currentState ->
            // Очищаем предыдущие ошибки
            var newState = currentState.copy(errorMessages = emptyMap(), isSaving = true)

            // Проверяем поля и собираем ошибки
            val errors = mutableMapOf<String, String>()
            if (currentState.courseName.text.isBlank()) {
                errors["courseName"] = "Введите название курса"
            }
            if (currentState.selectedSchedule == "Свой график" && currentState.selectedDays.isEmpty()) {
                errors["schedule"] = "Выберите хотя бы один день"
            }
            // Проверяем, есть ли дубликаты времени
            if (currentState.selectedTimes.distinct().size != currentState.selectedTimes.size) {
                errors["times"] = "Все времена приёма должны быть разными"
            }
            val dosePerIntake = currentState.dosePerIntake.toDoubleOrNull()
            if (dosePerIntake == null || dosePerIntake <= 0.0) {
                errors["dose"] = "Введите корректную дозировку (больше 0)"
            }
            val availablePills = currentState.availablePills.toDoubleOrNull()
            if (availablePills == null || availablePills < 0) {
                errors["pills"] = "Введите корректное количество таблеток"
            }

            // Если есть ошибки, обновляем состояние с ошибками
            if (errors.isNotEmpty()) {
                return@update newState.copy(errorMessages = errors, isSaving = false)
            }

            // Если ошибок нет, сохраняем курс
            viewModelScope.launch {
                val newCourseId = if (currentState.isEditMode) currentState.courseId else UUID.randomUUID().toString()
                val course = MedicineCourse(
                    name = currentState.courseName.text,
                    dosePerIntake = dosePerIntake!!,
                    intakeTime = currentState.selectedTimes,
                    days = if (currentState.selectedSchedule == "Свой график") currentState.selectedDays else emptyList(),
                    courseId = newCourseId,
                    availablePills = availablePills!!,
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
                        it.copy(errorMessages = emptyMap(), isSaved = true, isSaving = false)
                    } else {
                        it.copy(
                            errorMessages = mapOf("general" to (result.exceptionOrNull()?.message ?: "Ошибка сохранения")),
                            isSaving = false
                        )
                    }
                }
            }
            newState.copy(errorMessages = errors, isSaving = true)
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
                    it.copy(errorMessages = mapOf("general" to (result.exceptionOrNull()?.message ?: "Ошибка удаления")), isSaving = false)
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
    val errorMessages: Map<String, String> = emptyMap(),
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
)