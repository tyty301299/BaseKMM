package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioCameraIntroScreen(store: StudioStore, wardrobe: Boolean, onBack: () -> Unit, onContinue: () -> Unit) {
    StudioPage("Camera", store, onBack) {
        Text(if (wardrobe) "Cho SHADOW thấy món đồ." else "Thử trước. Lưu điều bạn thích.", style = MaterialTheme.typography.headlineLarge)
        Text("Camera trong bản UI này dùng hình nhân hoặc trang phục mẫu. Không xin quyền thiết bị, không chụp ảnh thật và không gửi ảnh lên máy chủ.")
        if (wardrobe) LookButton("Tiếp tục với áo mẫu") { store.dispatch(StudioAction.AddSample("coat")) }
        else LookButton("Mở camera demo", onClick = onContinue)
    }
}
