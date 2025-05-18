package com.example.pillremainder.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.pillremainder.data.model.MedicineCourse
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"
    private const val PREFS_NAME = "PillReminderPrefs"
    private const val ALARM_KEYS_PREFIX = "alarm_keys_"
    const val ALARM_ACTION = "com.example.pillremainder.ALARM"
    private const val MIN_ALARM_INTERVAL_MS = 60_000 // 1 минута
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    fun scheduleNotifications(context: Context, course: MedicineCourse) {
        if (!course.notificationsEnabled || course.intakeTime.isEmpty()) {
            Log.d(TAG, "scheduleNotifications: Уведомления отключены или нет времени для courseId: ${course.courseId}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "scheduleNotifications: Нет разрешения SCHEDULE_EXACT_ALARM для courseId: ${course.courseId}")
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ContextCompat.startActivity(context, intent, null)
                Toast.makeText(context, "Разрешите точные уведомления. На Xiaomi включите автозапуск и фоновый режим.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "scheduleNotifications: Ошибка запроса разрешения: ${e.message}", e)
                Toast.makeText(context, "Не удалось открыть настройки уведомлений", Toast.LENGTH_LONG).show()
            }
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmKeys = mutableSetOf<String>()
        val now = LocalDateTime.now()
        val today = now.dayOfWeek

        // Отменяем старые уведомления
        cancelNotifications(context, course)
        course.days.forEach { day ->
            Log.d(TAG, "scheduleNotifications: $day")
            Log.d(TAG, "scheduleNotifications: ${DayOfWeek.SUNDAY}")

        }
        course.intakeTime.forEach { time ->
            val (hour, minute) = time.split(":").map { it.toInt() }
            val localTime = LocalTime.parse(time, timeFormatter)


            DayOfWeek.values().forEach { day ->
                val localizedDay = dayOfWeekToLocalized[day]
                if (course.days.isEmpty() || (localizedDay != null && course.days.contains(localizedDay))) {
                    val key = "${course.courseId}:$time:${day.name}"
                    val requestCode = key.hashCode()
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        action = ALARM_ACTION
                        putExtra("courseId", course.courseId)
                        putExtra("courseName", course.name)
                        putExtra("time", time)
                        putExtra("day", day.name)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Вычисляем ближайшее время в будущем
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    // Устанавливаем день недели
                    val daysUntilTargetDay = (day.value - today.value + 7) % 7
                    calendar.add(Calendar.DAY_OF_YEAR, if (daysUntilTargetDay == 0 && (localTime.isBefore(now.toLocalTime()) || now.toLocalTime() == localTime)) 7 else daysUntilTargetDay)

                    var triggerTime = calendar.timeInMillis
                    val formattedTimeInitial = dateFormat.format(Date(triggerTime))

                    // Проверка минимального интервала
                    if (triggerTime <= System.currentTimeMillis() + MIN_ALARM_INTERVAL_MS) {
                        Log.d(TAG, "scheduleNotifications: Время слишком близкое для courseId: ${course.courseId}, time: $time, day: $day, triggerAt: $formattedTimeInitial")
                        calendar.add(Calendar.DAY_OF_YEAR, 7)
                        triggerTime = calendar.timeInMillis
                    }

                    val formattedTime = dateFormat.format(Date(triggerTime))

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                pendingIntent
                            )
                        } else {
                            alarmManager.setExact(
                                AlarmManager.RTC_WAKEUP,
                                triggerTime,
                                pendingIntent
                            )
                        }
                        alarmKeys.add(key)
                        Log.d(TAG, "scheduleNotifications: Будильник установлен для courseId: ${course.courseId}, время: $time, день: $day, triggerAt: $formattedTime (initial: $formattedTimeInitial)")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "scheduleNotifications: SecurityException для courseId: ${course.courseId}, error: ${e.message}", e)
                        Toast.makeText(context, "Не удалось установить уведомление: отсутствует разрешение", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        val success = prefs.edit().putStringSet(ALARM_KEYS_PREFIX + course.courseId, alarmKeys).commit()
        Log.d(TAG, "scheduleNotifications: Сохранение ключей для courseId: ${course.courseId}, успех: $success, ключи: ${alarmKeys.size}")
    }

    private val dayOfWeekToLocalized = mapOf(
        DayOfWeek.MONDAY to "Пн",
        DayOfWeek.TUESDAY to "Вт",
        DayOfWeek.WEDNESDAY to "Ср",
        DayOfWeek.THURSDAY to "Чт",
        DayOfWeek.FRIDAY to "Пт",
        DayOfWeek.SATURDAY to "Сб",
        DayOfWeek.SUNDAY to "Вс"
    )

    fun cancelNotifications(context: Context, course: MedicineCourse) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmKeys = prefs.getStringSet(ALARM_KEYS_PREFIX + course.courseId, emptySet())?.toMutableSet() ?: mutableSetOf()

        Log.d(TAG, "cancelNotifications: Отмена для courseId: ${course.courseId}, ключи: ${alarmKeys.size}")

        alarmKeys.forEach { key ->
            val parts = key.split(":")
            if (parts.size != 3) {
                Log.w(TAG, "cancelNotifications: Некорректный ключ: $key")
                return@forEach
            }
            val time = parts[1]
            val day = try {
                DayOfWeek.valueOf(parts[2])
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cancelNotifications: Некорректный день: ${parts[2]}")
                return@forEach
            }

            val requestCode = key.hashCode()
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ALARM_ACTION
                putExtra("courseId", course.courseId)
                putExtra("courseName", course.name)
                putExtra("time", time)
                putExtra("day", day.name)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "cancelNotifications: Отменён будильник для courseId: ${course.courseId}, время: $time, день: $day")
            }
        }

        course.intakeTime.forEach { time ->
            DayOfWeek.values().forEach { day ->
                if (course.days.isEmpty() || course.days.contains(day.name)) {
                    val key = "${course.courseId}:$time:${day.name}"
                    val requestCode = key.hashCode()
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        action = ALARM_ACTION
                        putExtra("courseId", course.courseId)
                        putExtra("courseName", course.name)
                        putExtra("time", time)
                        putExtra("day", day.name)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                    )

                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                        Log.d(TAG, "cancelNotifications: Дополнительно отменён будильник для courseId: ${course.courseId}, время: $time, день: $day")
                    }
                }
            }
        }

        prefs.edit().remove(ALARM_KEYS_PREFIX + course.courseId).commit()
        Log.d(TAG, "cancelNotifications: Ключи очищены для courseId: ${course.courseId}")
    }

    fun cancelNotificationsByCourseId(context: Context, courseId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmKeys = prefs.getStringSet(ALARM_KEYS_PREFIX + courseId, emptySet())?.toMutableSet() ?: mutableSetOf()

        Log.d(TAG, "cancelNotificationsByCourseId: Отмена для courseId: $courseId, ключи: ${alarmKeys.size}")

        alarmKeys.forEach { key ->
            val parts = key.split(":")
            if (parts.size != 3) {
                Log.w(TAG, "cancelNotificationsByCourseId: Некорректный ключ: $key")
                return@forEach
            }
            val time = parts[1]
            val day = try {
                DayOfWeek.valueOf(parts[2])
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "cancelNotificationsByCourseId: Некорректный день: ${parts[2]}")
                return@forEach
            }

            val requestCode = key.hashCode()
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ALARM_ACTION
                putExtra("courseId", courseId)
                putExtra("time", time)
                putExtra("day", day.name)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d(TAG, "cancelNotificationsByCourseId: Отменён будильник для courseId: $courseId, время: $time, день: $day")
            }
        }

        prefs.edit().remove(ALARM_KEYS_PREFIX + courseId).apply()
        Log.d(TAG, "cancelNotificationsByCourseId: Ключи очищены для courseId: $courseId")
    }
}