package com.example.kmmbase.android.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.R
import com.example.kmmbase.presentation.shadow.ShadowState

@Composable
internal fun ConnectionStatus(state: ShadowState, onReconnect: () -> Unit, onSettings: () -> Unit) {
    val language = LocalAppLanguage.current
    val connecting = !state.connected && state.busy.isNotEmpty()
    val status = when {
        connecting -> R.string.connecting
        state.connected -> R.string.connected
        state.error != null -> R.string.connection_failed
        else -> R.string.not_connected
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = onSettings, label = { Text(language.text(status)) }, leadingIcon = {
            if (connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text(if (state.connected) "✓" else "!", color = if (state.connected) Color(0xFF70C58C) else MaterialTheme.colorScheme.error)
        })
        TextButton(onClick = onReconnect, enabled = state.busy.isEmpty()) { Text(language.text(R.string.reconnect)) }
    }
}
