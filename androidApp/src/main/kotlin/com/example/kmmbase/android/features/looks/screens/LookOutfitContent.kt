package com.example.kmmbase.android.features.looks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookOutfitContent(state: LookState, store: LookStore, onCamera: () -> Unit, onService: () -> Unit) {

    Text("AI OUTFITS · CONCEPT", style = MaterialTheme.typography.labelLarge)
    Text("Mặc đúng gu.\nĐúng dịp.", style = MaterialTheme.typography.headlineLarge)
    Text("Bộ phối minh họa để thử giao diện. Kết nối SHADOW để dùng tủ đồ thật.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    LookMannequin(state.preset, Modifier.fillMaxWidth().height(290.dp).background(LookStudio, RoundedCornerShape(28.dp)))
    LookPresetPicker(state, store::dispatch)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
            FilterChip(selected = state.occasion == choice, onClick = { store.dispatch(LookAction.SelectOccasion(choice)) }, label = { Text(choice) }, modifier = Modifier.weight(1f))
        }
    }
    LookButton("Mở camera thử đồ", onClick = onCamera)
    LookButton(if (!state.outfitSaved) "Lưu bộ phối mẫu" else "Đã lưu trên máy", !state.saving && !state.outfitSaved) { store.dispatch(LookAction.SaveOutfit) }
    if (state.saving) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Đang lưu bộ phối…") }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    TextButton(onClick = onService) { Text("Kết nối tủ đồ & AI thật") }
}
