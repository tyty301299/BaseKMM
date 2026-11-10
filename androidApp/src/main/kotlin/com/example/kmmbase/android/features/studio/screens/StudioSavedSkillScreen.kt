package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioSavedSkillScreen(store: StudioStore, id: String, onBack: () -> Unit, onGuide: () -> Unit, onHistory: () -> Unit) {
    StudioPage("Đã lưu kỹ năng", store, onBack) { state ->
        if (state.data.skills.any { it.id == id }) {
            Text("Đã lưu một cách làm hay.", style = MaterialTheme.typography.headlineLarge)
            Text("Kỹ năng mẫu nằm trên máy này. Mở lại để làm theo hoặc xem trong Lịch sử.")
            LookButton("Làm theo ngay", onClick = onGuide)
            LookButton("Xem Lịch sử", onClick = onHistory)
        } else if (!state.loading) Text("Không tìm thấy kỹ năng đã lưu.")
    }
}
