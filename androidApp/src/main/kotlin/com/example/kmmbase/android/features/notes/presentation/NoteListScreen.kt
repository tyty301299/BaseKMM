package com.example.kmmbase.android.features.notes.presentation

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.common.message
import com.example.kmmbase.presentation.notes.NoteListAction
import com.example.kmmbase.presentation.notes.NoteListStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteListScreen(
    store: NoteListStore,
    onOpenNote: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(language.text(R.string.ui_066)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(language.text(R.string.back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenNote(null) }) { Text("+") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val error = state.error
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(language.systemText(error.message()))
                    Button(onClick = { store.dispatch(NoteListAction.Retry) }) { Text(language.text(R.string.retry)) }
                }
                state.notes.isEmpty() -> Text(language.text(R.string.ui_037), Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(state.notes, key = { it.id }) { note ->
                        ListItem(
                            headlineContent = { Text(note.title) },
                            supportingContent = { Text(note.body, maxLines = 1) },
                            trailingContent = {
                                TextButton(onClick = { store.dispatch(NoteListAction.Delete(note.id)) }) {
                                    Text(language.text(R.string.ui_197))
                                }
                            },
                            modifier = Modifier.clickable { onOpenNote(note.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
