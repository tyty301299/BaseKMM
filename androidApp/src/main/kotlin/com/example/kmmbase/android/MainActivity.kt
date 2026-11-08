package com.example.kmmbase.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.di.AppContainer

class MainActivity : ComponentActivity() {
    private val container = AppContainer()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                var name by rememberSaveable { mutableStateOf("") }
                Scaffold { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("KmmBase", style = MaterialTheme.typography.headlineLarge)
                        Text("Android · Kotlin Multiplatform")
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Tên của bạn") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(container.getGreeting.execute(name), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}
