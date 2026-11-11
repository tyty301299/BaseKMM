package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.kmmbase.android.features.looks.components.LookButton
import com.example.kmmbase.android.features.studio.components.StudioPage
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioSkillDetailScreen(store: StudioStore, id: String, onBack: () -> Unit, onGuide: () -> Unit) {
    val language = LocalAppLanguage.current
    var confirm by rememberSaveable(id) { mutableStateOf(false) }
    StudioPage(language.text(R.string.ui_104), store, onBack) { state ->
        val skill = state.data.skills.find { it.id == id }
        if (skill != null) {
            Text(skill.title, style = MaterialTheme.typography.headlineLarge)
            Text(language.format(R.string.sample_steps_count, (skill.steps.size).toString()))
            skill.steps.forEachIndexed { index, step -> Text("${index+1}. $step") }
            LookButton(language.text(R.string.ui_019), onClick = onGuide)
            TextButton(onClick = { confirm = true }, enabled = state.busy.isEmpty()) { Text(language.text(R.string.ui_200), color = MaterialTheme.colorScheme.error) }
        } else if (!state.loading) Text(language.text(R.string.ui_102))
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(language.text(R.string.ui_202)) },
            text = { Text(language.text(R.string.ui_103)) },
            confirmButton = { TextButton(onClick = { confirm = false; store.dispatch(StudioAction.DeleteSkill(id)) }) { Text(language.text(R.string.ui_197)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(language.text(R.string.cancel)) } })
    }
}
