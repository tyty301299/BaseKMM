package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.android.features.studio.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioTeachScreen(store: StudioStore, onBack: () -> Unit, onRecord: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_034), store, onBack) {
        Text(language.text(R.string.ui_031), style = MaterialTheme.typography.headlineLarge)
        StudioInstructionIllustration()
        Text(language.text(R.string.ui_000))
        Text(language.text(R.string.ui_001))
        Text(language.text(R.string.ui_002))
        Text(language.text(R.string.ui_012), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LookButton(language.text(R.string.ui_020), onClick = onRecord)
    }
}
