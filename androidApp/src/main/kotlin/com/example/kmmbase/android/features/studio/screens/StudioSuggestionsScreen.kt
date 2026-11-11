package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSuggestionsScreen(store: StudioStore, onBack: () -> Unit, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_097), store, onBack) { state ->
        Text(language.text(R.string.ui_150), style = MaterialTheme.typography.headlineLarge)
        if (state.suggestions.isEmpty()) { Text(language.text(R.string.ui_036)); LookButton(language.text(R.string.add_garment), onClick = onAdd) }
        state.suggestions.forEach { choice ->
            Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LookMannequin(choice.preset, Modifier.fillMaxWidth().height(240.dp))
                Text(choice.title, style = MaterialTheme.typography.headlineSmall)
                Text(choice.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LookButton(language.text(R.string.ui_194)) { onSelect(choice.preset) }
            }
        }
    }
}
