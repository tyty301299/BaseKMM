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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.kmmbase.android.features.studio.screens.*
import com.example.kmmbase.presentation.studio.StudioStore
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.features.looks.components.*
import com.example.kmmbase.presentation.looks.*

@Composable
internal fun LookHomeScreen(store: LookStore, studio: StudioStore, initialTab: Int,
    onCamera: () -> Unit, onRecord: (String) -> Unit, onService: (Int) -> Unit,
    onAdd: () -> Unit, onItem: (String) -> Unit, onOccasion: () -> Unit,
    onTeach: () -> Unit, onSkill: (String) -> Unit, onSettings: () -> Unit) {
    val state by store.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val openService = { onService(if (tab == 2) 0 else 1) }
    LookTheme {
        Scaffold(containerColor = LookBackground, bottomBar = {
            NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(28.dp)).lookGlass()) {
                listOf("Phối đồ" to LookSymbol.OUTFIT, "Tủ đồ" to LookSymbol.WARDROBE, "Học & làm" to LookSymbol.LEARN, "Lịch sử" to LookSymbol.HISTORY).forEachIndexed { index, (title, symbol) ->
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
                        TextButton(onClick = onSettings, modifier = Modifier.lookGlass()) { Text("Tùy chọn") }
                    }
                    when (tab) {
                        2 -> StudioLearningContent(studio, onTeach, onSkill, openService)
                        1 -> StudioWardrobeContent(studio, onAdd, onItem)
                        else -> {
                            Text("AI OUTFITS", style = MaterialTheme.typography.labelLarge)
                            Text("Mặc đúng gu.\nĐúng dịp.", style = MaterialTheme.typography.headlineLarge)
                            LookMannequin(state.preset, Modifier.fillMaxWidth().height(300.dp).background(LookStudio, RoundedCornerShape(28.dp)))
                            LookButton("Phối đồ cho hôm nay", onClick = onOccasion)
                            LookButton("Mở camera thử mẫu", onClick = onCamera)
                            TextButton(onClick = onAdd) { Text("Thêm trang phục") }
                        }
                    }
                }
            }
        }
    }
}
