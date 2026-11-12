package com.example.kmmbase.android.features.home.presentation

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.example.kmmbase.android.platform.media.preparePersonPhoto
import com.example.kmmbase.android.platform.media.preparePersonVideoFrame
import com.example.kmmbase.android.platform.media.personVideoDuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.common.LanguageSetting
import com.example.kmmbase.android.R
import com.example.kmmbase.android.features.looks.presentation.components.LookTheme
import com.example.kmmbase.android.features.looks.presentation.components.LookBackground
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
import com.example.kmmbase.android.platform.camera.CameraFrameSource
import com.example.kmmbase.android.platform.media.shadowImageRequest
import com.example.kmmbase.android.features.guide.presentation.LiveGuideScreen
import com.example.kmmbase.android.features.guide.presentation.rememberGuideStore
import com.example.kmmbase.android.features.teach.presentation.LiveTeachScreen
import com.example.kmmbase.android.features.teach.presentation.rememberTeachStore
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.domain.model.DEFAULT_SHADOW_ACCESS_CODE
import com.example.kmmbase.domain.model.DEFAULT_SHADOW_ORIGIN
import com.example.kmmbase.domain.model.ShadowOutfitItem
import com.example.kmmbase.domain.model.WardrobeItem
import com.example.kmmbase.presentation.guide.GuideAction
import com.example.kmmbase.presentation.shadow.*

private val ShadowAccent = Color(0xFFFFD23F)
private val ShadowBackground = Color(0xFF10130F)

