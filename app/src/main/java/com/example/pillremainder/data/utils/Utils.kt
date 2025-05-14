package com.example.pillremainder.data.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Форматирует дозировку для отображения (например, 0.5 -> "0,5", 1.0 -> "1")
internal fun formatDose(dose: Double): String {
    val symbols = DecimalFormatSymbols(Locale("ru")).apply {
        decimalSeparator = ','
    }
    return if (dose == dose.toInt().toDouble()) {
        // Если число целое (например, 1.0), отображаем без дробной части
        dose.toInt().toString()
    } else {
        // Для дробных чисел (например, 0.5) используем формат с одной цифрой после запятой
        DecimalFormat("0.##", symbols).format(dose)
    }
}

// Возвращает правильную форму слова "таблетка" в зависимости от числа
internal fun getTabletForm(dose: Double): String {
    val intDose = dose.toInt()
    return when {
        dose == 1.0 -> "таблетка"
        dose < 1.0 || dose > 20.0 -> {
            when {
                dose % 1.0 != 0.0 -> "таблетки" // Дробные, например, 0.5 -> "таблетки"
                intDose % 10 == 1 -> "таблетка"
                intDose % 10 in 2..4 -> "таблетки"
                else -> "таблеток"
            }
        }
        else -> "таблеток"
    }
}

internal fun getTimesForm(count: Int): String {
    return when {
        count == 1 -> "раз"
        count in 2..4 -> "раза"
        else -> "раз"
    }
}