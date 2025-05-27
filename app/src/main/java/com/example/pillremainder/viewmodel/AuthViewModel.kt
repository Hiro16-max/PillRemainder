package com.example.pillremainder.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.repository.AuthRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val secondPassword: String = "",
    val name: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
)

class AuthViewModel : ViewModel() {
    private val auth = AuthRepository()

    var uiState by mutableStateOf(AuthUiState())
        private set

    fun updateEmail(email: String) {
        uiState = uiState.copy(email = email)
    }

    fun updatePassword(password: String) {
        uiState = uiState.copy(password = password)
    }

    fun updateSecondPassword(secondPassword: String) {
        uiState = uiState.copy(secondPassword = secondPassword)
    }

    fun updateName(name: String) {
        uiState = uiState.copy(name = name)
    }

    fun toggleAuthMode() {
        uiState = uiState.copy(
            isLoginMode = !uiState.isLoginMode,
            errorMessage = null,
            isAuthenticated = false
        )
    }

    fun authenticate() {
        if (uiState.email.isBlank() || uiState.password.isBlank() ||
            (!uiState.isLoginMode && ( uiState.secondPassword.isBlank()))
        ) {
            uiState = uiState.copy(errorMessage = "Пожалуйста, заполните все поля")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(uiState.email).matches()) {
            uiState = uiState.copy(errorMessage = "Некорректный email")
            return
        }

        if (!uiState.isLoginMode && uiState.password != uiState.secondPassword) {
            uiState = uiState.copy(errorMessage = "Пароли не совпадают")
            return
        }

        uiState = uiState.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                if (uiState.isLoginMode) {
                    auth.loginUser(uiState.email, uiState.password)
                } else {
                    auth.registerUser(uiState.email, uiState.password)
                }
                uiState = uiState.copy(isLoading = false, isAuthenticated = true)
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Auth failed: ${e.message}")
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Ошибка аутентификации"
                )
            }
        }
    }
}
