package com.example.kmmbase.android.features.studio.presentation.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.presentation.components.LookButton
import com.example.kmmbase.android.features.studio.presentation.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioAskScreen(store: StudioStore, onBack: () -> Unit, onService: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_080), store, onBack) { state ->
        Text(language.text(R.string.question_hint), style = MaterialTheme.typography.headlineLarge)
        Text(language.systemText(state.currentStep))
        OutlinedTextField(state.question, { store.dispatch(StudioAction.Question(it)) }, label = { Text(language.text(R.string.ui_137)) }, supportingText = { Text("${state.question.length}/500") }, enabled = state.busy.isEmpty(), minLines = 3, modifier = Modifier.fillMaxWidth())
        LookButton(language.text(R.string.ui_195), state.question.isNotBlank() && state.currentStep.isNotEmpty()) { store.dispatch(StudioAction.AskSample) }
        if (state.answer.isNotEmpty()) Text(state.answer)
        TextButton(onClick = onService, enabled = state.busy.isEmpty()) { Text(language.text(R.string.ui_132)) }
    }
}
