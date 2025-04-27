package com.example.pillremainder.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Patterns

class LoginViewModel(private val repository: AuthRepository = AuthRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun updateEmail(email: TextFieldValue) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun updatePassword(password: TextFieldValue) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun login() {
        val state = _uiState.value
        val email = state.email.text.trim()
        val password = state.password.text.trim()

        if (email.isEmpty() || password.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Пожалуйста, заполните все поля")
            return
        }
        if (!isValidEmail(email)) {
            _uiState.value = state.copy(errorMessage = "Некорректный email")
            return
        }

        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            _uiState.value = state.copy(
                errorMessage = if (result.isSuccess) null else result.exceptionOrNull()?.message,
                isLoggedIn = result.isSuccess
            )
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

data class LoginUiState(
    val email: TextFieldValue = TextFieldValue(""),
    val password: TextFieldValue = TextFieldValue(""),
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false
)
