package com.example.kmmbase.android.features.looks.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookCameraScreen(store: LookStore, onClose: () -> Unit, onHistory: () -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    BackHandler { if (!state.saving) { if (state.capture != null) store.dispatch(LookAction.Retake) else onClose() } }
    LookTheme {
        val capture = state.capture
        if (capture != null) {
            LookCaptureScreen(state, capture, store::dispatch, onClose, onHistory)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().background(LookStudio)) {
                LookMannequin(state.preset, Modifier.fillMaxWidth().height(maxHeight * .65f).align(Alignment.Center).offset(y = (-30).dp), state.before, state.flipped)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .85f)))))
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val controlHeight = maxOf(maxHeight, 480.dp * LocalDensity.current.fontScale)
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).height(controlHeight).padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CameraControl(language.text(R.string.back), LookSymbol.BACK, onClose)
                            Text(language.text(R.string.ui_027), Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
                            CameraControl(language.text(R.string.ui_116), LookSymbol.FLIP) { store.dispatch(LookAction.Flip) }
                        }
                        TextButton(onClick = { store.dispatch(LookAction.Compare) }, modifier = Modifier.lookGlass().heightIn(min = 48.dp)) {
                            Text(if (state.before) language.text(R.string.ui_175) else language.text(R.string.ui_229), color = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(language.text(R.string.ui_077), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        LookPresetPicker(state, store::dispatch)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            CameraControl(language.text(R.string.ui_128), LookSymbol.HISTORY, onHistory)
                            TextButton(onClick = { store.dispatch(LookAction.Capture) }, contentPadding = PaddingValues(7.dp),
                                modifier = Modifier.size(84.dp).border(3.dp, Color.White, CircleShape).semantics { contentDescription = language.text(R.string.ui_044) }) {
                                Box(Modifier.fillMaxSize().background(Color.White, CircleShape))
                            }
                            Spacer(Modifier.size(48.dp))
                        }
                        Text(language.text(R.string.ui_046), Modifier.align(Alignment.CenterHorizontally), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraControl(label: String, symbol: LookSymbol, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.size(48.dp).lookGlass().semantics { contentDescription = label }) {
        LookIcon(symbol)
    }
}
