package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioGeneratingScreen(store: StudioStore, onBack: () -> Unit, onResults: () -> Unit, onRetry: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_033), store, onBack) { state ->
        Text(if (state.busy.isNotEmpty()) language.text(R.string.ui_213) else language.text(R.string.ui_024), style = MaterialTheme.typography.headlineLarge)
        Text(language.text(R.string.ui_052))
        if (state.suggested) LookButton(language.text(R.string.ui_194), onClick = onResults)
        else if (state.busy.isEmpty()) LookButton(language.text(R.string.retry), onClick = onRetry)
    }
}
