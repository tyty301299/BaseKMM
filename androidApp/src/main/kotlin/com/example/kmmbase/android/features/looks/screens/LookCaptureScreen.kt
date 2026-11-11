package com.example.kmmbase.android.features.looks.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.domain.model.SavedLook
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookCaptureScreen(state: LookState, capture: SavedLook, send: (LookAction) -> Unit, onClose: () -> Unit, onHistory: () -> Unit) {
    val language = LocalAppLanguage.current
    Column(Modifier.fillMaxSize().background(LookBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(language.text(R.string.ui_098), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose, enabled = !state.saving) { Text(language.text(R.string.close)) }
        }
        LookMannequin(capture.preset, Modifier.fillMaxWidth().height(380.dp).background(LookStudio, RoundedCornerShape(28.dp)), capture.before, capture.flipped)
        Text(capture.title, style = MaterialTheme.typography.headlineLarge)
        Text(language.format(R.string.sample_result_occasion, (capture.occasion).toString()))
        Text(language.text(R.string.ui_113))
        if (state.saving) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(language.text(R.string.ui_219)) }
        state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error) }
        if (state.savedId == capture.id) {
            Text(language.text(R.string.ui_246), color = LookAccent)
            LookButton(language.text(R.string.ui_193), onClick = onHistory)
        } else {
            LookButton(if (state.saving) language.text(R.string.ui_220) else language.text(R.string.ui_115), !state.saving) { send(LookAction.SaveCapture) }
        }
        TextButton(onClick = { send(LookAction.Retake) }, enabled = !state.saving) { Text(language.text(R.string.ui_045)) }
    }
}