@Composable
internal fun HomeScreen(container: AppContainer, store: ShadowStore, initialTab: Int = 0, initialSettings: Boolean = false,
                        onGuideCompleted: (String, String, String, Boolean) -> Unit) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("shadow", Context.MODE_PRIVATE) }
    var endpoint by rememberSaveable {
        mutableStateOf(preferences.getString("endpoint", null) ?: DEFAULT_SHADOW_ORIGIN)
    }
    // Access codes stay in memory; never saved to preferences or instance state.
    var accessCode by remember { mutableStateOf(if (endpoint == DEFAULT_SHADOW_ORIGIN) DEFAULT_SHADOW_ACCESS_CODE else "") }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var showSettings by rememberSaveable { mutableStateOf(initialSettings) }
    var event by rememberSaveable { mutableStateOf("Đi làm") }
    var notes by rememberSaveable { mutableStateOf("") }
    var personUri by rememberSaveable { mutableStateOf<String?>(null) }
    var personJPEG by remember { mutableStateOf<ByteArray?>(null) }
    var readingPhoto by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf(0.0) }
    var frameTime by rememberSaveable { mutableStateOf(0f) }
    var requestedFrameTime by rememberSaveable { mutableStateOf(0f) }
    var photoError by remember { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { frameTime = 0f; requestedFrameTime = 0f; personUri = uri.toString() }
    }
    LaunchedEffect(personUri, requestedFrameTime) {
        val uri = personUri ?: return@LaunchedEffect
        personJPEG = null
        videoDuration = 0.0
        readingPhoto = true
        photoError = false
        store.dispatch(ShadowAction.ClearTryon)
        try {
            val media = Uri.parse(uri)
            val isVideo = context.contentResolver.getType(media)?.startsWith("video/") == true
            val prepared = withContext(Dispatchers.IO) {
                if (isVideo) personVideoDuration(context, media) to preparePersonVideoFrame(context, media, requestedFrameTime.toDouble())
                else 0.0 to preparePersonPhoto(context, media)
            }
            videoDuration = prepared.first
            personJPEG = prepared.second
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { photoError = true }
        finally { readingPhoto = false }
    }
    DisposableEffect(store) { onDispose { store.dispatch(ShadowAction.CancelTryon) } }
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
            Toast.makeText(context, language.text(R.string.browser_error), Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(state.endpoint) {
        if (state.endpoint.isNotEmpty()) {
            preferences.edit().putString("endpoint", state.endpoint).apply()
        }
    }
    // Connect straight away so the demo opens on real skills, not a connect form.
    LaunchedEffect(Unit) {
        if (!state.connected && state.busy.isEmpty()) {
            store.dispatch(ShadowAction.Connect(endpoint, accessCode))
        }
    }
    LookTheme {
        val guide = guideSkill
        if (guide != null) {
            val frames = remember { CameraFrameSource() }
            val guideStore = rememberGuideStore(container, store, frames)
            BackHandler { guideStore.dispatch(GuideAction.Stop); guideSkill = null }
            LiveGuideScreen(guide.first, guide.second, frames, guideStore,
                imageHeaders = store::mediaHeadersFor,
                onCompleted = { sessionId, title, manual -> onGuideCompleted(sessionId, guide.first, title, manual) }) { guideSkill = null }
            return@LookTheme
        }
        if (teaching) {
            val frames = remember { CameraFrameSource() }
            val teachStore = rememberTeachStore(container, store, frames)
            val leave = {
                teaching = false
                store.dispatch(ShadowAction.Refresh) // the new skill should appear in the list
            }
            BackHandler(onBack = leave)
            LiveTeachScreen(frames, teachStore, imageHeaders = store::mediaHeadersFor, onDone = { leave() }, onExit = leave)
            return@LookTheme
        }
        BackHandler(enabled = showSettings) { showSettings = false }
        BackHandler(enabled = state.lesson != null && !showSettings) { store.dispatch(ShadowAction.CloseLesson) }
        Scaffold(
            containerColor = LookBackground,
            bottomBar = {
                if (state.lesson == null && !showSettings) NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Text("01") }, label = { Text(language.text(R.string.learn_tab)) })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Text("02") }, label = { Text(language.text(R.string.wardrobe_tab)) })
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (showSettings) language.text(R.string.settings_title) else "SHADOW", style = MaterialTheme.typography.titleLarge)
                    if (showSettings) {
                        TextButton(onClick = { showSettings = false }) { Text(language.text(R.string.close)) }
                    } else if (state.lesson == null) {
                        TextButton(onClick = { showSettings = true }) { Text(language.text(R.string.settings_title)) }
                    } else {
                        TextButton(onClick = { store.dispatch(ShadowAction.CloseLesson) }) { Text(language.text(R.string.close)) }
                    }
                }
                Text(language.systemText(state.modeLabel), color = ShadowAccent, style = MaterialTheme.typography.labelLarge)
                if (busy && state.connected) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(language.systemText(state.busy))
                }
                if (busy && !state.connected) {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
                        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(language.text(R.string.connecting), style = MaterialTheme.typography.titleMedium)
                                Text(language.text(R.string.connection_wait), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error) }
                val lesson = state.lesson
                if (lesson != null && !showSettings) {
                    LessonContent(state, store) { guideSkill = lesson.id to lesson.title }
                } else {
                    if (showSettings) {
                        Text(language.text(R.string.connection_title), style = MaterialTheme.typography.headlineSmall)
                        Text(language.text(R.string.auto_connect))
                        Text(language.text(if (state.connected) R.string.connected else R.string.not_connected))
                        if (state.connected) Text(state.endpoint, style = MaterialTheme.typography.bodySmall)
                        Text(language.text(R.string.server_hint))
                        OutlinedTextField(endpoint, { endpoint = it; accessCode = "" }, enabled = !busy, label = { Text(language.text(R.string.server_address)) },
                            placeholder = { Text("https://shadow.example.com") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(accessCode, { accessCode = it }, enabled = !busy, label = { Text(language.text(R.string.access_code)) },
                            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            if (state.connected && endpoint.trim().trimEnd('/') == state.endpoint && accessCode.isBlank()) store.dispatch(ShadowAction.Reconnect)
                            else store.dispatch(ShadowAction.Connect(endpoint, accessCode))
                        },
                            enabled = endpoint.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(language.text(if (state.connected) R.string.reconnect else R.string.connect))
                        }
                        Text(language.text(R.string.access_code_hint), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                        LanguageSetting()
                        Text(language.text(R.string.language_content), style = MaterialTheme.typography.bodySmall)
                    }
                    if (!showSettings && !state.connected && !busy) {
                        Button(onClick = { store.dispatch(ShadowAction.Connect(endpoint, accessCode)) }) { Text(language.text(R.string.retry)) }
                        TextButton(onClick = { showSettings = true }) { Text(language.text(R.string.settings_title)) }
                    }
                    if (!showSettings) {
                        if (tab == 0) {
                            Text(language.text(R.string.learn_heading), style = MaterialTheme.typography.headlineLarge)
                            Text(language.text(R.string.learn_description))
                            Button(onClick = { teaching = true }, enabled = state.connected && !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(language.text(R.string.teach_camera))
                            }
                            SectionTitle(language.text(R.string.learned_skills), state.connected && !busy) { store.dispatch(ShadowAction.Refresh) }
                            if (state.skills.isEmpty()) Text(if (state.connected)
                                language.text(R.string.skills_empty)
                                else language.text(R.string.connect_to_skills))
                            state.skills.forEach { skill ->
                                TextButton(onClick = { store.dispatch(ShadowAction.OpenLesson(skill.id)) },
                                    enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(skill.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                        Text(language.format(R.string.step_count, (skill.steps).toString()) + if (skill.teacher.isEmpty()) "" else language.format(R.string.teacher_credit, (skill.teacher).toString()))
                                        if (skill.summary.isNotEmpty()) Text(skill.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider()
                            }
                        } else {
                            Text(language.text(R.string.person_photo_title), style = MaterialTheme.typography.headlineSmall)
                            Text(language.text(R.string.person_photo_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            personJPEG?.let { bytes ->
                                AsyncImage(model = bytes, contentDescription = language.text(R.string.photo_selected),
                                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).clip(RoundedCornerShape(24.dp)))
                            }
                            Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                                enabled = !busy && !readingPhoto, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(language.text(if (personJPEG == null) R.string.choose_person_media else R.string.change_person_photo))
                            }
                            if (videoDuration > 0) {
                                Text(language.text(R.string.video_frame), style = MaterialTheme.typography.titleSmall)
                                Slider(value = frameTime, onValueChange = { frameTime = it },
                                    onValueChangeFinished = { requestedFrameTime = frameTime },
                                    valueRange = 0f..maxOf(0.01f, videoDuration.toFloat() - 0.1f), enabled = !busy && !readingPhoto)
                                Text(language.text(R.string.video_frame_note), style = MaterialTheme.typography.bodySmall)
                            }
                            if (readingPhoto) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (photoError) Text(language.text(R.string.photo_invalid), color = MaterialTheme.colorScheme.error)
                            Text(language.text(R.string.wardrobe_heading), style = MaterialTheme.typography.headlineLarge)
                            Text(language.text(R.string.wardrobe_description))
                            WebButton(language.text(R.string.manage_wardrobe), language.text(R.string.manage_wardrobe_detail),
                                state.connected && !busy) { openWeb("wardrobe") }
                            SectionTitle(language.format(R.string.wardrobe_count_short, (state.wardrobe.size).toString()), state.connected && !busy) { store.dispatch(ShadowAction.Refresh) }
                            if (state.wardrobe.isEmpty()) Text(language.text(R.string.wardrobe_empty))
                            WardrobeGrid(state.wardrobe, mediaHeaders)
                            Text(language.text(R.string.occasion_prompt), style = MaterialTheme.typography.titleMedium)
                            // Vertically flowing choices also fit narrow screens and large system text.
                            listOf("Đi làm", "Đi chơi", "Hẹn hò").forEach { choice ->
                                FilterChip(selected = event == choice, onClick = { event = choice },
                                    enabled = !busy, label = { Text(language.text(when (choice) { "Đi làm" -> R.string.work; "Đi chơi" -> R.string.casual; else -> R.string.date })) })
                            }
                            OutlinedTextField(notes, { notes = it }, label = { Text(language.text(R.string.extra_preferences)) },
                                supportingText = { Text("${notes.length}/300") }, isError = notes.length > 300,
                                enabled = !busy, modifier = Modifier.fillMaxWidth())
                            Button(onClick = { store.dispatch(ShadowAction.Suggest(event, notes)) },
                                enabled = state.connected && state.wardrobe.isNotEmpty() && !busy && notes.length <= 300,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(language.text(R.string.suggest)) }
                            personJPEG?.let { jpeg ->
                                Button(onClick = { store.dispatch(ShadowAction.StylePerson("data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP), event, notes)) },
                                    enabled = state.connected && !busy && !readingPhoto && state.wardrobe.isNotEmpty() && notes.length <= 300,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(language.text(R.string.create_tryon)) }
                                Text(language.text(R.string.person_photo_privacy), style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.tryingOn) TextButton(onClick = { store.dispatch(ShadowAction.CancelTryon) }) { Text(language.text(R.string.cancel)) }
                            state.tryon?.let { result ->
                                Text(language.text(R.string.tryon_result), style = MaterialTheme.typography.headlineSmall)
                                AsyncImage(model = shadowImageRequest(context, result.imageUrl, mediaHeaders), contentDescription = language.text(R.string.tryon_result),
                                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(24.dp)))
                                Text(language.text(if (result.provider == "mock") R.string.tryon_mock else R.string.tryon_ai), style = MaterialTheme.typography.labelLarge)
                                if (result.skippedItemIds.isNotEmpty()) Text(language.text(R.string.tryon_partial))
                                if (result.note.isNotBlank()) Text(result.note, style = MaterialTheme.typography.bodySmall)
                            }
                            state.suggestions?.let { result ->
                                Text(language.format(R.string.suggestions_for, (state.suggestionsEvent).toString()), style = MaterialTheme.typography.titleMedium)
                                Text(language.systemText(state.suggestionsLabel), color = ShadowAccent)
                                result.weather?.takeIf { it.available }?.let {
                                    Text(it.summary, color = Color(0xFF9FD4FF))
                                }
                                if (result.rulesUsed.isNotEmpty()) {
                                    Text(language.systemText("Quy tắc đã học: ") + result.rulesUsed.joinToString("; "),
                                        color = ShadowAccent, style = MaterialTheme.typography.bodySmall)
                                }
                                if (result.outfits.isEmpty()) Text(language.text(R.string.outfits_empty))
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
}

@Composable
private fun LessonContent(state: ShadowState, store: ShadowStore, onGuide: () -> Unit) {
    val language = LocalAppLanguage.current
    val lesson = state.lesson ?: return
    var index by rememberSaveable(lesson.id) { mutableStateOf(0) }
    var question by rememberSaveable(lesson.id) { mutableStateOf("") }
    val busy = state.busy.isNotEmpty()
    Text(lesson.title, style = MaterialTheme.typography.headlineLarge)
    Text(language.text(R.string.guide_description))
    Button(onClick = onGuide, enabled = !busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(language.text(R.string.start_guide)) }
    if (lesson.steps.isNotEmpty()) {
        val step = lesson.steps[index.coerceIn(0, lesson.steps.lastIndex)]
        Text(language.format(R.string.step_progress_small, (index + 1).toString(), (lesson.steps.size).toString()), color = ShadowAccent)
        Text(step.instruction, style = MaterialTheme.typography.headlineSmall)
        if (step.tip.isNotEmpty()) Text(step.tip)
        if (step.warning.isNotEmpty()) Text(language.format(R.string.warning_detail, (step.warning).toString()), color = Color(0xFFFFB547))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { index--; store.dispatch(ShadowAction.ClearAnswer) }, enabled = index > 0 && !busy) { Text(language.text(R.string.previous_step)) }
            OutlinedButton(onClick = { index++; store.dispatch(ShadowAction.ClearAnswer) }, enabled = index < lesson.steps.lastIndex && !busy) { Text(language.text(R.string.next_step)) }
        }
        HorizontalDivider()
        Text(language.text(R.string.ask_step), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(question, { question = it }, label = { Text(language.text(R.string.question_hint)) },
            supportingText = { Text("${question.length}/500") }, isError = question.length > 500,
            enabled = !busy, modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.dispatch(ShadowAction.Ask(step.id, question)) },
            enabled = question.isNotBlank() && question.length <= 500 && !busy,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(language.text(R.string.send_question)) }
    }
    state.answer?.let { answer ->
        Text(language.systemText(state.answerLabel), color = ShadowAccent)
        Text(state.answerQuestion, style = MaterialTheme.typography.titleMedium)
        Text(answer.text)
    }
}

/** Wardrobe thumbnails in rows of three; the server supplies real garment photos. */
@Composable
private fun WardrobeGrid(items: List<WardrobeItem>, headers: Map<String, String>) {
    val language = LocalAppLanguage.current
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
    val language = LocalAppLanguage.current
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
    val language = LocalAppLanguage.current
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = refresh, enabled = enabled) { Text(language.text(R.string.refresh)) }
    }
}

@Composable
private fun WebButton(title: String, detail: String, enabled: Boolean, action: () -> Unit) {
    val language = LocalAppLanguage.current
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
