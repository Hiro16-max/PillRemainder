package com.example.pillremainder

import android.os.Build
import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.ui.screens.AppNavigation
import com.example.pillremainder.ui.theme.CreateCourseScreenTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb


class MainActivity : ComponentActivity() {
    private val courseRepository by lazy { CourseRepository(applicationContext) }
    private var notificationCourseId: String? = null
    private var notificationTime: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.Black.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        // Запрос разрешения на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) {
                    Log.w("MainActivity", "Разрешение на уведомления не предоставлено")
                } else {
                    Log.d("MainActivity", "Разрешение на уведомления предоставлено")
                }
            }.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Запрос игнорирования оптимизации батареи
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "Запрос игнорирования оптимизации батареи")
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Log.d("MainActivity", "Оптимизация батареи уже отключена")
            }
        }

        // Обработка Intent из уведомления
        handleIntent(intent)

        val auth = FirebaseAuth.getInstance()
        val startDestination = if (auth.currentUser != null) "main/home" else "auth"

        setContent {
            CreateCourseScreenTheme {
                AppNavigation(
                    startDestination = startDestination,
                    repository = courseRepository,
                    courseIdFromNotification = notificationCourseId,
                    timeFromNotification = notificationTime
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent вызван с действием: ${intent.action}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_INTAKE_DIALOG") {
            notificationCourseId = intent.getStringExtra("courseId")
            notificationTime = intent.getStringExtra("time")
            Log.d("MainActivity", "Получен Intent с courseId: $notificationCourseId, time: $notificationTime")
            if (notificationCourseId == null || notificationTime == null) {
                Log.w("MainActivity", "courseId или time отсутствуют в Intent")
            }
            // Очищаем параметры в Intent, чтобы избежать повторной обработки
            intent.removeExtra("courseId")
            intent.removeExtra("time")
        }
    }
}