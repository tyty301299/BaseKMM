package com.example.kmmbase.android.features.studio.screens

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioLearningContent(store: StudioStore, onTeach: () -> Unit, onSkill: (String) -> Unit, onService: () -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    Text(language.text(R.string.learn_heading), style = MaterialTheme.typography.headlineLarge)
    Text(language.text(R.string.ui_168))
    LookButton(language.text(R.string.ui_060), state.busy.isEmpty(), onTeach)
    TextButton(onClick = onService) { Text(language.text(R.string.ui_057)) }
    Text(language.text(R.string.ui_104), style = MaterialTheme.typography.headlineSmall)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error); TextButton(onClick = { store.dispatch(StudioAction.Retry) }) { Text(language.text(R.string.refresh)) } }
    if (state.data.skills.isEmpty()) Text(language.text(R.string.ui_038))
    state.data.skills.forEach { skill ->
        Column(Modifier.fillMaxWidth().background(LookPanel, RoundedCornerShape(24.dp)).clickable(enabled = state.busy.isEmpty()) { onSkill(skill.id) }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(skill.title, style = MaterialTheme.typography.titleLarge)
            Text(language.format(R.string.saved_steps_count, (skill.steps.size).toString()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
