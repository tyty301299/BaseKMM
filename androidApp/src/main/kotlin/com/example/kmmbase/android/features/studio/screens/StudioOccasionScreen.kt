package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.looks.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioOccasionScreen(store: StudioStore, looks: LookStore, onBack: () -> Unit, onGenerate: () -> Unit, onAdd: () -> Unit) {
    val look by looks.state.collectAsState()
    StudioPage("Bạn sắp đi đâu?", store, onBack) { state ->
        Text("Đúng dịp. Đúng cảm giác.", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
                FilterChip(selected = look.occasion == choice, onClick = { looks.dispatch(LookAction.SelectOccasion(choice)) }, enabled = state.busy.isEmpty(), label = { Text(choice) }, modifier = Modifier.weight(1f))
            }
        }
        OutlinedTextField(state.outfitNotes, { store.dispatch(StudioAction.OutfitNotes(it)) }, label = { Text("Mong muốn thêm") }, supportingText = { Text("${state.outfitNotes.length}/300") }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text("Gợi ý mẫu chỉ lấy từ tủ đồ local, chưa xử lý yêu cầu bằng AI.")
        if (state.data.items.isEmpty()) {
            Text("Tủ đồ đang trống. Thêm một món trước nhé.")
            LookButton("Thêm trang phục", onClick = onAdd)
        } else LookButton("Tạo gợi ý mẫu", onClick = onGenerate)
    }
}
