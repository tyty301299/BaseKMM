package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.kmmbase.android.features.looks.presentation.components.LookButton
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioAddItemScreen(store: StudioStore, onBack: () -> Unit, onPermission: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.add_garment), store, onBack) {
        Text(language.text(R.string.ui_127), style = MaterialTheme.typography.headlineLarge)
        Text(language.text(R.string.ui_016))
        LookButton(language.text(R.string.ui_196), onClick = onPermission)
        Text(language.text(R.string.ui_075), style = MaterialTheme.typography.titleMedium)
        LookButton(language.text(R.string.ui_163)) { store.dispatch(StudioAction.AddSample("coat")) }
        LookButton(language.text(R.string.ui_161)) { store.dispatch(StudioAction.AddSample("blouse")) }
    }
}
