package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.common.LanguageSetting
import com.example.kmmbase.android.R
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSettingsScreen(store: StudioStore, remote: com.example.kmmbase.presentation.shadow.ShadowStore, onBack: () -> Unit, onService: () -> Unit, onComponents: () -> Unit) {
    val language = LocalAppLanguage.current
    val remoteState by remote.state.collectAsState()
    var confirm by rememberSaveable { mutableStateOf(false) }
    StudioPage(language.text(R.string.options), store, onBack) { state ->
        com.example.kmmbase.android.common.ConnectionStatus(remoteState, { remote.dispatch(com.example.kmmbase.presentation.shadow.ShadowAction.Reconnect) }, onService)
        if (remoteState.connected) Text(remoteState.endpoint, style = MaterialTheme.typography.bodySmall)
        remoteState.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error) }
        Text(language.text(R.string.connection_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Card(onClick = onService, shape = MaterialTheme.shapes.large) {
            ListItem(headlineContent = { Text(language.text(R.string.connection_title)) },
                supportingContent = { Text(language.text(R.string.auto_connect)) }, trailingContent = { Text("›") })
        }
        Text(language.text(R.string.language_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        LanguageSetting()
        Text(language.text(R.string.appearance_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.text(R.string.dark_appearance), Modifier.weight(1f))
            Switch(state.data.settings.dark, { store.dispatch(StudioAction.Appearance(it, state.data.settings.glass)) }, enabled = state.busy.isEmpty())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.text(R.string.glass_effect), Modifier.weight(1f))
            Switch(state.data.settings.glass, { store.dispatch(StudioAction.Appearance(state.data.settings.dark, it)) }, enabled = state.busy.isEmpty())
        }
        TextButton(onClick = onComponents, enabled = state.busy.isEmpty()) { Text(language.text(R.string.view_components)) }
        Text(language.text(R.string.local_data), style = MaterialTheme.typography.headlineSmall)
        Text(language.text(R.string.local_data_description))
        TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text(language.text(R.string.clear_all_samples), color = MaterialTheme.colorScheme.error) }
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(language.text(R.string.clear_samples_short_question)) },
            text = { Text(language.text(R.string.clear_samples_warning)) },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.ClearData) }) { Text(language.text(R.string.clear_data)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(language.text(R.string.cancel)) } })
    }
}
