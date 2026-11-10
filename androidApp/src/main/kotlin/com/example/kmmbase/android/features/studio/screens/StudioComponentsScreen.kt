package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioComponentsScreen(store: StudioStore, onBack: () -> Unit, onCamera: () -> Unit) {
    StudioPage("Thành phần UI", store, onBack) {
        Text("SHADOW / Mobile system", style = MaterialTheme.typography.headlineLarge)
        Text("Nội dung rõ ràng, kính ở lớp điều hướng.")
        Text("Adaptive glass / Material", Modifier.fillMaxWidth().lookGlass().padding(24.dp))
        LookButton("Thử màn camera", onClick = onCamera)
        Text("Ví dụ trạng thái đang xử lý")
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("Dữ liệu mẫu, không phải AI")
    }
}
