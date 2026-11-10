package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSkillDetailScreen(store: StudioStore, id: String, onBack: () -> Unit, onGuide: () -> Unit) {
    var confirm by rememberSaveable(id) { mutableStateOf(false) }
    StudioPage("Kỹ năng đã lưu", store, onBack) { state ->
        val skill = state.data.skills.find { it.id == id }
        if (skill != null) {
            Text(skill.title, style = MaterialTheme.typography.headlineLarge)
            Text("${skill.steps.size} bước · dữ liệu mẫu trên máy")
            skill.steps.forEachIndexed { index, step -> Text("${index+1}. $step") }
            LookButton("Bắt đầu làm theo", onClick = onGuide)
            TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text("Xóa kỹ năng", color = MaterialTheme.colorScheme.error) }
        } else if (!state.loading) Text("Kỹ năng này không còn trên máy.")
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Xóa kỹ năng?") },
            text = { Text("Kỹ năng này sẽ bị xóa khỏi Lịch sử. Không thể hoàn tác.") },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.DeleteSkill(id)) }) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy") } })
    }
}
