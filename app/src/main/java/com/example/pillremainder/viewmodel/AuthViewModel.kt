package com.example.pillremainder.viewmodel

import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pillremainder.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await

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
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

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
            (!uiState.isLoginMode && (uiState.name.isBlank() || uiState.secondPassword.isBlank()))
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
                    auth.signInWithEmailAndPassword(uiState.email, uiState.password).await()
                } else {
                    val result = auth.createUserWithEmailAndPassword(uiState.email, uiState.password).await()
                    val userId = result.user?.uid
                    if (userId != null) {
                        val profileRef = database.getReference("Users").child(userId).child("profile")
                        profileRef.child("name").setValue(uiState.name).await()
                        Log.d("AuthViewModel", "Name saved for user: $userId, name: ${uiState.name}")
                    }
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
