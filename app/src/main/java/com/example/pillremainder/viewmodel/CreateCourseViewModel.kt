package com.example.pillremainder.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CreateCourseViewModel(private val repository: CourseRepository = CourseRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateCourseUiState())
    val uiState: StateFlow<CreateCourseUiState> = _uiState

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun updateCourseName(name: TextFieldValue) {
        _uiState.value = _uiState.value.copy(courseName = name)
    }

    fun updateSchedule(schedule: String) {
        _uiState.value = _uiState.value.copy(
            selectedSchedule = schedule,
            selectedDays = if (schedule != "Свой график") emptyList() else _uiState.value.selectedDays
        )
    }

    fun toggleDay(day: String) {
        val currentDays = _uiState.value.selectedDays
        _uiState.value = _uiState.value.copy(
            selectedDays = if (currentDays.contains(day)) currentDays - day else currentDays + day
        )
    }

    fun updateIntakeSelection(selection: String) {
        val count = when (selection) {
            "Один раз в день" -> 1
            "Два раза в день" -> 2
            "Три раза в день" -> 3
            else -> 0
        }
        _uiState.value = _uiState.value.copy(
            intakeSelection = selection,
            selectedTimes = MutableList(count) { "00:00" }
        )
    }

    fun updateTime(index: Int, time: String) {
        val newTimes = _uiState.value.selectedTimes.toMutableList().also { it[index] = time }
        _uiState.value = _uiState.value.copy(
            selectedTimes = newTimes.sortedBy { LocalTime.parse(it, timeFormatter) }
        )
    }

    fun saveCourse() {
        val state = _uiState.value
        if (state.courseName.text.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Введите название курса")
            return
        }
        if (state.selectedSchedule == "Свой график" && state.selectedDays.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Выберите хотя бы один день")
            return
        }
        if (state.intakeSelection != "По необходимости" && state.selectedTimes.contains("00:00")) {
            _uiState.value = state.copy(errorMessage = "Выберите время приема")
            return
        }

        viewModelScope.launch {
            val course = MedicineCourse(
                name = state.courseName.text,
                timesPerDay = when (state.intakeSelection) {
                    "Один раз в день" -> 1
                    "Два раза в день" -> 2
                    "Три раза в день" -> 3
                    else -> 0
                },
                intakeTime = state.selectedTimes,
                days = if (state.selectedSchedule == "Свой график") state.selectedDays else emptyList()
            )
            val result = repository.saveCourse(course)
            _uiState.value = state.copy(
                errorMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                isSaved = result.isSuccess
            )
        }
    }
}

data class CreateCourseUiState(
    val courseName: TextFieldValue = TextFieldValue(""),
    val selectedSchedule: String = "Каждый день",
    val selectedDays: List<String> = emptyList(),
    val intakeSelection: String = "Один раз в день",
    val selectedTimes: List<String> = listOf("00:00"),
    val errorMessage: String? = null,
    val isSaved: Boolean = false
)