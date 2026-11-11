package com.example.kmmbase.android.features.home

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.kmmbase.android.common.CameraFrameSource
import com.example.kmmbase.android.common.shadowImageRequest
import com.example.kmmbase.android.features.guide.LiveGuideScreen
import com.example.kmmbase.android.features.guide.rememberGuideStore
import com.example.kmmbase.android.features.teach.LiveTeachScreen
import com.example.kmmbase.android.features.teach.rememberTeachStore
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.domain.model.DEFAULT_SHADOW_ACCESS_CODE
import com.example.kmmbase.domain.model.DEFAULT_SHADOW_ORIGIN
import com.example.kmmbase.domain.model.ShadowOutfitItem
import com.example.kmmbase.domain.model.WardrobeItem
import com.example.kmmbase.presentation.guide.GuideAction
import com.example.kmmbase.presentation.shadow.*

private val ShadowAccent = Color(0xFFFFD23F)
private val ShadowBackground = Color(0xFF0B0B0F)

@Composable
internal fun HomeScreen(container: AppContainer, store: ShadowStore, initialTab: Int = 0) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("shadow", Context.MODE_PRIVATE) }
    var endpoint by rememberSaveable {
        mutableStateOf(preferences.getString("endpoint", null) ?: DEFAULT_SHADOW_ORIGIN)
    }
    // Access codes stay in memory; never saved to preferences or instance state.
    var accessCode by remember { mutableStateOf(DEFAULT_SHADOW_ACCESS_CODE) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var event by rememberSaveable { mutableStateOf("Đi làm") }
    var notes by rememberSaveable { mutableStateOf("") }
    // Non-null while the live camera guide is on screen: skill id to display name.
    var guideSkill by remember { mutableStateOf<Pair<String, String>?>(null) }
    var teaching by remember { mutableStateOf(false) }
    // Re-read on reconnect: server media is gated, so image requests need the access header.
    val mediaHeaders = remember(state.connected, state.endpoint) { store.mediaHeaders() }
    val uriHandler = LocalUriHandler.current
    val busy = state.busy.isNotEmpty()
    val openWeb: (String) -> Unit = { route ->
        try {
            uriHandler.openUri("${state.endpoint}/#/$route")
        } catch (_: Exception) {
            Toast.makeText(context, "Không mở được trình duyệt trên thiết bị này.", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.endpoint) {
        if (state.endpoint.isNotEmpty()) {
            preferences.edit().putString("endpoint", state.endpoint).apply()
            accessCode = ""
            showConnection = false
        }
    }
    // Connect straight away so the demo opens on real skills, not a connect form.
    LaunchedEffect(Unit) {
        if (!state.connected && state.busy.isEmpty()) {
            store.dispatch(ShadowAction.Connect(endpoint, accessCode))
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = ShadowAccent, onPrimary = Color(0xFF1B1500),
        background = ShadowBackground, surface = Color(0xFF16161E),
    )) {
        val guide = guideSkill
        if (guide != null) {
            val frames = remember { CameraFrameSource() }
            val guideStore = rememberGuideStore(container, store, frames)
            BackHandler { guideStore.dispatch(GuideAction.Stop); guideSkill = null }
            LiveGuideScreen(guide.first, guide.second, frames, guideStore) { guideSkill = null }
            return@MaterialTheme
        }
        if (teaching) {
            val frames = remember { CameraFrameSource() }
            val teachStore = rememberTeachStore(container, store, frames)
            val leave = {
                teaching = false
                store.dispatch(ShadowAction.Refresh) // the new skill should appear in the list
            }
            BackHandler(onBack = leave)
            LiveTeachScreen(frames, teachStore, onDone = { leave() }, onExit = leave)
            return@MaterialTheme
        }
        BackHandler(enabled = state.lesson != null) { store.dispatch(ShadowAction.CloseLesson) }
        Scaffold(
            containerColor = ShadowBackground,
            bottomBar = {
                if (state.lesson == null) NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Text("01") }, label = { Text("Học & làm theo") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Text("02") }, label = { Text("Tủ đồ") })
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SHADOW", style = MaterialTheme.typography.titleLarge)
                    if (state.lesson == null) {
                        TextButton(onClick = { showConnection = !showConnection }) { Text("Kết nối") }
                    } else {
                        TextButton(onClick = { store.dispatch(ShadowAction.CloseLesson) }) { Text("Đóng") }
                    }
                }
                Text(state.modeLabel, color = ShadowAccent, style = MaterialTheme.typography.labelLarge)
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.busy)
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val lesson = state.lesson
                if (lesson != null) {
                    LessonContent(state, store) { guideSkill = lesson.id to lesson.title }
                } else {
                    if (showConnection || !state.connected) {
                        Text("Kết nối với SHADOW", style = MaterialTheme.typography.headlineSmall)
                        Text("Dùng địa chỉ HTTPS của nhóm để truy cập kỹ năng và tủ đồ.")
                        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Địa chỉ máy chủ") },
                            placeholder = { Text("https://shadow.example.com") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(accessCode, { accessCode = it }, label = { Text("Mã truy cập (nếu có)") },
                            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { store.dispatch(ShadowAction.Connect(endpoint, accessCode)) },
                            enabled = endpoint.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Kết nối máy chủ")
                        }
                    }
                    if (tab == 0) {
                        Text("Bạn làm mẫu.\nSHADOW học cùng.", style = MaterialTheme.typography.headlineLarge)
                        Text("Ghi lại cách bạn làm, xem các bước AI đã học và hỏi khi cần hướng dẫn.")
                        Button(onClick = { teaching = true }, enabled = state.connected && !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text("Dạy kỹ năng bằng camera")
                        }
                        SectionTitle("Kỹ năng đã học", state.connected && !busy) { store.dispatch(ShadowAction.Refresh) }
                        if (state.skills.isEmpty()) Text(if (state.connected)
                            "Chưa có kỹ năng. Hãy dạy SHADOW thao tác đầu tiên của bạn."
                            else "Kết nối máy chủ để xem các kỹ năng đã học.")
                        state.skills.forEach { skill ->
                            TextButton(onClick = { store.dispatch(ShadowAction.OpenLesson(skill.id)) },
                                enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(skill.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text("${skill.steps} bước" + if (skill.teacher.isEmpty()) "" else " · Học từ ${skill.teacher}")
                                    if (skill.summary.isNotEmpty()) Text(skill.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    } else {
                        Text("Hôm nay,\nbạn muốn mặc gì?", style = MaterialTheme.typography.headlineLarge)
                        Text("Gợi ý từ những món đồ có trong tủ của bạn, phù hợp với dịp sắp tới.")
                        WebButton("Thêm ảnh & quản lý tủ đồ", "Mở bản web · thêm ảnh đồ thật, chỉnh nhãn và mặc thử",
                            state.connected && !busy) { openWeb("wardrobe") }
                        SectionTitle("Tủ đồ · ${state.wardrobe.size}", state.connected && !busy) { store.dispatch(ShadowAction.Refresh) }
                        if (state.wardrobe.isEmpty()) Text("Thêm ảnh trang phục của bạn trên bản web, rồi tải lại để bắt đầu phối đồ.")
                        WardrobeGrid(state.wardrobe, mediaHeaders)
                        Text("Bạn sắp đi đâu?", style = MaterialTheme.typography.titleMedium)
                        // Vertically flowing choices also fit narrow screens and large system text.
                        listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
                            FilterChip(selected = event == choice, onClick = { event = choice },
                                enabled = !busy, label = { Text(choice) })
                        }
                        OutlinedTextField(notes, { notes = it }, label = { Text("Mong muốn thêm (không bắt buộc)") },
                            supportingText = { Text("${notes.length}/300") }, isError = notes.length > 300,
                            enabled = !busy, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { store.dispatch(ShadowAction.Suggest(event, notes)) },
                            enabled = state.connected && state.wardrobe.isNotEmpty() && !busy && notes.length <= 300,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Gợi ý phối đồ") }
                        state.suggestions?.let { result ->
                            Text("Gợi ý cho: ${state.suggestionsEvent}", style = MaterialTheme.typography.titleMedium)
                            Text(state.suggestionsLabel, color = ShadowAccent)
                            result.weather?.takeIf { it.available }?.let {
                                Text(it.summary, color = Color(0xFF9FD4FF))
                            }
                            if (result.rulesUsed.isNotEmpty()) {
                                Text("Quy tắc đã học: ${result.rulesUsed.joinToString("; ")}",
                                    color = ShadowAccent, style = MaterialTheme.typography.bodySmall)
                            }
                            if (result.outfits.isEmpty()) Text("Chưa có bộ phối phù hợp. Thử đổi dịp hoặc thêm trang phục.")
                            result.outfits.forEach { outfit ->
                                Text(outfit.title, style = MaterialTheme.typography.headlineSmall)
                                OutfitRow(outfit.items, mediaHeaders)
                                Text(outfit.reason)
                                if (outfit.tips.isNotEmpty()) Text(outfit.tips, color = Color(0xFFD8DCCF))
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonContent(state: ShadowState, store: ShadowStore, onGuide: () -> Unit) {
    val lesson = state.lesson ?: return
    var index by rememberSaveable(lesson.id) { mutableStateOf(0) }
    var question by rememberSaveable(lesson.id) { mutableStateOf("") }
    val busy = state.busy.isNotEmpty()
    Text(lesson.title, style = MaterialTheme.typography.headlineLarge)
    Text("SHADOW nhìn qua camera và hướng dẫn bạn từng bước bằng giọng nói.")
    Button(onClick = onGuide, enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Bắt đầu hướng dẫn qua camera") }
    if (lesson.steps.isNotEmpty()) {
        val step = lesson.steps[index.coerceIn(0, lesson.steps.lastIndex)]
        Text("Bước ${index + 1}/${lesson.steps.size}", color = ShadowAccent)
        Text(step.instruction, style = MaterialTheme.typography.headlineSmall)
        if (step.tip.isNotEmpty()) Text(step.tip)
        if (step.warning.isNotEmpty()) Text("Lưu ý: ${step.warning}", color = Color(0xFFFFB547))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { index--; store.dispatch(ShadowAction.ClearAnswer) }, enabled = index > 0 && !busy) { Text("Bước trước") }
            OutlinedButton(onClick = { index++; store.dispatch(ShadowAction.ClearAnswer) }, enabled = index < lesson.steps.lastIndex && !busy) { Text("Bước sau") }
        }
        HorizontalDivider()
        Text("Hỏi SHADOW về bước này", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(question, { question = it }, label = { Text("Bạn đang vướng ở đâu?") },
            supportingText = { Text("${question.length}/500") }, isError = question.length > 500,
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.dispatch(ShadowAction.Ask(step.id, question)) },
            enabled = question.isNotBlank() && question.length <= 500 && !busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text("Gửi câu hỏi") }
    }
    state.answer?.let { answer ->
        Text(state.answerLabel, color = ShadowAccent)
        Text(state.answerQuestion, style = MaterialTheme.typography.titleMedium)
        Text(answer.text)
    }
}

/** Wardrobe thumbnails in rows of three; the server supplies real garment photos. */
@Composable
private fun WardrobeGrid(items: List<WardrobeItem>, headers: Map<String, String>) {
    val context = LocalContext.current
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { item ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = shadowImageRequest(context, item.imageUrl, headers),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(110.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun OutfitRow(items: List<ShadowOutfitItem>, headers: Map<String, String>) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = shadowImageRequest(context, item.imageUrl, headers),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
                Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, enabled: Boolean, refresh: () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = refresh, enabled = enabled) { Text("Tải lại") }
    }
}

@Composable
private fun WebButton(title: String, detail: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
