package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.android.features.studio.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioTeachScreen(store: StudioStore, onBack: () -> Unit, onRecord: () -> Unit) {
    StudioPage("Chuẩn bị dạy", store, onBack) {
        Text("Cho AI thấy cách bạn làm.", style = MaterialTheme.typography.headlineLarge)
        StudioInstructionIllustration()
        Text("01  Đặt điện thoại cố định")
        Text("02  Vừa làm vừa giải thích")
        Text("03  Duyệt các bước trước khi lưu")
        Text("Bài mẫu: lắp giá đỡ điện thoại. Chế độ demo không mở camera hoặc micro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LookButton("Bắt đầu thao tác mẫu", onClick = onRecord)
    }
}
