package com.example.pillremainder.data.repository

import com.example.pillremainder.data.model.MedicineCourse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CourseRepository {
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun saveCourse(course: MedicineCourse): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("User not authenticated"))
            val courseId = UUID.randomUUID().toString()
            val courseWithId = course.copy(courseId = courseId)
            val userCoursesRef = database.getReference("Users").child(userId).child("courses").child(courseId)
            userCoursesRef.setValue(courseWithId).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}