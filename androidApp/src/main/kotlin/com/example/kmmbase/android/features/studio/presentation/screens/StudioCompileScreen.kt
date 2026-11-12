package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.presentation.components.LookButton
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioCompileScreen(store: StudioStore, onBack: () -> Unit, onReview: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_003), store, onBack) { state ->
        Text(if (state.busy.isNotEmpty()) language.text(R.string.ui_210) else language.text(R.string.ui_185), style = MaterialTheme.typography.headlineLarge)
        Text(language.text(R.string.ui_047))
        if (state.draftSteps.isNotEmpty()) LookButton(language.text(R.string.ui_054), onClick = onReview)
        else if (state.busy.isEmpty()) LookButton(language.text(R.string.ui_035)) { store.dispatch(StudioAction.CompileSample) }
    }
}
