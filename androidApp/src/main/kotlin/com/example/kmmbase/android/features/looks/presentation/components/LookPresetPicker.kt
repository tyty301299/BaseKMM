package com.example.kmmbase.android.features.looks.presentation.components

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kmmbase.presentation.looks.LookAction
import com.example.kmmbase.presentation.looks.LookState

@Composable
internal fun LookPresetPicker(state: LookState, send: (LookAction) -> Unit) {
    val language = LocalAppLanguage.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("coat" to language.text(R.string.ui_190), "blouse" to language.text(R.string.ui_025)).forEach { (key, label) ->
            TextButton(onClick = { send(LookAction.SelectPreset(key)) }, enabled = !state.saving,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).lookGlass(state.preset == key)
                    .semantics { selected = state.preset == key }) { Text(language.systemText(label)) }
        }
    }
}
