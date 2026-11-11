package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.android.features.studio.components.StudioInstructionIllustration
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioGuideScreen(store: StudioStore, onBack: () -> Unit, onAsk: () -> Unit, onDone: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_107), store, onBack) { state ->
        val skill = state.activeSkill
        if (skill == null) Text(language.text(R.string.ui_093))
        else if (state.completed) {
            Text(language.text(R.string.ui_015), style = MaterialTheme.typography.headlineLarge)
            Text(language.text(R.string.ui_073))
            LookButton(language.text(R.string.ui_187), onClick = onDone)
        } else {
            Text(skill.title, style = MaterialTheme.typography.headlineLarge)
            Text(language.format(R.string.step_progress, (state.stepIndex + 1).toString(), (skill.steps.size).toString()))
            LinearProgressIndicator(progress = { (state.stepIndex + 1).toFloat() / skill.steps.size }, modifier = Modifier.fillMaxWidth())
            StudioInstructionIllustration()
            Text(language.systemText(state.currentStep), style = MaterialTheme.typography.headlineSmall)
            Text(language.text(R.string.ui_079))
            LookButton(language.text(R.string.ui_178)) { store.dispatch(StudioAction.NextStep) }
            TextButton(onClick = { store.dispatch(StudioAction.PreviousStep) }, enabled = state.stepIndex > 0 && state.busy.isEmpty()) { Text(language.text(R.string.previous_step)) }
            LookButton(language.text(R.string.ui_081), onClick = onAsk)
        }
    }
}
