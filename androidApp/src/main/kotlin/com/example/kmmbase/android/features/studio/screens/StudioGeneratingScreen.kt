package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioGeneratingScreen(store: StudioStore, onBack: () -> Unit, onResults: () -> Unit, onRetry: () -> Unit) {
    StudioPage("Chuẩn bị bộ phối", store, onBack) { state ->
        Text(if (state.busy.isNotEmpty()) "Đang chuẩn bị…" else "Bộ phối mẫu đã sẵn sàng.", style = MaterialTheme.typography.headlineLarge)
        Text("Demo từ những món đang có trong tủ. Không phải kết quả AI.")
        if (state.suggested) LookButton("Xem bộ phối", onClick = onResults)
        else if (state.busy.isEmpty()) LookButton("Thử lại", onClick = onRetry)
    }
}
