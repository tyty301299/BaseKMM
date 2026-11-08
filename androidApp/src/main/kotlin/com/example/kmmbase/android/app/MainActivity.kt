package com.example.kmmbase.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import com.example.kmmbase.android.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    private val container get() = (application as KmmBaseApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { AppNavHost(container) }
        }
    }
}
