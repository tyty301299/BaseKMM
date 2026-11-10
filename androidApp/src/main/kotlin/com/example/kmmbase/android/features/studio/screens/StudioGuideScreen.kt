package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.android.features.studio.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioGuideScreen(store: StudioStore, onBack: () -> Unit, onAsk: () -> Unit, onDone: () -> Unit) {
    StudioPage("Làm theo", store, onBack) { state ->
        val skill = state.activeSkill
        if (skill == null) Text("Không tìm thấy kỹ năng. Hãy quay lại danh sách.")
        else if (state.completed) {
            Text("Bạn đã hoàn thành.", style = MaterialTheme.typography.headlineLarge)
            Text("Hoàn thành luồng hướng dẫn mẫu. AI chưa đánh giá thao tác thật.")
            LookButton("Về Học & làm", onClick = onDone)
        } else {
            Text(skill.title, style = MaterialTheme.typography.headlineLarge)
            Text("BƯỚC ${state.stepIndex + 1} / ${skill.steps.size}")
            LinearProgressIndicator(progress = { (state.stepIndex + 1).toFloat() / skill.steps.size }, modifier = Modifier.fillMaxWidth())
            StudioInstructionIllustration()
            Text(state.currentStep, style = MaterialTheme.typography.headlineSmall)
            Text("Hướng dẫn mẫu. Không dùng camera để đánh giá thao tác.")
            LookButton("Tôi đã làm xong bước này") { store.dispatch(StudioAction.NextStep) }
            TextButton(onClick = { store.dispatch(StudioAction.PreviousStep) }, enabled = state.stepIndex > 0 && state.busy.isEmpty()) { Text("Bước trước") }
            LookButton("Hỏi về bước này", onClick = onAsk)
        }
    }
}
