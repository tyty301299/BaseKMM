package com.example.kmmbase.android.features.looks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.domain.model.SavedLook
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookCaptureScreen(state: LookState, capture: SavedLook, send: (LookAction) -> Unit, onClose: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().background(LookBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Kết quả vừa chụp", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Đóng") }
        }
        LookMannequin(capture.preset, Modifier.fillMaxWidth().height(380.dp).background(LookStudio, RoundedCornerShape(28.dp)), capture.before, capture.flipped)
        Text(capture.title, style = MaterialTheme.typography.headlineLarge)
        Text("Kết quả mẫu · ${capture.occasion}")
        Text("Lưu lựa chọn trên máy để xem lại. Đây không phải ảnh chụp hoặc ảnh AI.")
        if (state.saving) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang lưu trên máy…") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.savedId == capture.id) {
            Text("Đã lưu trên máy", color = LookAccent)
            LookButton("Xem Lịch sử", onClick = onHistory)
        } else {
            LookButton(if (state.saving) "Đang lưu…" else "Lưu trên máy", !state.saving) { send(LookAction.SaveCapture) }
        }
        TextButton(onClick = { send(LookAction.Retake) }, enabled = !state.saving) { Text("Chụp lại") }
    }
}
