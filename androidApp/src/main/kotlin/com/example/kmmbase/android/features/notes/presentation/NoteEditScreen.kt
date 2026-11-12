package com.example.kmmbase.android.features.notes.presentation

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.common.message
import com.example.kmmbase.domain.model.AppError
import com.example.kmmbase.presentation.notes.NoteEditAction
import com.example.kmmbase.presentation.notes.NoteEditEffect
import com.example.kmmbase.presentation.notes.NoteEditStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteEditScreen(store: NoteEditStore, onDone: () -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                NoteEditEffect.Finished -> onDone()
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isExisting) language.text(R.string.ui_156) else language.text(R.string.ui_067)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(language.text(R.string.back)) } },
                actions = {
                    TextButton(
                        onClick = { store.dispatch(NoteEditAction.Save) },
                        enabled = state.canSave,
                    ) { Text(language.text(R.string.ui_109)) }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val error = state.error
            if (error != null) {
                Text(language.systemText(error.message()), color = MaterialTheme.colorScheme.error)
                if (error == AppError.STORAGE && state.isExisting) {
                    TextButton(onClick = { store.dispatch(NoteEditAction.Retry) }) { Text(language.text(R.string.retry)) }
                }
            }
            if (error != AppError.NOT_FOUND) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { store.dispatch(NoteEditAction.TitleChanged(it)) },
                    label = { Text(language.text(R.string.ui_171)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.body,
                    onValueChange = { store.dispatch(NoteEditAction.BodyChanged(it)) },
                    label = { Text(language.text(R.string.ui_143)) },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.isExisting) {
                    OutlinedButton(
                        onClick = { store.dispatch(NoteEditAction.Delete) },
                        enabled = !state.isSaving,
                    ) { Text(language.text(R.string.ui_198)) }
                }
            }
        }
    }
}
