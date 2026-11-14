package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.presentation.components.*
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioComponentsScreen(store: StudioStore, onBack: () -> Unit, onCamera: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_159), store, onBack) {
        Text("SKI / Mobile system", style = MaterialTheme.typography.headlineLarge)
        Text(language.text(R.string.ui_147))
        Text("Adaptive glass / Material", Modifier.fillMaxWidth().lookGlass().padding(24.dp))
        LookButton(language.text(R.string.ui_166), onClick = onCamera)
        Text(language.text(R.string.ui_186))
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(language.text(R.string.ui_064))
    }
}
