package com.example.kmmbase.android.features.studio.screens

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioAddItemScreen(store: StudioStore, onBack: () -> Unit, onPermission: () -> Unit) {
    StudioPage("Thêm trang phục", store, onBack) {
        Text("Một món đồ.\nMột bức ảnh rõ.", style = MaterialTheme.typography.headlineLarge)
        Text("Bản UI này chỉ thêm mẫu có sẵn, chưa truy cập camera hoặc thư viện ảnh thật.")
        LookButton("Xem luồng chụp ảnh", onClick = onPermission)
        Text("Hoặc chọn mẫu để lưu vào tủ đồ", style = MaterialTheme.typography.titleMedium)
        LookButton("Thêm áo xanh rêu") { store.dispatch(StudioAction.AddSample("coat")) }
        LookButton("Thêm blouse cam đào") { store.dispatch(StudioAction.AddSample("blouse")) }
    }
}
