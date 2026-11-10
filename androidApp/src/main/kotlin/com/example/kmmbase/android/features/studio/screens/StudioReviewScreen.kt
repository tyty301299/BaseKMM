package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioReviewScreen(store: StudioStore, onBack: () -> Unit) {
    StudioPage("Duyệt kỹ năng", store, onBack) { state ->
        Text("Bạn xác nhận trước khi lưu.", style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(state.title, { store.dispatch(StudioAction.LessonTitle(it)) }, label = { Text("Tên kỹ năng") }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth())
        state.draftSteps.forEachIndexed { index, step ->
            OutlinedTextField(step, { store.dispatch(StudioAction.EditStep(index, it)) }, label = { Text("Bước ${index + 1}") }, enabled = state.busy.isEmpty(), minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        LookButton("Lưu kỹ năng trên máy", state.canSaveSkill && state.savedSkillId == null) { store.dispatch(StudioAction.SaveSkill) }
    }
}
