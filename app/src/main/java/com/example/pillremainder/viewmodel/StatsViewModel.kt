package com.example.pillremainder.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class StatsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(Unit)
    val uiState: StateFlow<Unit> = _uiState
}