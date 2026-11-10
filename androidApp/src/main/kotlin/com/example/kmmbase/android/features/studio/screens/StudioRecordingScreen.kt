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
import com.example.kmmbase.android.features.studio.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioRecordingScreen(store: StudioStore, onBack: () -> Unit, onCompile: () -> Unit) {
    StudioPage("Quay thao tác · demo", store, onBack) { state ->
        StudioInstructionIllustration()
        Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (state.recording) "Đang mô phỏng thao tác" else "Đoạn thao tác mẫu đã sẵn sàng", style = MaterialTheme.typography.headlineSmall)
            Text("Không có video hoặc âm thanh được ghi.")
        }
        OutlinedTextField(state.transcript, { store.dispatch(StudioAction.Transcript(it)) }, label = { Text("Lời giải thích mẫu") }, enabled = state.busy.isEmpty(), minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("Nội dung này dùng duyệt flow; các bước tiếp theo là dữ liệu mẫu cố định.", style = MaterialTheme.typography.bodySmall)
        if (state.recording) LookButton("Dừng thao tác mẫu") { store.dispatch(StudioAction.StopRecording) }
        else {
            LookButton("Xem AI chia bước · demo", state.recorded, onCompile)
            TextButton(onClick = { store.dispatch(StudioAction.StartRecording) }, enabled = state.busy.isEmpty()) { Text("Làm mẫu lại") }
        }
    }
}
