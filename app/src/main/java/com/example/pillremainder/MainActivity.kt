package com.example.pillremainder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.ui.screens.AppNavigation
import com.example.pillremainder.ui.theme.CreateCourseScreenTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val courseRepository = CourseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = FirebaseAuth.getInstance()
        val startDestination = if (auth.currentUser != null) "main/home" else "auth"

        setContent {
            CreateCourseScreenTheme {
                AppNavigation(startDestination = startDestination, repository = courseRepository)

            }
        }
    }
}