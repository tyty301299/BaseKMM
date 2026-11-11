package com.example.kmmbase.android.features.studio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.studio.*

@Composable
internal fun StudioWardrobeContent(store: StudioStore, onAdd: () -> Unit, onItem: (String) -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    Text(language.text(R.string.ui_181), style = MaterialTheme.typography.headlineLarge)
    Text(language.text(R.string.ui_174), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error); TextButton(onClick = { store.dispatch(StudioAction.Retry) }) { Text(language.text(R.string.refresh)) } }
    if (!state.loading && state.data.items.isEmpty()) {
        Text(language.text(R.string.ui_182), style = MaterialTheme.typography.headlineSmall)
        Text(language.text(R.string.ui_162))
    }
    Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("all", R.string.filter_all, LookSymbol.OUTFIT), Triple("Áo", R.string.filter_tops, LookSymbol.WARDROBE), Triple("Áo khoác", R.string.filter_jackets, LookSymbol.WARDROBE)).forEach { (key, label, symbol) ->
                val selected = state.wardrobeFilter == key
                FilterChip(selected = selected, onClick = { store.dispatch(StudioAction.WardrobeFilter(key)) },
                    enabled = state.busy.isEmpty(), label = { Text(language.text(label)) },
                    leadingIcon = { LookIcon(symbol, if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = RoundedCornerShape(24.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    if (state.data.items.isNotEmpty() && state.filteredItems.isEmpty()) Text(language.text(R.string.ui_040))
    state.filteredItems.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            row.forEach { item ->
                Column(Modifier.weight(1f).clickable(enabled = state.busy.isEmpty()) { onItem(item.id) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioGarmentImage(item.preset, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)))
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(language.format(R.string.sample_category, (item.category).toString()), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    LookButton(language.text(R.string.add_garment), state.busy.isEmpty(), onAdd)
}
