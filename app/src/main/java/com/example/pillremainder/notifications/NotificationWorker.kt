package com.example.pillremainder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.pillremainder.MainActivity
import android.Manifest

object NotificationWorker {
    private const val TAG = "NotificationWorker"
    private const val CHANNEL_ID = "pill_reminder_channel"
    private const val CHANNEL_NAME = "Pill Reminder Notifications"

    fun sendNotification(context: Context, courseId: String, time: String, courseName: String) {
        Log.d(TAG, "sendNotification: Начало отправки уведомления для courseId: $courseId, time: $time, courseName: $courseName")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Проверка разрешения на уведомления
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "sendNotification: Разрешение POST_NOTIFICATIONS не предоставлено")
                return
            }
            Log.d(TAG, "sendNotification: Разрешение POST_NOTIFICATIONS предоставлено")
        }

        // Создание канала уведомлений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for pill intake reminders"
                    enableVibration(true)
                    setShowBadge(true)
                }
                Log.d(TAG, "sendNotification: Создан новый канал уведомлений: $CHANNEL_ID")
                notificationManager.createNotificationChannel(channel)
            } else {
                Log.d(TAG, "sendNotification: Канал уведомлений уже существует: $CHANNEL_ID")
            }
        }

        // Intent для перехода на MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "OPEN_INTAKE_DIALOG"
            putExtra("courseId", courseId)
            putExtra("time", time)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = try {
            PendingIntent.getActivity(
                context,
                courseId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendNotification: Ошибка создания PendingIntent для courseId: $courseId, error: ${e.message}", e)
            return
        }

        // Создание уведомления
        val notification = try {
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Пора принять $courseName")
                .setContentText("Запланированный приём в $time")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 500, 500, 500))
                .setDefaults(NotificationCompat.DEFAULT_SOUND)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "sendNotification: Ошибка создания уведомления для courseId: $courseId, error: ${e.message}", e)
            return
        }

        // Отправка уведомления
        try {
            val notificationId = (courseId + time).hashCode() // Более надёжный ID
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "sendNotification: Уведомление отправлено для courseId: $courseId, time: $time, notificationId: $notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "sendNotification: Ошибка отправки уведомления для courseId: $courseId, error: ${e.message}", e)
        }
    }
}