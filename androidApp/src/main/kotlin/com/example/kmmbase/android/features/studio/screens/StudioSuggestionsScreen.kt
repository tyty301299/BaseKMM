package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSuggestionsScreen(store: StudioStore, onBack: () -> Unit, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    StudioPage("Kết quả phối đồ", store, onBack) { state ->
        Text("Quen gu. Mới cách mặc.", style = MaterialTheme.typography.headlineLarge)
        if (state.suggestions.isEmpty()) { Text("Chưa có bộ phối. Hãy thêm trang phục vào tủ."); LookButton("Thêm trang phục", onClick = onAdd) }
        state.suggestions.forEach { choice ->
            Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LookMannequin(choice.preset, Modifier.fillMaxWidth().height(240.dp))
                Text(choice.title, style = MaterialTheme.typography.headlineSmall)
                Text(choice.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LookButton("Xem bộ phối") { onSelect(choice.preset) }
            }
        }
    }
}
