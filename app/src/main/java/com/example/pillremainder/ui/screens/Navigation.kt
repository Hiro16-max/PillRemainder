package com.example.pillremainder.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.pillremainder.viewmodel.CreateCourseViewModel
import com.example.pillremainder.viewmodel.LoginViewModel
import com.example.pillremainder.viewmodel.RegisterViewModel

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("register") {
            RegisterScreen(
                viewModel = RegisterViewModel(),
                onRegisterSuccess = { navController.navigate("login") { popUpTo("register") { inclusive = true } } }
            )
        }
        composable("login") {
            LoginScreen(
                viewModel = LoginViewModel(),
                onLoginSuccess = { navController.navigate("createCourse") { popUpTo("login") { inclusive = true } } },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }
        composable("createCourse") {
            CreateCourseScreen(viewModel = CreateCourseViewModel())
        }
    }
}