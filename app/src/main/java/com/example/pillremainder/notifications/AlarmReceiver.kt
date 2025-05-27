package com.example.pillremainder.notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.example.pillremainder.data.model.MedicineCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: Получен сигнал, action: ${intent.action}")
        if (intent.action != NotificationScheduler.ALARM_ACTION) {
            Log.w(TAG, "onReceive: Некорректное действие: ${intent.action}")
            return
        }

        val courseId = intent.getStringExtra("courseId") ?: run {
            Log.e(TAG, "onReceive: courseId отсутствует")
            return
        }
        val courseName = intent.getStringExtra("courseName") ?: run {
            Log.e(TAG, "onReceive: courseName отсутствует")
            return
        }
        val time = intent.getStringExtra("time") ?: run {
            Log.e(TAG, "onReceive: time отсутствует")
            return
        }
        val day = intent.getStringExtra("day")?.let {
            try {
                DayOfWeek.valueOf(it)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "onReceive: Некорректный день: $it")
                null
            }
        } ?: run {
            Log.e(TAG, "onReceive: day отсутствует")
            return
        }

        Log.d(TAG, "onReceive: Обработка для courseId: $courseId, courseName: $courseName, time: $time, day: $day")

        // Запускаем тяжелую логику в фоновом потоке
        CoroutineScope(Dispatchers.IO).launch {
            processNotification(context, courseId, courseName, time, day)
        }
    }

    private suspend fun processNotification(context: Context, courseId: String, courseName: String, time: String, day: DayOfWeek) {
        val db = FirebaseDatabase.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "processNotification: Пользователь не аутентифицирован")
            return
        }
        val courseRef = db.getReference("Users").child(userId).child("courses").child(courseId)
        try {
            val snapshot = courseRef.get().await()
            val course = snapshot.getValue(MedicineCourse::class.java)
            if (course == null || !course.notificationsEnabled) {
                Log.d(TAG, "processNotification: Уведомления отключены или курс не найден для courseId: $courseId")
                return
            }

            Log.d(TAG, "processNotification: Вызов NotificationWorker для courseId: $courseId")
            NotificationWorker.sendNotification(context, courseId, time, courseName)
            scheduleNextAlarm(context, courseId, courseName, time, day)
        } catch (e: Exception) {
            Log.e(TAG, "processNotification: Ошибка получения курса для courseId: $courseId, error: ${e.message}", e)
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNextAlarm(context: Context, courseId: String, courseName: String, time: String, day: DayOfWeek) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "scheduleNextAlarm: Нет разрешения SCHEDULE_EXACT_ALARM для courseId: $courseId")
            Toast.makeText(context, "Разрешите точные уведомления в настройках", Toast.LENGTH_LONG).show()
            return
        }

        val (hour, minute) = time.split(":").map { it.toInt() }
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_WEEK, day.toCalendarDay())
            add(Calendar.WEEK_OF_YEAR, 1)
        }

        val key = "$courseId:$time:${day.name}"
        val requestCode = key.hashCode()
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = NotificationScheduler.ALARM_ACTION
            putExtra("courseId", courseId)
            putExtra("courseName", courseName)
            putExtra("time", time)
            putExtra("day", day.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = calendar.timeInMillis
        val formattedTime = dateFormat.format(Date(triggerTime))
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "scheduleNextAlarm: Следующий будильник установлен для courseId: $courseId, time: $time, day: $day, triggerAt: $formattedTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "scheduleNextAlarm: SecurityException для courseId: $courseId, error: ${e.message}", e)
            Toast.makeText(context, "Не удалось установить уведомление: отсутствует разрешение", Toast.LENGTH_LONG).show()
        }
    }

    private fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }

    companion object {
        private const val TAG = "AlarmReceiver"
    }
}