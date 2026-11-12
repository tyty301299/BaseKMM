package com.example.kmmbase.android.features.looks.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.presentation.components.*
import com.example.kmmbase.presentation.looks.*
import com.example.kmmbase.presentation.studio.StudioStore
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LookHistoryScreen(store: LookStore, studio: StudioStore, onRecord: (String) -> Unit, onCamera: () -> Unit, onSkill: (String) -> Unit, modifier: Modifier = Modifier) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    val studioState by studio.state.collectAsState()
    LazyColumn(modifier.fillMaxSize().background(LookBackground), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text(language.text(R.string.history_tab), style = MaterialTheme.typography.titleLarge) }
        item { Text(language.text(R.string.ui_142), style = MaterialTheme.typography.headlineLarge) }
        item { Text(language.format(R.string.saved_items_count, (state.records.size + studioState.data.skills.size).toString()), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to language.text(R.string.filter_all), "tryon" to language.text(R.string.ui_169), "outfit" to language.text(R.string.ui_021), "skill" to language.text(R.string.ui_099), "guide" to "Đã làm").forEach { (key, label) ->
                    FilterChip(selected = state.filter == key, onClick = { store.dispatch(LookAction.Filter(key)) }, label = { Text(language.systemText(label)) })
                }
            }
        }
        if (state.loading) item { CircularProgressIndicator() }
        state.error?.let { error -> item {
            Text(language.systemText(error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { store.dispatch(LookAction.Retry) }) { Text(language.text(R.string.refresh)) }
        } }
        if (!state.loading && !studioState.loading && state.filteredRecords.isEmpty() && (state.filter !in setOf("all", "skill") || studioState.data.skills.isEmpty())) item {
            Text(language.text(R.string.ui_039))
            Spacer(Modifier.height(20.dp))
            LookButton(language.text(R.string.ui_165), onClick = onCamera)
        }
        if (state.filter in setOf("all", "skill")) items(studioState.data.skills, key = { "skill-" + it.id }) { skill ->
            Surface(shape = RoundedCornerShape(24.dp), color = LookPanel) {
                Column(Modifier.fillMaxWidth().clickable { onSkill(skill.id) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.title, style = MaterialTheme.typography.titleMedium)
                    Text(language.format(R.string.sample_skill_steps, (skill.steps.size).toString()), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        items(state.filteredRecords, key = { it.id }) { record ->
            Surface(shape = RoundedCornerShape(20.dp), color = LookPanel) {
                Row(Modifier.fillMaxWidth().clickable { onRecord(record.id) }.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (record.isGuide) {
                        Box(Modifier.size(56.dp, 72.dp).background(LookStudio, RoundedCornerShape(10.dp)), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("✓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    } else LookMannequin(record.preset, Modifier.size(56.dp,72.dp).background(LookStudio, RoundedCornerShape(10.dp)), record.before, record.flipped)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(record.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (record.isGuide) when (record.kind) {
                            "guide-manual" -> "Hoàn thành · hướng dẫn mẫu"
                            "guide-demo" -> "Hoàn thành · demo"
                            else -> "Hoàn thành · AI"
                        } else if (record.kind == "tryon") language.text(R.string.ui_170) else language.text(R.string.ui_023), style = MaterialTheme.typography.labelMedium)
                        Text(DateFormat.getDateInstance().format(Date(record.createdAt)), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("›")
                }
            }
        }
    }
}
