package com.example.kmmbase.android.features.looks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.kmmbase.android.R
import com.example.kmmbase.android.common.ConnectionStatus
import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.presentation.shadow.ShadowState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.kmmbase.android.features.studio.screens.*
import com.example.kmmbase.presentation.studio.StudioStore
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookHomeScreen(store: LookStore, studio: StudioStore, remote: ShadowState, initialTab: Int,
    onCamera: () -> Unit, onRecord: (String) -> Unit, onService: (Int) -> Unit,
    onAdd: () -> Unit, onItem: (String) -> Unit, onOccasion: () -> Unit,
    onTeach: () -> Unit, onSkill: (String) -> Unit, onSettings: () -> Unit, onReconnect: () -> Unit) {
    val language = LocalAppLanguage.current
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val openService = { onService(if (tab == 2) 0 else 1) }
    LookTheme {
        Scaffold(containerColor = LookBackground, bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(28.dp))) {
                listOf(language.text(R.string.outfit_tab) to LookSymbol.OUTFIT, language.text(R.string.wardrobe_tab) to LookSymbol.WARDROBE, language.text(R.string.learn_tab) to LookSymbol.LEARN, language.text(R.string.history_tab) to LookSymbol.HISTORY).forEachIndexed { index, (title, symbol) ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { LookIcon(symbol, if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }, label = { Text(title) })
                }
            }
        }) { padding ->
            if (tab == 3) {
                LookHistoryScreen(store, studio, onRecord, onCamera, onSkill, Modifier.padding(padding))
            } else {
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SHADOW", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onSettings, modifier = Modifier.lookGlass()) { Text(language.text(R.string.settings_title)) }
                    }
                    ConnectionStatus(remote, onReconnect, onSettings)
                    when (tab) {
                        2 -> StudioLearningContent(studio, onTeach, onSkill, openService)
                        1 -> StudioWardrobeContent(studio, onAdd, onItem)
                        else -> {
                            Text("AI OUTFITS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(language.text(R.string.outfit_heading), style = MaterialTheme.typography.headlineLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("coat" to R.string.sample_coat, "blouse" to R.string.sample_blouse).forEach { (preset, title) ->
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        StudioGarmentImage(preset, Modifier.fillMaxWidth().aspectRatio(0.82f).clip(RoundedCornerShape(24.dp)))
                                        Text(language.text(title), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            LookButton(language.text(R.string.outfit_today), onClick = openService)
                            Text(language.text(R.string.quick_start), style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = openService, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(language.text(R.string.person_photo_title)) }
                                OutlinedButton(onClick = onAdd, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(language.text(R.string.add_garment)) }
                            }
                            Card(onClick = openService, shape = MaterialTheme.shapes.large) {
                                ListItem(headlineContent = { Text(language.text(R.string.live_service)) },
                                    supportingContent = { Text(language.text(R.string.live_service_detail)) },
                                    trailingContent = { Text("↗") })
                            }
                            TextButton(onClick = onOccasion) { Text(language.text(R.string.sample_flow)) }
                            Text(language.text(R.string.sample_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
