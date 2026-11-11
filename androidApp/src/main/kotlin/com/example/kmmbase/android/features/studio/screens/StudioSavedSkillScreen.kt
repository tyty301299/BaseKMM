package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.StudioStore

@Composable
internal fun StudioSavedSkillScreen(store: StudioStore, id: String, onBack: () -> Unit, onGuide: () -> Unit, onHistory: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_242), store, onBack) { state ->
        if (state.data.skills.any { it.id == id }) {
            Text(language.text(R.string.ui_245), style = MaterialTheme.typography.headlineLarge)
            Text(language.text(R.string.ui_101))
            LookButton(language.text(R.string.ui_108), onClick = onGuide)
            LookButton(language.text(R.string.ui_193), onClick = onHistory)
        } else if (!state.loading) Text(language.text(R.string.ui_092))
    }
}
