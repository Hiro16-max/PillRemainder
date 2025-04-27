package com.example.pillremainder.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Patterns

class RegisterViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun updateEmail(email: TextFieldValue) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun updatePassword(password: TextFieldValue) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun updateSecondPassword(secondPassword: TextFieldValue) {
        _uiState.value = _uiState.value.copy(secondPassword = secondPassword)
    }

    fun register() {
        val state = _uiState.value
        val email = state.email.text.trim()
        val password = state.password.text.trim()
        val secondPassword = state.secondPassword.text.trim()

        // Валидация
        if (email.isEmpty() || password.isEmpty() || secondPassword.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Пожалуйста, заполните все поля")
            return
        }
        if (!isValidEmail(email)) {
            _uiState.value = state.copy(errorMessage = "Некорректный email")
            return
        }
        if (password != secondPassword) {
            _uiState.value = state.copy(errorMessage = "Пароли не совпадают")
            return
        }

        viewModelScope.launch {
            val result = repository.registerUser(email, password)
            _uiState.value = state.copy(
                errorMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                isRegistered = result.isSuccess
            )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

data class RegisterUiState(
    val email: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
    val secondPassword: TextFieldValue = TextFieldValue(""),
    val errorMessage: String? = null,
    val isRegistered: Boolean = false
)