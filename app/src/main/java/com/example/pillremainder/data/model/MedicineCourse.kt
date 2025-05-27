package com.example.pillremainder.data.model

data class MedicineCourse(
    val courseId: String = "",
    val name: String = "",
    val days: List<String> = emptyList(), // Например, ["Пн", "Вт"] или пустой для ежедневного
    val intakeTime: List<String> = emptyList(), // Например, ["08:00", "20:00"]
    val dosePerIntake: Double = 0.0, // Количество таблеток за приём (поддерживает 0.25, 0.5 и т.д.)
    val availablePills: Double = 0.0, // Количество имеющихся таблеток
    val notificationsEnabled: Boolean = true // Уведомления включены по умолчанию
) {
    val timesPerDay: Int = intakeTime.count()
}