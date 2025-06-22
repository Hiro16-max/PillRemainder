package com.example.pillremainder.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pillremainder.viewmodel.StatsViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val animatedTaken by animateFloatAsState(
        targetValue = uiState.pieChartData.takenPercent * 3.6f,
        animationSpec = tween(durationMillis = 1000),
        label = "takenPercentAnimation"
    )
    val animatedRefused by animateFloatAsState(
        targetValue = uiState.pieChartData.refusedPercent * 3.6f,
        animationSpec = tween(durationMillis = 1000),
        label = "refusedPercentAnimation"
    )
    val animatedMissed by animateFloatAsState(
        targetValue = uiState.pieChartData.missedPercent * 3.6f,
        animationSpec = tween(durationMillis = 1000),
        label = "missedPercentAnimation"
    )

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(56.dp) // стандартная высота топ-бара
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Статистика",
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Фильтры
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var expandedCourse by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedCourse,
                            onExpandedChange = { expandedCourse = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedCourseId?.let { courseId ->
                                    uiState.courses.find { it.courseId == courseId }?.name ?: "Все курсы"
                                } ?: "Все курсы",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Курс") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCourse)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedCourse,
                                onDismissRequest = { expandedCourse = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все курсы") },
                                    onClick = {
                                        viewModel.updateCourseFilter(null)
                                        expandedCourse = false
                                    }
                                )
                                uiState.courses.forEach { courseItem ->
                                    DropdownMenuItem(
                                        text = { Text(courseItem.name) },
                                        onClick = {
                                            viewModel.updateCourseFilter(courseItem.courseId)
                                            expandedCourse = false
                                        }
                                    )
                                }
                            }
                        }

                        var expandedPeriod by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedPeriod,
                            onExpandedChange = { expandedPeriod = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (uiState.period == "week") "Неделя" else "Месяц",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Период") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPeriod)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedPeriod,
                                onDismissRequest = { expandedPeriod = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Неделя") },
                                    onClick = {
                                        viewModel.updatePeriod("week")
                                        expandedPeriod = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Месяц") },
                                    onClick = {
                                        viewModel.updatePeriod("month")
                                        expandedPeriod = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Карточка с метриками и диаграммой
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ключевые метрики",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${DecimalFormat("0.#").format(uiState.complianceRate)}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Соблюдение",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Распределение приёмов",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (uiState.pieChartData.total == 0) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Нет данных о приёмах",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically() + fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .padding(8.dp)
                                    ) {
                                        val sweepAngles = listOf(
                                            animatedTaken,
                                            animatedRefused,
                                            animatedMissed
                                        )
                                        val colors = listOf(
                                            Color(0xFF4CAF50), // Принято — зелёный
                                            Color(0xFFF44336), // Отказано — красный
                                            Color(0xFFB0BEC5)  // Пропущено — серый
                                        )
                                        var startAngle = 0f
                                        sweepAngles.forEachIndexed { index, angle ->
                                            drawArc(
                                                color = colors[index],
                                                startAngle = startAngle,
                                                sweepAngle = angle,
                                                useCenter = true,
                                                topLeft = Offset.Zero,
                                                size = Size(size.width, size.height)
                                            )
                                            startAngle += angle
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        LegendItem(
                                            color = Color(0xFF4CAF50),
                                            label = "Принято",
                                            percent = uiState.pieChartData.takenPercent
                                        )
                                        LegendItem(
                                            color = Color(0xFFF44336),
                                            label = "Отказано",
                                            percent = uiState.pieChartData.refusedPercent
                                        )
                                        LegendItem(
                                            color = Color(0xFFB0BEC5),
                                            label = "Пропущено",
                                            percent = uiState.pieChartData.missedPercent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, percent: Float) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Text(
            text = "$label (${DecimalFormat("0.#").format(percent)}%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}