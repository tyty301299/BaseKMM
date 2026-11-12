package com.example.kmmbase.android.features.looks.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
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
import com.example.kmmbase.android.features.looks.presentation.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookOutfitContent(state: LookState, store: LookStore, onCamera: () -> Unit, onService: () -> Unit) {
    val language = LocalAppLanguage.current

    Text("AI OUTFITS · CONCEPT", style = MaterialTheme.typography.labelLarge)
    Text(language.text(R.string.outfit_heading), style = MaterialTheme.typography.headlineLarge)
    Text(language.text(R.string.ui_022), color = MaterialTheme.colorScheme.onSurfaceVariant)
    LookMannequin(state.preset, Modifier.fillMaxWidth().height(290.dp).background(LookStudio, RoundedCornerShape(28.dp)))
    LookPresetPicker(state, store::dispatch)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
            FilterChip(selected = state.occasion == choice, onClick = { store.dispatch(LookAction.SelectOccasion(choice)) }, label = { Text(language.systemText(choice)) }, modifier = Modifier.weight(1f))
        }
    }
    LookButton(language.text(R.string.ui_131), onClick = onCamera)
    LookButton(if (!state.outfitSaved) language.text(R.string.ui_110) else language.text(R.string.ui_246), !state.saving && !state.outfitSaved) { store.dispatch(LookAction.SaveOutfit) }
    if (state.saving) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(language.text(R.string.ui_215)) }
    state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error) }
    TextButton(onClick = onService) { Text(language.text(R.string.ui_096)) }
}
