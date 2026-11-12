package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.presentation.components.LookButton
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioCameraIntroScreen(store: StudioStore, wardrobe: Boolean, onBack: () -> Unit, onContinue: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage("Camera", store, onBack) {
        Text(if (wardrobe) language.text(R.string.ui_032) else language.text(R.string.ui_167), style = MaterialTheme.typography.headlineLarge)
        Text(language.text(R.string.ui_028))
        if (wardrobe) LookButton(language.text(R.string.ui_172)) { store.dispatch(StudioAction.AddSample("coat")) }
        else LookButton(language.text(R.string.ui_129), onClick = onContinue)
    }
}
