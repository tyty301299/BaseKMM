package com.example.kmmbase.android.features.looks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*
import com.example.kmmbase.presentation.studio.StudioStore
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LookHistoryScreen(store: LookStore, studio: StudioStore, onRecord: (String) -> Unit, onCamera: () -> Unit, onSkill: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by store.state.collectAsState()
    val studioState by studio.state.collectAsState()
    LazyColumn(modifier.fillMaxSize().background(LookBackground), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Lịch sử", style = MaterialTheme.typography.titleLarge) }
        item { Text("Những lần bạn thích.", style = MaterialTheme.typography.headlineLarge) }
        item { Text("${state.records.size + studioState.data.skills.size} mục · lưu trên máy", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "Tất cả", "tryon" to "Thử đồ", "outfit" to "Bộ phối", "skill" to "Kỹ năng").forEach { (key, label) ->
                    FilterChip(selected = state.filter == key, onClick = { store.dispatch(LookAction.Filter(key)) }, label = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { error -> item {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { store.dispatch(LookAction.Retry) }) { Text("Tải lại") }
        } }
        if (!state.loading && !studioState.loading && state.filteredRecords.isEmpty() && (state.filter !in setOf("all", "skill") || studioState.data.skills.isEmpty())) item {
            Text("Chưa có mục đã lưu trong nhóm này.")
            Spacer(Modifier.height(20.dp))
            LookButton("Thử bộ đồ đầu tiên", onClick = onCamera)
        }
        if (state.filter in setOf("all", "skill")) items(studioState.data.skills, key = { "skill-" + it.id }) { skill ->
            Surface(shape = RoundedCornerShape(24.dp), color = LookPanel) {
                Column(Modifier.fillMaxWidth().clickable { onSkill(skill.id) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.title, style = MaterialTheme.typography.titleMedium)
                    Text("Kỹ năng mẫu · ${skill.steps.size} bước", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        items(state.filteredRecords, key = { it.id }) { record ->
            Surface(shape = RoundedCornerShape(20.dp), color = LookPanel) {
                Row(Modifier.fillMaxWidth().clickable { onRecord(record.id) }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LookMannequin(record.preset, Modifier.size(56.dp,72.dp).background(LookStudio, RoundedCornerShape(10.dp)), record.before, record.flipped)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (record.kind == "tryon") "Thử đồ mẫu" else "Bộ phối mẫu", style = MaterialTheme.typography.labelMedium)
                        Text(DateFormat.getDateInstance().format(Date(record.createdAt)), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("›")
                }
            }
        }
    }
}
