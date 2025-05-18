package com.example.pillremainder.ui.screens

import android.app.TimePickerDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pillremainder.viewmodel.CourseViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.pillremainder.data.repository.CourseRepository

val days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(
    onSaveSuccess: () -> Unit,
    onBack: () -> Unit,
    courseId: String? = null,
    repository: CourseRepository
) {
    val context = LocalContext.current
    val viewModel: CourseViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CourseViewModel(
                    repository = repository,
                    courseId = courseId ?: "",
                    context = context.applicationContext
                ) as T
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) } // Защита от повторных нажатий

    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) {
            Log.d("CourseScreen", "Курс сохранён или удалён, переход на onSaveSuccess")
            onSaveSuccess()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.isEditMode) "Редактировать курс" else "Создать курс",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            Log.d("CourseScreen", "Нажата кнопка Назад")
                            onBack()
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Назад",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (uiState.isEditMode) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить курс",
                                    tint = Color.Red
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(48.dp))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Секция: Основная информация
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Основная информация",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = uiState.courseName,
                            onValueChange = { viewModel.updateCourseName(it) },
                            label = { Text("Название курса") },
                            leadingIcon = {
                                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.errorMessage?.contains("название") == true
                        )
                        if (uiState.errorMessage?.contains("название") == true) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Секция: График
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "График приёма",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var expandedSchedule by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedSchedule,
                            onExpandedChange = { expandedSchedule = it }
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedSchedule,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("График") },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSchedule) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                isError = uiState.errorMessage?.contains("день") == true
                            )
                            ExposedDropdownMenu(
                                expanded = expandedSchedule,
                                onDismissRequest = { expandedSchedule = false }
                            ) {
                                listOf("Каждый день", "Свой график").forEach { schedule ->
                                    DropdownMenuItem(
                                        text = { Text(schedule) },
                                        onClick = {
                                            viewModel.updateSchedule(schedule)
                                            expandedSchedule = false
                                        }
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = uiState.selectedSchedule == "Свой график",
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(vertical = 1.dp)
                            ) {
                                items(days.size) { index ->
                                    val day = days[index]
                                    FilterChip(
                                        selected = uiState.selectedDays.contains(day),
                                        onClick = { viewModel.toggleDay(day) },
                                        label = {
                                            Text(
                                                text = day,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (uiState.selectedDays.contains(day)) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            borderColor = MaterialTheme.colorScheme.outline,
                                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                                            enabled = true,
                                            selected = true
                                        ),
                                        modifier = Modifier
                                            .width(48.dp)
                                            .height(32.dp)
                                    )
                                }
                            }
                        }
                        if (uiState.errorMessage?.contains("день") == true) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Секция: Частота и время
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Частота и время",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var expandedIntakeCount by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedIntakeCount,
                            onExpandedChange = { expandedIntakeCount = it }
                        ) {
                            OutlinedTextField(
                                value = "${uiState.selectedTimes.size} приём(а) в день",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Частота приёма") },
                                leadingIcon = {
                                    Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntakeCount) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                isError = uiState.errorMessage?.contains("время") == true
                            )
                            ExposedDropdownMenu(
                                expanded = expandedIntakeCount,
                                onDismissRequest = { expandedIntakeCount = false }
                            ) {
                                listOf(1, 2, 3).forEach { count ->
                                    DropdownMenuItem(
                                        text = { Text("$count приём(а) в день") },
                                        onClick = {
                                            viewModel.updateTimeCount(count)
                                            expandedIntakeCount = false
                                        }
                                    )
                                }
                            }
                        }
                        uiState.selectedTimes.forEachIndexed { index, time ->
                            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                            val initialTime = try {
                                LocalTime.parse(time, timeFormatter)
                            } catch (e: Exception) {
                                LocalTime.of(0, 0)
                            }
                            OutlinedTextField(
                                value = time,
                                onValueChange = { viewModel.updateTime(index, it) },
                                label = { Text("Время приёма ${index + 1}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Выбрать время",
                                        modifier = Modifier.clickable {
                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    val selectedTime = LocalTime.of(hourOfDay, minute)
                                                    viewModel.updateTime(index, selectedTime.format(timeFormatter))
                                                },
                                                initialTime.hour,
                                                initialTime.minute,
                                                true
                                            ).show()
                                        },
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                isError = uiState.errorMessage?.contains("время") == true
                            )
                        }
                        if (uiState.errorMessage?.contains("время") == true) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Секция: Дозировка и уведомления
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Дозировка и уведомления",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.dosePerIntake,
                                onValueChange = { viewModel.updateDosePerIntake(it) },
                                label = { Text("Дозировка (таблетки)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                leadingIcon = {
                                    Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                modifier = Modifier.weight(1f),
                                isError = uiState.errorMessage?.contains("дозировка") == true
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.decrementDosePerIntake() }) {
                                Icon(Icons.Default.Remove, contentDescription = "Уменьшить", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { viewModel.incrementDosePerIntake() }) {
                                Icon(Icons.Default.Add, contentDescription = "Увеличить", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        OutlinedTextField(
                            value = uiState.availablePills,
                            onValueChange = { viewModel.updateAvailablePills(it) },
                            label = { Text("Имеющихся таблеток") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isError = uiState.errorMessage?.contains("таблеток") == true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Уведомления",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.notificationsEnabled,
                                onCheckedChange = { viewModel.toggleNotifications() },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                        if (uiState.errorMessage?.contains("дозировка|таблеток") == true) {
                            Text(
                                text = uiState.errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Кнопка сохранения
                Button(
                    onClick = {
                        if (!isSaving) {
                            Log.d("CourseScreen", "Нажата кнопка Сохранить")
                            isSaving = true
                            viewModel.saveCourse()
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сохранить")
                    }
                }

                // Spacer для нижнего отступа
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Диалоговое окно подтверждения удаления
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Удалить курс") },
                    text = { Text("Вы уверены, что хотите удалить этот курс? Все связанные данные будут удалены.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteCourse()
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Удалить", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

