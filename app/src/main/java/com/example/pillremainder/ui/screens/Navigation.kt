package com.example.pillremainder.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.viewmodel.MedicationsViewModel
import com.example.pillremainder.viewmodel.ScreenMode
import com.example.pillremainder.viewmodel.StatsViewModel

@Composable
fun AppNavigation(
    startDestination: String,
    repository: CourseRepository,
    courseIdFromNotification: String? = null,
    timeFromNotification: String? = null
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("main/home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("main/{tab}") { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "home"
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Главная") },
                            label = { Text("Главная") },
                            selected = tab == "home",
                            onClick = { navController.navigate("main/home") },
                            enabled = true
                        )
                        NavigationBarItem(
                            icon = { Icon(imageVector = Icons.Default.Medication, contentDescription = "Медикаменты") },
                            label = { Text("Медикаменты") },
                            selected = tab == "medications",
                            onClick = { navController.navigate("main/medications") },
                            enabled = true
                        )
                        NavigationBarItem(
                            icon = { Icon(imageVector = Icons.Default.BarChart, contentDescription = "Статистика") },
                            label = { Text("Статистика") },
                            selected = tab == "stats",
                            onClick = { navController.navigate("main/stats") },
                            enabled = true
                        )
                    }
                }
            ) { padding ->
                when (tab) {
                    "home" -> HomeScreen(
                        viewModel = MedicationsViewModel(repository, ScreenMode.TODAY),
                        navController = navController,
                        onNavigateToCourse = { courseId ->
                            if (courseId == null) {
                                navController.navigate("course/new")
                            } else {
                                navController.navigate("course/$courseId")
                            }
                        },
                        modifier = Modifier.padding(padding),
                        courseRepository = repository,
                        courseIdFromNotification = courseIdFromNotification,
                        timeFromNotification = timeFromNotification
                    )
                    "medications" -> MedicationsScreen(
                        viewModel = MedicationsViewModel(repository, ScreenMode.LIBRARY),
                        navController = navController,
                        onNavigateToCourse = { courseId ->
                            if (courseId == null) {
                                navController.navigate("course/new")
                            } else {
                                navController.navigate("course/$courseId")
                            }
                        },
                        modifier = Modifier.padding(padding)
                    )
                    "stats" -> StatsScreen(
                        viewModel = StatsViewModel(),
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
        composable("course/new") {
            CourseScreen(
                courseId = null,
                repository = repository,
                onBack = { navController.popBackStack("main/medications", inclusive = false) },
                onSaveSuccess = {
                    navController.navigate("main/medications") {
                        popUpTo("course/new") { inclusive = true }
                    }
                }
            )
        }
        composable("course/{courseId}") { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: ""
            CourseScreen(
                courseId = courseId,
                repository = repository,
                onBack = { navController.popBackStack("main/medications", inclusive = false) },
                onSaveSuccess = {
                    navController.navigate("main/medications") {
                        popUpTo("course/$courseId") { inclusive = true }
                    }
                }
            )
        }
    }
}