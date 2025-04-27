package com.example.pillremainder.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.pillremainder.viewmodel.CreateCourseViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCourseScreen(viewModel: CreateCourseViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val intakeOptions = listOf("Один раз в день", "Два раза в день", "Три раза в день", "По необходимости")
    val daysOfWeek = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val scheduleOptions = listOf("Каждый день", "Через день", "Свой график")
    var expanded by remember { mutableStateOf(false) }

    val showTimePicker = { index: Int ->
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
            val time = LocalTime.of(selectedHour, selectedMinute).format(timeFormatter)
            viewModel.updateTime(index, time)
        }, hour, minute, true).show()
    }

    LaunchedEffect(uiState.errorMessage, uiState.isSaved) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
        if (uiState.isSaved) {
            Toast.makeText(context, "Курс '${uiState.courseName.text}' сохранён!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Создание курса",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color(0xFFBB86FC),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.courseName,
            onValueChange = { viewModel.updateCourseName(it) },
            label = { Text("Название курса") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = uiState.selectedSchedule,
                onValueChange = {},
                readOnly = true,
                label = { Text("Выберите график приема") },
                modifier = Modifier.menuAnchor().fillMaxWidth().clickable { expanded = true }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                scheduleOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            viewModel.updateSchedule(option)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (uiState.selectedSchedule == "Свой график") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Выберите дни приема:", fontWeight = FontWeight.Bold, color = Color.Black)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                daysOfWeek.forEach { day ->
                    FilterChip(
                        selected = uiState.selectedDays.contains(day),
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(day, color = Color.Black) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Количество приемов в день:", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFBB86FC))
        intakeOptions.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = uiState.intakeSelection == option,
                    onClick = { viewModel.updateIntakeSelection(option) }
                )
                Text(option, fontSize = 16.sp)
            }
        }

        if (uiState.intakeSelection != "По необходимости") {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Выберите время приема", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFBB86FC))
            Spacer(modifier = Modifier.height(16.dp))
            uiState.selectedTimes.forEachIndexed { index, time ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${index + 1} прием", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text(
                        text = time,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { showTimePicker(index) },
                        textAlign = TextAlign.End,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { viewModel.saveCourse() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("Сохранить курс")
        }
    }
}