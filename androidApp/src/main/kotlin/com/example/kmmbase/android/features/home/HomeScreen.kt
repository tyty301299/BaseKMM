package com.example.kmmbase.android.features.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeScreen(greeting: (String) -> String, onOpenNotes: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("KmmBase", style = MaterialTheme.typography.headlineLarge)
            Text("Android · Kotlin Multiplatform")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tên của bạn") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(greeting(name), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onOpenNotes) { Text("Ghi chú") }
        }
    }
}
