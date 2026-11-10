package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioAskScreen(store: StudioStore, onBack: () -> Unit, onService: () -> Unit) {
    StudioPage("Hỏi SHADOW", store, onBack) { state ->
        Text("Bạn đang vướng ở đâu?", style = MaterialTheme.typography.headlineLarge)
        Text(state.currentStep)
        OutlinedTextField(state.question, { store.dispatch(StudioAction.Question(it)) }, label = { Text("Nhập câu hỏi") }, supportingText = { Text("${state.question.length}/500") }, enabled = state.busy.isEmpty(), minLines = 3, modifier = Modifier.fillMaxWidth())
        LookButton("Xem gợi ý mẫu", state.question.isNotBlank() && state.currentStep.isNotEmpty()) { store.dispatch(StudioAction.AskSample) }
        if (state.answer.isNotEmpty()) Text(state.answer)
        TextButton(onClick = onService, enabled = state.busy.isEmpty()) { Text("Mở dịch vụ AI thật") }
    }
}
