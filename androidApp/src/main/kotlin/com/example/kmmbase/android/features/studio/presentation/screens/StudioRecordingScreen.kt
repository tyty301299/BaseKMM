package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.presentation.components.*
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.android.features.studio.presentation.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioRecordingScreen(store: StudioStore, onBack: () -> Unit, onCompile: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_149), store, onBack) { state ->
        StudioInstructionIllustration()
        Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(28.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(if (state.recording) language.text(R.string.ui_221) else language.text(R.string.ui_236), style = MaterialTheme.typography.headlineSmall)
            Text(language.text(R.string.ui_082))
        }
        OutlinedTextField(state.transcript, { store.dispatch(StudioAction.Transcript(it)) }, label = { Text(language.text(R.string.ui_118)) }, enabled = state.busy.isEmpty(), minLines = 3, modifier = Modifier.fillMaxWidth())
        Text(language.text(R.string.ui_146), style = MaterialTheme.typography.bodySmall)
        if (state.recording) LookButton(language.text(R.string.ui_062)) { store.dispatch(StudioAction.StopRecording) }
        else {
            LookButton(language.text(R.string.ui_192), state.recorded, onCompile)
            TextButton(onClick = { store.dispatch(StudioAction.StartRecording) }, enabled = state.busy.isEmpty()) { Text(language.text(R.string.ui_106)) }
        }
    }
}
