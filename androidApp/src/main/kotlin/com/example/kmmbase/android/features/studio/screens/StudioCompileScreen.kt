package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioCompileScreen(store: StudioStore, onBack: () -> Unit, onReview: () -> Unit) {
    StudioPage("AI chia bước · demo", store, onBack) { state ->
        Text(if (state.busy.isNotEmpty()) "Đang chuẩn bị bài mẫu…" else "Từng bước đã rõ hơn.", style = MaterialTheme.typography.headlineLarge)
        Text("Các bước minh họa cho bài lắp giá đỡ điện thoại. Chưa phân tích video bằng AI.")
        if (state.draftSteps.isNotEmpty()) LookButton("Duyệt các bước", onClick = onReview)
        else if (state.busy.isEmpty()) LookButton("Chuẩn bị lại bài mẫu") { store.dispatch(StudioAction.CompileSample) }
    }
}
