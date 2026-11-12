package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.presentation.components.LookButton
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.looks.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioOccasionScreen(store: StudioStore, looks: LookStore, onBack: () -> Unit, onGenerate: () -> Unit, onAdd: () -> Unit) {
    val language = LocalAppLanguage.current
    val look by looks.state.collectAsState()
    StudioPage(language.text(R.string.occasion_prompt), store, onBack) { state ->
        Text(language.text(R.string.ui_255), style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
                FilterChip(selected = look.occasion == choice, onClick = { looks.dispatch(LookAction.SelectOccasion(choice)) }, enabled = state.busy.isEmpty(), label = { Text(language.systemText(choice)) }, modifier = Modifier.weight(1f))
            }
        }
        OutlinedTextField(state.outfitNotes, { store.dispatch(StudioAction.OutfitNotes(it)) }, label = { Text(language.text(R.string.ui_120)) }, supportingText = { Text("${state.outfitNotes.length}/300") }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth(), minLines = 3)
        Text(language.text(R.string.ui_072))
        if (state.data.items.isEmpty()) {
            Text(language.text(R.string.ui_184))
            LookButton(language.text(R.string.add_garment), onClick = onAdd)
        } else LookButton(language.text(R.string.ui_179), onClick = onGenerate)
    }
}
