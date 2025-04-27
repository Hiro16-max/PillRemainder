package com.example.pillremainder.data.model

data class MedicineCourse(
    val name: String = "",
    val days: List<String> = emptyList(),
    val timesPerDay: Int = 0,
    val pillCount: Int = 3,
    val dosage: Float = 0f,
    val intakeTime: List<String> = emptyList(),
    val courseId: String = "",
)