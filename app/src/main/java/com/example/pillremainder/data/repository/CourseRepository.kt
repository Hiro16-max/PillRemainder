package com.example.pillremainder.data.repository

import android.util.Log
import com.example.pillremainder.data.model.MedicineCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

data class IntakeRecord(val date: String, val status: String) // "taken" или "refused"

class CourseRepository {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _cachedCourses = MutableStateFlow<List<MedicineCourse>>(emptyList())
    val cachedCourses: StateFlow<List<MedicineCourse>> = _cachedCourses.asStateFlow()

    private val _cachedIntakeStatuses = MutableStateFlow<Map<String, String?>>(emptyMap())

    private var isInitialized = false

    suspend fun initialize() {
        if (!isInitialized && auth.currentUser != null) {
            try {
                val userId = auth.currentUser!!.uid
                val snapshot = database.getReference("Users").child(userId).child("courses").get().await()
                val courses = snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                _cachedCourses.value = courses
                setupRealtimeListener(userId)
                isInitialized = true
                Log.d("CourseRepository", "Initialized with ${courses.size} courses")
            } catch (e: Exception) {
                Log.e("CourseRepository", "Initialization failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun setupRealtimeListener(userId: String) {
        val userCoursesRef = database.getReference("Users").child(userId).child("courses")
        userCoursesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val courses = snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                _cachedCourses.value = courses
                Log.d("CourseRepository", "Courses updated: ${courses.size} courses loaded")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("CourseRepository", "Realtime listener cancelled: ${error.message}")
                error.toException().printStackTrace()
            }
        })
    }

    suspend fun getCourses(): Result<List<MedicineCourse>> {
        return try {
            if (_cachedCourses.value.isNotEmpty()) {
                Log.d("CourseRepository", "Returning cached courses: ${_cachedCourses.value.size}")
                Result.success(_cachedCourses.value)
            } else {
                val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
                val snapshot = database.getReference("Users").child(userId).child("courses").get().await()
                val courses = snapshot.children.mapNotNull { it.getValue(MedicineCourse::class.java) }
                _cachedCourses.value = courses
                setupRealtimeListener(userId)
                isInitialized = true
                Log.d("CourseRepository", "Fetched courses: ${courses.size}")
                Result.success(courses)
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch courses: ${e.message}")
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
                val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
                val userCoursesRef = database.getReference("Users").child(userId).child("courses").child(courseId)
                val snapshot = userCoursesRef.get().await()
                val fetchedCourse = snapshot.getValue(MedicineCourse::class.java)
                if (fetchedCourse != null) {
                    Log.d("CourseRepository", "Fetched course: $courseId")
                    Result.success(fetchedCourse)
                } else {
                    Log.e("CourseRepository", "Course not found: $courseId")
                    Result.failure(Exception("Course not found"))
                }
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch course: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun saveCourse(course: MedicineCourse): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val courseId = UUID.randomUUID().toString()
            val courseWithId = course.copy(courseId = courseId)
            val userCoursesRef = database.getReference("Users").child(userId).child("courses").child(courseId)
            userCoursesRef.setValue(courseWithId).await()
            _cachedCourses.update { it + courseWithId }
            Log.d("CourseRepository", "Course saved: $courseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to save course: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateCourse(course: MedicineCourse): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val courseId = course.courseId.takeIf { it.isNotEmpty() } ?: return Result.failure(Exception("Invalid course ID"))
            val userCoursesRef = database.getReference("Users").child(userId).child("courses").child(courseId)
            userCoursesRef.setValue(course).await()
            _cachedCourses.update { courses ->
                courses.map { if (it.courseId == courseId) course else it }
            }
            Log.d("CourseRepository", "Course updated: $courseId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to update course: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markIntake(courseId: String, time: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef = database.getReference("Users").child(userId).child("intakes").child(courseId).child(time).child("history")
            intakeRef.child(date).setValue("taken").await()
            _cachedIntakeStatuses.update { it + ("$courseId-$time" to "taken") }
            Log.d("CourseRepository", "Intake marked: courseId=$courseId, time=$time, date=$date, status=taken")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to mark intake: courseId=$courseId, time=$time, error=${e.message}")
            Result.failure(e)
        }
    }

    suspend fun markIntakeRefusal(courseId: String, time: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef = database.getReference("Users").child(userId).child("intakes").child(courseId).child(time).child("history")
            intakeRef.child(date).setValue("refused").await()
            _cachedIntakeStatuses.update { it + ("$courseId-$time" to "refused") }
            Log.d("CourseRepository", "Refusal marked: courseId=$courseId, time=$time, date=$date, status=refused")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to mark refusal: courseId=$courseId, time=$time, error=${e.message}")
            Result.failure(e)
        }
    }

    suspend fun checkIntakeStatus(courseId: String, time: String): Result<String?> {
        return try {
            val key = "$courseId-$time"
            val cachedStatus = _cachedIntakeStatuses.value[key]
            if (cachedStatus != null || _cachedIntakeStatuses.value.containsKey(key)) {
                Log.d("CourseRepository", "Returning cached intake status: $key, status=$cachedStatus")
                return Result.success(cachedStatus)
            }
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val date = LocalDate.now().toString()
            val intakeRef = database.getReference("Users").child(userId).child("intakes").child(courseId).child(time).child("history")
            val snapshot = intakeRef.child(date).get().await()
            val status = snapshot.getValue(String::class.java)
            _cachedIntakeStatuses.update { it + (key to status) }
            Log.d("CourseRepository", "Intake status checked: courseId=$courseId, time=$time, date=$date, status=$status")
            Result.success(status) // "taken", "refused", или null
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to check intake status: courseId=$courseId, time=$time, error=${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getIntakeHistory(courseId: String, time: String): Result<List<IntakeRecord>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val intakeRef = database.getReference("Users").child(userId).child("intakes").child(courseId).child(time).child("history")
            val snapshot = intakeRef.get().await()
            val history = snapshot.children.mapNotNull { snap ->
                val date = snap.key ?: return@mapNotNull null
                val status = snap.getValue(String::class.java) ?: return@mapNotNull null
                IntakeRecord(date, status)
            }
            Log.d("CourseRepository", "Intake history fetched: courseId=$courseId, time=$time, records=${history.size}")
            Result.success(history)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to fetch intake history: courseId=$courseId, time=$time, error=${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUserName(): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val profileRef = database.getReference("Users").child(userId).child("profile").child("name")
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
            Log.e("CourseRepository", "Failed to fetch user name: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun updateUserName(name: String): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val profileRef = database.getReference("Users").child(userId).child("profile").child("name")
            profileRef.setValue(name).await()
            Log.d("CourseRepository", "User name updated: $name")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CourseRepository", "Failed to update user name: ${e.message}")
            Result.failure(e)
        }
    }
}