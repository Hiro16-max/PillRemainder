package com.example.pillremainder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pillremainder.ui.screens.RegisterScreen
import com.example.pillremainder.ui.theme.CreateCourseScreenTheme

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CreateCourseScreenTheme {
                RegisterScreen(
                    onRegisterSuccess = {
                        //startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}