package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioItemScreen(store: StudioStore, id: String, onBack: () -> Unit, onTry: (String) -> Unit) {
    val current by store.state.collectAsState()
    LaunchedEffect(id, current.data.items.map { it.id }) {
        if (store.currentState.itemDraft?.id != id) store.dispatch(StudioAction.OpenItem(id))
    }
    var confirm by rememberSaveable(id) { mutableStateOf(false) }
    StudioPage("Chi tiết trang phục", store, onBack) { state ->
        val item = state.itemDraft?.takeIf { it.id == id && state.data.items.any { saved -> saved.id == id } }
        if (item == null) { if (!state.loading) Text("Không tìm thấy trang phục này.") } else {
            LookMannequin(item.preset, Modifier.fillMaxWidth().height(300.dp).background(LookStudio, RoundedCornerShape(28.dp)))
            OutlinedTextField(item.name, { store.dispatch(StudioAction.ItemName(it)) }, label = { Text("Tên trang phục") }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Áo", "Áo khoác").forEach { category ->
                    FilterChip(selected = item.category == category, onClick = { store.dispatch(StudioAction.ItemCategory(category)) }, enabled = state.busy.isEmpty(), label = { Text(category) })
                }
            }
            Text("Trang phục mẫu")
            LookButton("Lưu thay đổi", item.name.isNotBlank()) { store.dispatch(StudioAction.SaveItem) }
            LookButton("Mở camera thử mẫu") { onTry(item.preset) }
            TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text("Xóa trang phục", color = MaterialTheme.colorScheme.error) }
        }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Xóa trang phục?") },
            text = { Text("Món này sẽ được xóa khỏi tủ đồ local.") },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.DeleteItem(id)) }) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Hủy") } })
    }
}
