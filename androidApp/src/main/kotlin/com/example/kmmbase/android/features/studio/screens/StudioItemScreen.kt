package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioItemScreen(store: StudioStore, id: String, onBack: () -> Unit, onTry: (String) -> Unit) {
    val language = LocalAppLanguage.current
    val current by store.state.collectAsState()
    LaunchedEffect(id, current.data.items.map { it.id }) {
        if (store.currentState.itemDraft?.id != id) store.dispatch(StudioAction.OpenItem(id))
    }
    var confirm by rememberSaveable(id) { mutableStateOf(false) }
    StudioPage(language.text(R.string.ui_030), store, onBack) { state ->
        val item = state.itemDraft?.takeIf { it.id == id && state.data.items.any { saved -> saved.id == id } }
        if (item == null) { if (!state.loading) Text(language.text(R.string.ui_094)) } else {
            StudioGarmentImage(item.preset, Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)))
            OutlinedTextField(item.name, { store.dispatch(StudioAction.ItemName(it)) }, label = { Text(language.text(R.string.ui_177)) }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Áo", "Áo khoác").forEach { category ->
                    FilterChip(selected = item.category == category, onClick = { store.dispatch(StudioAction.ItemCategory(category)) }, enabled = state.busy.isEmpty(), label = { Text(language.systemText(category)) })
                }
            }
            Text(language.text(R.string.ui_173))
            LookButton(language.text(R.string.ui_114), item.name.isNotBlank()) { store.dispatch(StudioAction.SaveItem) }
            LookButton(language.text(R.string.ui_130)) { onTry(item.preset) }
            TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text(language.text(R.string.ui_205), color = MaterialTheme.colorScheme.error) }
        }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(language.text(R.string.ui_207)) },
            text = { Text(language.text(R.string.ui_126)) },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.DeleteItem(id)) }) { Text(language.text(R.string.ui_197)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(language.text(R.string.cancel)) } })
    }
}
