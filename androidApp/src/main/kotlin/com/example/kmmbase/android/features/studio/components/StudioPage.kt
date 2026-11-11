package com.example.kmmbase.android.features.studio.components

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioPage(title: String, store: StudioStore, onBack: () -> Unit, content: @Composable ColumnScope.(StudioState) -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    val busy = state.busy.isNotEmpty()
    BackHandler(enabled = busy) {}
    LookTheme {
        CompositionLocalProvider(LocalOperationBusy provides busy) {
            Column(Modifier.fillMaxSize().background(LookBackground).safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onBack, enabled = !busy) { Text(language.text(R.string.back)) }
                    Text(title, Modifier.weight(1f).padding(vertical = 14.dp), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(language.text(R.string.ui_235)) }
                    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(language.systemText(state.busy)) }
                    state.error?.let {
                        Text(language.systemText(it), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { store.dispatch(StudioAction.Retry) }, enabled = !busy) { Text(language.text(R.string.ui_180)) }
                    }
                    content(state)
                }
            }
        }
    }
}
