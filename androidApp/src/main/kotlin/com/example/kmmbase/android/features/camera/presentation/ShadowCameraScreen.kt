package com.example.kmmbase.android.features.camera.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kmmbase.android.platform.camera.*
import com.example.kmmbase.android.platform.speech.VietnameseListener
import com.example.kmmbase.android.platform.speech.VietnameseSpeaker
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.domain.model.DEFAULT_SHADOW_ORIGIN
import com.example.kmmbase.domain.repository.ConversationLink
import com.example.kmmbase.presentation.shadow.ShadowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CameraAccent = Color(0xFFFFD23F)

/** Native CameraX preview and Compose chrome. All chat data comes from the shared API store. */
@Composable
internal fun ShadowCameraScreen(container: AppContainer, shadow: ShadowStore,
    onWardrobe: () -> Unit, onLessons: () -> Unit, onAction: (ConversationLink) -> Unit,
    onConnectionChanged: (String, String) -> Unit) {
    val context = LocalContext.current
    val gps = com.example.kmmbase.android.platform.location.rememberStylingLocation(container)
    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val oldStatus = bars?.isAppearanceLightStatusBars
        val oldNavigation = bars?.isAppearanceLightNavigationBars
        val oldContrast = if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced else null
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = false
        if (Build.VERSION.SDK_INT >= 29) window?.isNavigationBarContrastEnforced = false
        onDispose {
            if (oldStatus != null) bars?.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) bars?.isAppearanceLightNavigationBars = oldNavigation
            if (Build.VERSION.SDK_INT >= 29 && oldContrast != null) window?.isNavigationBarContrastEnforced = oldContrast
        }
    }
    val preferences = remember { context.getSharedPreferences("shadow", 0) }
    val store = remember(container, shadow) { container.conversationStore(shadow) }
    val state by store.state.collectAsState()
    val remote by shadow.state.collectAsState()
    var conversationEndpoint by remember { mutableStateOf(remote.endpoint) }
    LaunchedEffect(remote.endpoint) {
        if (conversationEndpoint != remote.endpoint) { store.clear(); conversationEndpoint = remote.endpoint }
    }
    val frames = remember { CameraFrameSource() }
    val granted = rememberCameraPermission()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var cameraEnabled by rememberSaveable { mutableStateOf(true) }
    var front by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    var spokenTurn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureJob by remember { mutableStateOf<Job?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val speaker = remember { lazy { VietnameseSpeaker(context) } }
    fun stopSpeaking() { if (speaker.isInitialized()) speaker.value.stop() }
    val submit: () -> Unit = {
        if (text.isNotBlank() && !state.busy && !capturing) {
            val prompt = text; text = ""; listening = false; notice = null; keyboard?.hide()
            capturing = true
            captureJob = scope.launch {
                try {
                    val image = if (granted && cameraEnabled && active && !settings)
                        withContext(Dispatchers.Default) { frames.capture(640) } else null
                    store.send(prompt, image)
                } finally { capturing = false }
            }
        }
    }
    val latestSubmit by rememberUpdatedState(submit)
    val listener = remember { lazy {
        VietnameseListener(context, { android.os.SystemClock.elapsedRealtime() },
            { _, _, sentence ->
                if (listening) { text = sentence; spokenTurn = true; listening = false; latestSubmit() }
            }, { if (listening) text = it }, {
                listening = false; notice = "Nhập câu hỏi nếu thiết bị chưa hỗ trợ nhận giọng nói tiếng Việt."
            })
    } }
    fun stopListening() { if (listener.isInitialized()) listener.value.stop() }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) { stopSpeaking(); listening = true }
        else notice = "Cho phép micro trong Cài đặt để nói chuyện. Bạn vẫn có thể nhập câu hỏi."
    }
    LaunchedEffect(listening, active, settings) {
        if (listening && active && !settings) listener.value.start() else stopListening()
    }
    LaunchedEffect(state.busy, state.reply, active) {
        if (active && !settings && !state.busy && spokenTurn && state.reply.isNotBlank() && state.error == null) {
            spokenTurn = false; speaker.value.speak(state.reply)
        }
    }
    DisposableEffect(lifecycle, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) active = true
            if (event == Lifecycle.Event.ON_STOP) {
                active = false; listening = false; spokenTurn = false; captureJob?.cancel(); stopListening(); stopSpeaking(); store.cancel()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); if (listener.isInitialized()) listener.value.shutdown(); if (speaker.isInitialized()) speaker.value.shutdown(); store.close() }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(state.turns.size, state.reply) { scroll.animateScrollTo(scroll.maxValue) }
    fun openSettings() {
        listening = false; spokenTurn = false; captureJob?.cancel(); stopSpeaking(); store.cancel()
        endpoint = preferences.getString("endpoint", DEFAULT_SHADOW_ORIGIN) ?: DEFAULT_SHADOW_ORIGIN
        code = preferences.getString("access_code", "") ?: ""
        settingsError = null; settings = true
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF111512))) {
        if (granted && cameraEnabled && active && !settings) CameraPreview(frames, Modifier.fillMaxSize(), front)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .54f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .82f)))))
        if (!granted || !cameraEnabled) Column(Modifier.align(Alignment.Center).padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CameraGlyph("camera", Color.White.copy(alpha = .65f), Modifier.size(38.dp))
            Spacer(Modifier.height(16.dp))
            Text(if (!granted) "Cho phép camera để bắt đầu" else "Camera đang tắt", color = Color.White)
            TextButton(onClick = {
                if (granted) cameraEnabled = true else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text(if (granted) "Bật camera" else "Mở cài đặt", color = CameraAccent) }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SHADOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { listening = false; onWardrobe() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Tủ đồ", color = Color.White, fontSize = 13.sp)
                }
                TextButton(onClick = { listening = false; onLessons() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Bài đã học", color = Color.White, fontSize = 13.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = gps.request, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(gps.label, color = CameraAccent, fontSize = 11.sp) }
                Box(Modifier.size(6.dp).background(if (remote.connected) CameraAccent else Color.Gray, CircleShape))
                Text(if (remote.connected) "Đã kết nối" else if (remote.busy.isNotEmpty()) "Đang kết nối…" else "Chưa kết nối", color = Color.White.copy(alpha = .7f), fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(scroll).padding(end = 42.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.turns.isEmpty()) Text("Chào bạn. Hôm nay bạn muốn mình giúp gì?", color = Color.White, fontSize = 20.sp, lineHeight = 28.sp)
                val completed = if (state.busy || state.error != null) state.turns else state.turns.dropLastWhile { it.role == "assistant" }
                completed.takeLast(5).forEach { turn ->
                    Text(turn.text, color = if (turn.role == "user") Color.White.copy(alpha = .63f) else Color.White,
                        fontSize = if (turn.role == "user") 14.sp else 18.sp, lineHeight = 25.sp)
                }
                if (state.reply.isNotEmpty()) Text(state.reply, color = Color.White, fontSize = 18.sp, lineHeight = 25.sp)
            }
            state.links.forEach { link ->
                if (link.route in setOf("wardrobe", "stylist", "tryon-live", "tryon-photo", "teach", "home", "guide"))
                    TextButton(onClick = { onAction(link) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("${link.label}  →", color = CameraAccent) }
            }
            Spacer(Modifier.height(14.dp))
            val status = state.error ?: notice ?: remote.error ?: when {
                state.busy || capturing -> "Đang phân tích yêu cầu…"
                listening -> "Đang nghe bạn nói…"
                !remote.connected -> "Mở Cài đặt để kiểm tra kết nối."
                else -> "Thử áo, trò chuyện, học hoặc dạy một kỹ năng."
            }
            Text(status, color = if (state.error != null || remote.error != null) Color(0xFFFFCAA8) else Color.White.copy(alpha = .68f), fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraControl(if (listening) "stop" else "mic", if (listening) "Tắt micro" else "Bật micro", highlighted = listening) {
                    if (listening) { listening = false; stopListening() }
                    else if (!state.busy) {
                        stopSpeaking()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listening = true
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                BasicTextField(value = text, onValueChange = { text = it.take(500); spokenTurn = false }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(CameraAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Câu hỏi" }
                        .border(1.dp, Color.White.copy(alpha = .25f), CircleShape).background(Color.Black.copy(alpha = .12f), CircleShape).padding(horizontal = 17.dp, vertical = 14.dp),
                    decorationBox = { inner -> if (text.isEmpty()) Text("Hỏi Shadow…", color = Color.White.copy(alpha = .55f), fontSize = 15.sp); inner() })
                CameraControl(if (state.busy || capturing) "stop" else "send", if (state.busy || capturing) "Dừng phản hồi" else "Gửi câu hỏi", highlighted = true) {
                    if (state.busy || capturing) { spokenTurn = false; captureJob?.cancel(); store.cancel() } else submit()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(top = 100.dp, end = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CameraControl("camera", if (cameraEnabled) "Tắt camera" else "Bật camera") { cameraEnabled = !cameraEnabled }
            CameraControl("flip", "Đổi camera trước sau") { front = !front }
            CameraControl("settings", "Cài đặt kết nối", action = ::openSettings)
        }
    }
    if (settings) AlertDialog(onDismissRequest = { code = ""; settings = false }, title = { Text("Kết nối SHADOW") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Đổi địa chỉ máy chủ khi cần. Chạy local dùng http://localhost:8001.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("Base URL") }, singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text("Mã truy cập (nếu có)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = {
            runCatching { container.normalizeEndpoint(endpoint) }.onSuccess { normalized ->
                preferences.edit().putString("endpoint", normalized).putString("access_code", code.trim()).apply()
                store.clear(); onConnectionChanged(normalized, code.trim()); code = ""; settings = false
            }.onFailure { settingsError = "Dùng HTTPS hoặc http://localhost:8001, không kèm đường dẫn." }
        }) { Text("Lưu & kết nối") } }, dismissButton = { TextButton(onClick = { code = ""; settings = false }) { Text("Hủy") } })
}

@Composable
private fun CameraControl(icon: String, label: String, highlighted: Boolean = false, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(44.dp).background(if (highlighted) CameraAccent else Color.Black.copy(alpha = .24f), CircleShape)
        .border(1.dp, if (highlighted) Color.Transparent else Color.White.copy(alpha = .24f), CircleShape).semantics { contentDescription = label }) {
        CameraGlyph(icon, if (highlighted) Color(0xFF171B14) else Color.White, Modifier.size(21.dp))
    }
}

@Composable
private fun CameraGlyph(name: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = Stroke(1.7f * u, cap = StrokeCap.Round)
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x * u, y * u), Offset(x2 * u, y2 * u), 1.7f * u, StrokeCap.Round)
        when (name) {
            "send" -> { line(12f, 20f, 12f, 4f); line(5f, 11f, 12f, 4f); line(19f, 11f, 12f, 4f) }
            "stop" -> drawRoundRect(color, Offset(6*u, 6*u), Size(12*u, 12*u), CornerRadius(2*u))
            "mic" -> {
                drawRoundRect(color, Offset(9*u, 2*u), Size(6*u, 13*u), CornerRadius(3*u), style = stroke)
                drawArc(color, 0f, 180f, false, Offset(5*u, 7*u), Size(14*u, 12*u), style = stroke)
                line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f)
            }
            "camera" -> {
                drawRoundRect(color, Offset(2*u, 6*u), Size(20*u, 15*u), CornerRadius(3*u), style = stroke)
                drawCircle(color, 4*u, Offset(12*u, 13*u), style = stroke)
                line(7f, 6f, 9f, 3f); line(9f, 3f, 15f, 3f); line(15f, 3f, 17f, 6f)
            }
            "flip" -> {
                drawArc(color, -150f, 135f, false, Offset(4*u, 4*u), Size(16*u, 16*u), style = stroke)
                drawArc(color, 30f, 135f, false, Offset(4*u, 4*u), Size(16*u, 16*u), style = stroke)
                line(20f, 2f, 20f, 8f); line(20f, 8f, 14f, 8f)
                line(4f, 22f, 4f, 16f); line(4f, 16f, 10f, 16f)
            }
            else -> {
                drawCircle(color, 7*u, center, style = stroke); drawCircle(color, 2.5f*u, center, style = stroke)
                repeat(8) { index ->
                    val angle = index * kotlin.math.PI / 4
                    val x = kotlin.math.cos(angle).toFloat(); val y = kotlin.math.sin(angle).toFloat()
                    line(12+x*7, 12+y*7, 12+x*10, 12+y*10)
                }
            }
        }
    }
}
