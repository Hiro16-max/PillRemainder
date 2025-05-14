package com.example.pillremainder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pillremainder.viewmodel.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(),
    modifier: Modifier = Modifier // Добавляем параметр modifier
) {
    Column(
        modifier = modifier // Применяем modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Статистика",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFBB86FC)
        )
    }
}