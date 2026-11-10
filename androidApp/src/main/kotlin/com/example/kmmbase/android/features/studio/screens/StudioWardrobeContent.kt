package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioWardrobeContent(store: StudioStore, onAdd: () -> Unit, onItem: (String) -> Unit) {
    val state by store.state.collectAsState()
    Text("Tủ đồ của bạn.", style = MaterialTheme.typography.headlineLarge)
    Text("Trang phục mẫu lưu trên máy", color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { store.dispatch(StudioAction.Retry) }) { Text("Tải lại") } }
    if (!state.loading && state.data.items.isEmpty()) {
        Text("Tủ đồ đang chờ bạn.", style = MaterialTheme.typography.headlineSmall)
        Text("Thêm món đầu tiên để thử luồng phối đồ.")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all" to "Tất cả", "Áo" to "Áo", "Áo khoác" to "Áo khoác").forEach { (key, label) ->
            FilterChip(selected = state.wardrobeFilter == key, onClick = { store.dispatch(StudioAction.WardrobeFilter(key)) },
                enabled = state.busy.isEmpty(), label = { Text(label) }, modifier = Modifier.weight(1f))
        }
    }
    if (state.data.items.isNotEmpty() && state.filteredItems.isEmpty()) Text("Chưa có trang phục trong nhóm này.")
    state.filteredItems.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { item ->
                Column(Modifier.weight(1f).clickable(enabled = state.busy.isEmpty()) { onItem(item.id) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LookMannequin(item.preset, Modifier.fillMaxWidth().height(180.dp).background(LookStudio, RoundedCornerShape(24.dp)))
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text("${item.category} · mẫu", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    LookButton("Thêm trang phục", state.busy.isEmpty(), onAdd)
}
