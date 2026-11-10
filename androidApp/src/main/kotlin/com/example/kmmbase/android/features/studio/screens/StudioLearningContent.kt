package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioLearningContent(store: StudioStore, onTeach: () -> Unit, onSkill: (String) -> Unit, onService: () -> Unit) {
    val state by store.state.collectAsState()
    Text("Bạn làm mẫu.\nSHADOW học cùng.", style = MaterialTheme.typography.headlineLarge)
    Text("Thử trọn luồng dạy và làm theo với bài mẫu. Không quay video hay phân tích AI trong chế độ này.")
    LookButton("Dạy một kỹ năng · demo", state.busy.isEmpty(), onTeach)
    TextButton(onClick = onService) { Text("Dùng dịch vụ AI thật") }
    Text("Kỹ năng đã lưu", style = MaterialTheme.typography.headlineSmall)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { store.dispatch(StudioAction.Retry) }) { Text("Tải lại") } }
    if (state.data.skills.isEmpty()) Text("Chưa có kỹ năng. Bắt đầu với một bài mẫu.")
    state.data.skills.forEach { skill ->
        Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(24.dp)).clickable(enabled = state.busy.isEmpty()) { onSkill(skill.id) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(skill.title, style = MaterialTheme.typography.titleLarge)
            Text("${skill.steps.size} bước · lưu trên máy", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
