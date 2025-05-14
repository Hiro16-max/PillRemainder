package com.example.pillremainder.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.utils.formatDose
import com.example.pillremainder.data.utils.getTabletForm
import com.example.pillremainder.data.utils.getTimesForm
import com.example.pillremainder.viewmodel.MedicationsViewModel
import com.example.pillremainder.viewmodel.ScreenMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Composable
fun MedicationsScreen(
    viewModel: MedicationsViewModel,
    navController: NavController,
    onNavigateToCourse: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Заголовок
        Text(
            text = "Библиотека курсов",
            fontSize = 24.sp,
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Список курсов
        Box(
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.isLoading && uiState.medications.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFBB86FC)
                )
            } else {
                LazyColumn {
                    items(uiState.medications) { medication ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onNavigateToCourse(medication.courseId) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = medication.name,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                                Text(
                                    text = "${
                                        if (medication.days.isEmpty()) "Каждый день"
                                        else medication.days.joinToString(", ")
                                    } ${medication.intakeTime.size} ${getTimesForm(medication.intakeTime.size)} в день",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Имеющихся таблеток: ${medication.availablePills}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onNavigateToCourse(null) }, // Навигация для создания нового курса
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Добавить курс", color = Color.White, fontSize = 16.sp)
        }
    }
}