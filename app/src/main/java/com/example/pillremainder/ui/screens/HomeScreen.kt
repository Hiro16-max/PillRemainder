package com.example.pillremainder.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.pillremainder.viewmodel.MedicationsViewModel
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.data.repository.CourseRepository
import com.example.pillremainder.data.utils.formatDose
import com.example.pillremainder.data.utils.getTabletForm
import com.example.pillremainder.viewmodel.ScreenMode
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    viewModel: MedicationsViewModel = viewModel { MedicationsViewModel(mode = ScreenMode.TODAY) },
    onNavigateToCourse: (String?) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    courseRepository: CourseRepository = CourseRepository()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var selectedMedication by remember { mutableStateOf<MedicineCourse?>(null) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf("Загрузка...") }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isNameUpdating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Загружаем имя пользователя
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val result = courseRepository.getUserName()
            if (result.isSuccess) {
                userName = result.getOrNull()!!
                newName = userName // Инициализируем поле ввода
                Log.d("HomeScreen", "User name loaded: $userName")
            } else {
                userName = "Пользователь"
                newName = ""
                Log.e("HomeScreen", "Failed to load user name: ${result.exceptionOrNull()?.message}")
                Toast.makeText(context, "Ошибка загрузки имени", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (uiState.isLoading && uiState.medications.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFBB86FC)
            )
        } else {
            Column {
                // Шапка с именем, кнопкой редактирования и выходом
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = userName,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        IconButton(onClick = { showEditNameDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать имя",
                                tint = Color(0xFFBB86FC)
                            )
                        }
                    }
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("auth") {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Выход",
                            tint = Color.Red
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Календарь с текущей датой
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFBB86FC), shape = MaterialTheme.shapes.medium),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сегодня, ${LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM"))}",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Список лекарств с группировкой по времени
                LazyColumn {
                    val medicationTimePairs = uiState.medications.flatMap { medication ->
                        medication.intakeTime.map { time -> time to medication }
                    }

                    // Группируем по времени
                    val medicationsByTime = medicationTimePairs.groupBy { it.first }

                    // Сортируем ключи (времена) и создаем UI
                    medicationsByTime.keys.sorted().forEach { time ->
                        item {
                            Text(
                                text = time,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBB86FC),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        // Отображаем карточки лекарств для этого времени
                        items(medicationsByTime[time] ?: emptyList()) { (_, medication) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        selectedMedication = medication
                                        selectedTime = time
                                        showDialog = true
                                    },
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = medication.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Принять ${formatDose(medication.dosePerIntake)} ${getTabletForm(medication.dosePerIntake)}",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    // Визуальный индикатор статуса
                                    val status = uiState.intakeStatuses["${medication.courseId}-$time"]
                                    Log.d("HomeScreen", "Карточка: ${medication.name}, courseId: ${medication.courseId}, time: $time, status: $status")
                                    Icon(
                                        imageVector = when (status) {
                                            null -> Icons.Default.RadioButtonUnchecked
                                            "refused" -> Icons.Default.Close
                                            "taken" -> Icons.Default.CheckCircle
                                            else -> Icons.Default.CheckCircle // Для обратной совместимости
                                        },
                                        contentDescription = "Статус приёма",
                                        tint = when (status) {
                                            null -> Color.Gray
                                            "refused" -> Color.Red
                                            else -> Color(0xFF4CAF50) // Зелёный для принятого
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалоговое окно для принятия/отказа
    if (showDialog && selectedMedication != null && selectedTime != null) {
        val status = uiState.intakeStatuses["${selectedMedication!!.courseId}-$selectedTime"]
        Log.d("HomeScreen", "Диалог: ${selectedMedication!!.name}, courseId: ${selectedMedication!!.courseId}, time: $selectedTime, status: $status")
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Приём ${selectedMedication!!.name}") },
            text = {
                when (status) {
                    null -> Text("Вы хотите принять или отказаться от дозы в $selectedTime?")
                    "refused" -> Text("Вы отказались от приёма в $selectedTime")
                    "taken" -> Text("Лекарство уже принято в $selectedTime")
                    else -> Text("Лекарство уже принято в $selectedTime") // Для обратной совместимости
                }
            },
            confirmButton = {
                if (status == null || status == "refused") {
                    TextButton(onClick = {
                        viewModel.markIntake(selectedMedication!!.courseId, selectedTime!!)
                        showDialog = false
                    }) {
                        Text("Принять")
                    }
                } else {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Закрыть")
                    }
                }
            },
            dismissButton = {
                if (status == null) {
                    TextButton(onClick = {
                        viewModel.markIntakeRefusal(selectedMedication!!.courseId, selectedTime!!)
                        showDialog = false
                    }) {
                        Text("Отказаться")
                    }
                }
            }
        )
    }

    // Диалоговое окно для редактирования имени
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Редактировать имя") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Новое имя") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isNameUpdating
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            isNameUpdating = true
                            coroutineScope.launch {
                                val result = courseRepository.updateUserName(newName)
                                if (result.isSuccess) {
                                    userName = newName
                                    showEditNameDialog = false
                                    Toast.makeText(context, "Имя обновлено", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Ошибка обновления имени", Toast.LENGTH_SHORT).show()
                                    Log.e("HomeScreen", "Failed to update name: ${result.exceptionOrNull()?.message}")
                                }
                                isNameUpdating = false
                            }
                        } else {
                            Toast.makeText(context, "Введите имя", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isNameUpdating
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}