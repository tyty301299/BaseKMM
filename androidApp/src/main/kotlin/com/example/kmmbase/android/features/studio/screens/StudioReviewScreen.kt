package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioReviewScreen(store: StudioStore, onBack: () -> Unit) {
    val language = LocalAppLanguage.current
    StudioPage(language.text(R.string.ui_056), store, onBack) { state ->
        Text(language.text(R.string.ui_013), style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(state.title, { store.dispatch(StudioAction.LessonTitle(it)) }, label = { Text(language.text(R.string.ui_176)) }, enabled = state.busy.isEmpty(), modifier = Modifier.fillMaxWidth())
        state.draftSteps.forEachIndexed { index, step ->
            OutlinedTextField(step, { store.dispatch(StudioAction.EditStep(index, it)) }, label = { Text(language.format(R.string.step_number, (index + 1).toString())) }, enabled = state.busy.isEmpty(), minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        LookButton(language.text(R.string.ui_112), state.canSaveSkill && state.savedSkillId == null) { store.dispatch(StudioAction.SaveSkill) }
    }
}
