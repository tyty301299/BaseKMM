package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSettingsScreen(store: StudioStore, onBack: () -> Unit, onService: () -> Unit, onComponents: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    StudioPage("Tùy chọn", store, onBack) { state ->
        Text("Ít chi tiết. Đúng điểm nhấn.", style = MaterialTheme.typography.headlineLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Giao diện tối", Modifier.weight(1f))
            Switch(state.data.settings.dark, { store.dispatch(StudioAction.Appearance(it, state.data.settings.glass)) }, enabled = state.busy.isEmpty())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Hiệu ứng kính", Modifier.weight(1f))
            Switch(state.data.settings.glass, { store.dispatch(StudioAction.Appearance(state.data.settings.dark, it)) }, enabled = state.busy.isEmpty())
        }
        Text("Android dùng bề mặt Material với hiệu ứng kính tương ứng. Tắt kính để dùng nền đặc.")
        LookButton("Kết nối dịch vụ SHADOW", onClick = onService)
        TextButton(onClick = onComponents, enabled = state.busy.isEmpty()) { Text("Xem thành phần UI") }
        Text("Dữ liệu local", style = MaterialTheme.typography.headlineSmall)
        Text("Tủ mẫu, kỹ năng và lịch sử được lưu trên máy. Xóa dữ liệu không thể hoàn tác.")
        TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text("Xóa toàn bộ dữ liệu mẫu", color = MaterialTheme.colorScheme.error) }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Xóa dữ liệu mẫu?") },
            text = { Text("Tủ mẫu, kỹ năng và lịch sử sẽ bị xóa. Không thể hoàn tác.") },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.ClearData) }) { Text("Xóa dữ liệu") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy") } })
    }
}
