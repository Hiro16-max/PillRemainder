package com.example.pillremainder.data.repository

import android.content.Context
import android.util.Log
import com.example.pillremainder.data.model.MedicineCourse
import com.example.pillremainder.notifications.NotificationScheduler
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.UUID

data class IntakeRecord(
    val courseId: String,
    val time: String,
    val date: String,
    val status: String?
)

class CourseRepository(
    private val context: Context? = null
) {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _cachedCourses = MutableStateFlow<List<MedicineCourse>>(emptyList())
    val cachedCourses: StateFlow<List<MedicineCourse>> = _cachedCourses.asStateFlow()

    private val _cachedIntakeStatuses = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val _cachedIntakeHistories =
        MutableStateFlow<Map<String, List<IntakeRecord>>>(emptyMap())
    private var isInitialized = false
    private var isListenerActive = false

    suspend fun initialize() {
        if (!isInitialized && auth.currentUser != null) {
            try {
                val userId = auth.currentUser!!.uid
                val snapshot =
                    database.getReference("Users").child(userId).child("courses").get().await()
                val courses =
                    snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                        .filter { it.courseId.isNotBlank() && it.name.isNotBlank() }
                _cachedCourses.value = courses.distinctBy { it.courseId }
                isInitialized = true
                setupRealtimeListener(userId)
                Log.d("CourseRepository", "Initialized with ${courses.size} courses")
            } catch (e: Exception) {
                Log.e("CourseRepository", "Initialization failed: ${e.message}", e)
            }
        }
    }

    private fun setupRealtimeListener(userId: String) {
        if (isListenerActive) {
            Log.d("CourseRepository", "Realtime listener already active, skipping setup")
            return
        }
        val userCoursesRef = database.getReference("Users").child(userId).child("courses")
        userCoursesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val courses =
                    snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                        .filter { it.courseId.isNotBlank() && it.name.isNotBlank() }
                val currentCourses = _cachedCourses.value
                val newCourses = courses.distinctBy { it.courseId }
                if (currentCourses != newCourses) {
                    _cachedCourses.value = newCourses
                    Log.d("CourseRepository", "Courses updated: ${newCourses.size} courses loaded")
                } else {
                    Log.d("CourseRepository", "No changes in courses, skipping cache update")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(
                    "CourseRepository",
                    "Realtime listener cancelled: ${error.message}",
                    error.toException()
                )
                isListenerActive = false
            }
        })
        isListenerActive = true
    }

    suspend fun getCourses(): Result<List<MedicineCourse>> {
        return try {
            if (_cachedCourses.value.isNotEmpty()) {
                Log.d("CourseRepository", "Returning cached courses: ${_cachedCourses.value.size}")
                Result.success(_cachedCourses.value)
            } else {
                val userId = auth.currentUser?.uid
                    ?: return Result.failure(Exception("User not authenticated"))
                val snapshot =
                    database.getReference("Users").child(userId).child("courses").get().await()
                val courses =
                    snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                        .filter { it.courseId.isNotBlank() && it.name.isNotBlank() }
                _cachedCourses.value = courses.distinctBy { it.courseId }
                setupRealtimeListener(userId)
                isInitialized = true
                Log.d("CourseRepository", "Fetched courses: ${courses.size}")
                Result.success(courses)
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch courses: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCourse(courseId: String): Result<MedicineCourse> {
        return try {
            val course = _cachedCourses.value.find { it.courseId == courseId }
            if (course != null) {
                Log.d("CourseRepository", "Returning cached course: $courseId")
                Result.success(course)
            } else {
                val userId = auth.currentUser?.uid
                    ?: return Result.failure(Exception("User not authenticated"))
                val userCoursesRef =
                    database.getReference("Users").child(userId).child("courses").child(courseId)
                val snapshot = userCoursesRef.get().await()
                val fetchedCourse = snapshot.getValue(MedicineCourse::class.java)
                if (fetchedCourse != null && fetchedCourse.courseId.isNotBlank() && fetchedCourse.name.isNotBlank()) {
                    Log.d("CourseRepository", "Fetched course: $courseId")
                    Result.success(fetchedCourse)
                } else {
                    Log.e("CourseRepository", "Course not found or invalid: $courseId")
                    Result.failure(Exception("Course not found"))
                }
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch course: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveCourse(course: MedicineCourse): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            Log.d("CourseRepository", "saveCourse: Сохранение курса: ${course.name}, courseId: ${course.courseId}")
            val courseRef = database.getReference("Users").child(userId).child("courses").child(course.courseId)

            // Проверяем, нет ли уже курса с таким courseId в кэше
            val existingCourse = _cachedCourses.value.find { it.courseId == course.courseId }
            if (existingCourse != null) {
                Log.w("CourseRepository", "saveCourse: Курс с courseId: ${course.courseId} уже существует в кэше")
                return Result.failure(Exception("Course already exists"))
            }

            // Отменяем уведомления перед сохранением
            context?.let { NotificationScheduler.cancelNotifications(it, course) }
            courseRef.setValue(course).await()

            // Обновляем кэш, избегая дублирования
            _cachedCourses.update { currentCourses ->
                val updatedCourses = currentCourses.filter { it.courseId != course.courseId } + course
                Log.d("CourseRepository", "saveCourse: Кэш обновлён, курсов: ${updatedCourses.size}")
                updatedCourses
            }

            // Планируем уведомления, если включены
            context?.let {
                if (course.notificationsEnabled) {
                    NotificationScheduler.scheduleNotifications(it, course)
                }
            }
            Log.d("CourseRepository", "Course saved: ${course.courseId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "saveCourse: Ошибка: ${course.courseId}, error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateCourse(course: MedicineCourse): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            Log.d("CourseRepository", "updateCourse: Обновление курса: ${course.name}, courseId: ${course.courseId}")
            val courseRef = database.getReference("Users").child(userId).child("courses").child(course.courseId)
            context?.let { NotificationScheduler.cancelNotifications(it, course) }
            courseRef.setValue(course).await()
            _cachedCourses.update { courses ->
                val updatedCourses = courses.map { if (it.courseId == course.courseId) course else it }
                Log.d("CourseRepository", "updateCourse: Кэш обновлён, курсов: ${updatedCourses.size}")
                updatedCourses
            }
            context?.let {
                if (course.notificationsEnabled) {
                    NotificationScheduler.scheduleNotifications(it, course)
                }
            }
            Log.d("CourseRepository", "Course updated: ${course.courseId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "updateCourse: Ошибка: ${course.courseId}, error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deleteCourse(courseId: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            Log.d("CourseRepository", "deleteCourse: Удаление курса: courseId: $courseId")
            val courseRef = database.getReference("Users").child(userId).child("courses").child(courseId)
            val courseSnapshot = courseRef.get().await()
            val course = courseSnapshot.getValue(MedicineCourse::class.java)
            courseRef.removeValue().await()
            database.getReference("Users").child(userId).child("intakes").child(courseId).removeValue().await()
            _cachedCourses.update { courses -> courses.filter { it.courseId != courseId } }
            _cachedIntakeStatuses.update { statuses -> statuses.filterKeys { !it.startsWith("$courseId-") } }
            _cachedIntakeHistories.update { histories -> histories.filterKeys { !it.startsWith("$courseId-") } }
            context?.let {
                if (course != null) {
                    NotificationScheduler.cancelNotifications(it, course)
                } else {
                    NotificationScheduler.cancelNotificationsByCourseId(it, courseId)
                }
            }
            Log.d("CourseRepository", "Course deleted: $courseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "deleteCourse: Ошибка удаления: $courseId, error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationsEnabled(courseId: String, enabled: Boolean): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            Log.d("CourseRepository", "updateNotificationsEnabled: courseId: $courseId, enabled: $enabled")
            val courseRef = database.getReference("Users").child(userId).child("courses").child(courseId)
            val snapshot = courseRef.get().await()
            val course = snapshot.getValue(MedicineCourse::class.java) ?: return Result.failure(Exception("Course not found"))
            courseRef.child("notificationsEnabled").setValue(enabled).await()
            _cachedCourses.update {
                it.map { if (it.courseId == courseId) it.copy(notificationsEnabled = enabled) else it }
            }
            context?.let {
                if (enabled) {
                    NotificationScheduler.scheduleNotifications(it, course)
                } else {
                    NotificationScheduler.cancelNotifications(it, course)
                }
            }
            Log.d("CourseRepository", "Notifications updated for courseId: $courseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "updateNotificationsEnabled: Ошибка: $courseId, error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun markIntake(courseId: String, time: String): Result<Unit> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef =
                database.getReference("Users").child(userId).child("intakes").child(courseId)
                    .child(time).child("history")
            intakeRef.child(date).setValue("taken").await()
            _cachedIntakeStatuses.update { it + ("$courseId-$time" to "taken") }
            Log.d(
                "CourseRepository",
                "Intake marked: courseId=$courseId, time=$time, date=$date, status=taken"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(
                "CourseRepository",
                "Failed to mark intake: courseId=$courseId, time=$time, error=${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun markIntakeRefusal(courseId: String, time: String): Result<Unit> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef =
                database.getReference("Users").child(userId).child("intakes").child(courseId)
                    .child(time).child("history")
            intakeRef.child(date).setValue("refused").await()
            _cachedIntakeStatuses.update { it + ("$courseId-$time" to "refused") }
            Log.d(
                "CourseRepository",
                "Refusal marked: courseId=$courseId, time=$time, date=$date, status=refused"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(
                "CourseRepository",
                "Failed to mark refusal: courseId=$courseId, time=$time, error=${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun checkIntakeStatus(courseId: String, time: String): Result<String?> {
        return try {
            val key = "$courseId-$time"
            val cachedStatus = _cachedIntakeStatuses.value[key]
            if (cachedStatus != null || _cachedIntakeStatuses.value.containsKey(key)) {
                Log.d(
                    "CourseRepository",
                    "Returning cached intake status: $key, status=$cachedStatus"
                )
                return Result.success(cachedStatus)
            }
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef =
                database.getReference("Users").child(userId).child("intakes").child(courseId)
                    .child(time).child("history")
            val snapshot = intakeRef.child(date).get().await()
            val status = snapshot.getValue(String::class.java)
            _cachedIntakeStatuses.update { it + (key to status) }
            Log.d(
                "CourseRepository",
                "Intake status checked: courseId=$courseId, time=$time, date=$date, status=$status"
            )
            Result.success(status)
        } catch (e: Exception) {
            Log.e(
                "CourseRepository",
                "Failed to check intake status: courseId=$courseId, time=$time, error=${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun getIntakeHistory(
        courseId: String,
        startDate: String,
        endDate: String
    ): Result<List<IntakeRecord>> {
        return try {
            val cacheKey = "$courseId-$startDate-$endDate"
            val cachedRecords = _cachedIntakeHistories.value[cacheKey]
            if (cachedRecords != null) {
                Log.d(
                    "CourseRepository",
                    "Returning cached history for courseId=$courseId: ${cachedRecords.size} records"
                )
                return Result.success(cachedRecords)
            }

            val records = mutableListOf<IntakeRecord>()
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val intakeRef =
                database.getReference("Users").child(userId).child("intakes").child(courseId)
            val snapshot = withTimeoutOrNull(3000) {
                try {
                    intakeRef.get().await()
                } catch (e: DatabaseException) {
                    Log.e(
                        "CourseRepository",
                        "Database error for courseId=$courseId: ${e.message}",
                        e
                    )
                    null
                }
            } ?: return Result.success(emptyList())

            Log.d(
                "CourseRepository",
                "Snapshot for courseId=$courseId: exists=${snapshot.exists()}, childrenCount=${snapshot.childrenCount}"
            )
            if (snapshot.exists()) {
                for (timeSnapshot in snapshot.children) {
                    val timeKey = timeSnapshot.key ?: continue
                    val time = timeKey.replace("_", ":")
                    val historySnapshot = timeSnapshot.child("history")
                    for (historyEntry in historySnapshot.children) {
                        val date = historyEntry.key ?: continue
                        if (date >= startDate && date <= endDate) {
                            val status = historyEntry.getValue(String::class.java)
                            records.add(
                                IntakeRecord(
                                    courseId = courseId,
                                    time = time,
                                    date = date,
                                    status = status
                                )
                            )
                            Log.d(
                                "CourseRepository",
                                "Added record: courseId=$courseId, time=$time, date=$date, status=$status"
                            )
                        }
                    }
                }
            } else {
                Log.w("CourseRepository", "No intake data found for courseId=$courseId")
            }

            _cachedIntakeHistories.update { it + (cacheKey to records) }
            Log.d(
                "CourseRepository",
                "Fetched intake history for courseId=$courseId: ${records.size} records"
            )
            Result.success(records)
        } catch (e: Exception) {
            Log.e(
                "CourseRepository",
                "Failed to fetch intake history for courseId=$courseId: ${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun getAllIntakeHistories(
        courseIds: List<String>,
        startDate: String,
        endDate: String
    ): Result<Map<String, List<IntakeRecord>>> {
        return try {
            val histories = mutableMapOf<String, List<IntakeRecord>>()
            withContext(Dispatchers.IO) {
                val chunkedCourseIds = courseIds.chunked(3)
                for (chunk in chunkedCourseIds) {
                    val deferred = chunk.map { courseId ->
                        async {
                            val result = getIntakeHistory(courseId, startDate, endDate)
                            courseId to (if (result.isSuccess) result.getOrNull()
                                ?: emptyList() else emptyList())
                        }
                    }
                    deferred.awaitAll().forEach { (courseId, records) ->
                        histories[courseId] = records
                    }
                }
            }
            Log.d(
                "CourseRepository",
                "Fetched histories for ${courseIds.size} courses: ${histories.mapValues { it.value.size }}"
            )
            Result.success(histories)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch all intake histories: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserName(): Result<String> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val profileRef =
                database.getReference("Users").child(userId).child("profile").child("name")
            val snapshot = profileRef.get().await()
            val name = snapshot.getValue(String::class.java)
            if (name != null) {
                Log.d("CourseRepository", "Fetched user name: $name")
                Result.success(name)
            } else {
                Log.e("CourseRepository", "Name not found for user: $userId")
                Result.failure(Exception("Name not found"))
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch user name: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserName(name: String): Result<Unit> {
        return try {
            val userId =
                auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val profileRef =
                database.getReference("Users").child(userId).child("profile").child("name")
            profileRef.setValue(name).await()
            Log.d("CourseRepository", "User name updated: $name")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to update user name: ${e.message}", e)
            Result.failure(e)
        }
    }
}