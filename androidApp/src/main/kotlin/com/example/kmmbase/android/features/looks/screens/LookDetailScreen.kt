package com.example.kmmbase.android.features.looks.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookDetailScreen(store: LookStore, id: String, onBack: () -> Unit, onRetry: () -> Unit) {
    val state by store.state.collectAsState()
    var confirm by rememberSaveable(id) { mutableStateOf(false) }
    BackHandler { if (!state.saving) onBack() }
    LookTheme {
        Column(Modifier.fillMaxSize().background(LookBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val record = state.records.find { it.id == id }
            if (record != null) {
                Text(record.title, style = MaterialTheme.typography.headlineLarge)
                LookMannequin(record.preset, Modifier.fillMaxWidth().height(360.dp).background(LookStudio, RoundedCornerShape(28.dp)), record.before, record.flipped)
                Text("${record.occasion} · hình nhân mẫu lưu trên máy")
                LookButton("Thử lại", !state.saving) { store.dispatch(LookAction.Reuse(id)); onRetry() }
                TextButton(onClick = { confirm = true }, enabled = !state.saving) { Text("Xóa khỏi Lịch sử", color = MaterialTheme.colorScheme.error) }
            } else if (state.loading) CircularProgressIndicator()
            else Text("Mục này không còn trong Lịch sử.")
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onBack, enabled = !state.saving) { Text("Đóng") }
        }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Xóa mục này?") },
            text = { Text("Không thể hoàn tác. Các mục khác được giữ nguyên.") },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(LookAction.Delete(id)) }) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy") } })
    }
}
