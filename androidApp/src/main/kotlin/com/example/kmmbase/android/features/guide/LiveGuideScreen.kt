package com.example.kmmbase.android.features.guide

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import coil.compose.AsyncImage
import com.example.kmmbase.android.common.*
import com.example.kmmbase.android.features.looks.screens.ObjectDetectionCorners
import com.example.kmmbase.android.features.looks.screens.BuiltinStepIllustration
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.domain.model.SocketStatus
import com.example.kmmbase.presentation.guide.*
import com.example.kmmbase.presentation.shadow.ShadowStore

private val Accent = Color(0xFFFFD23F)
private val Scrim = Color(0xB3000000)

/**
 * Full-screen camera with the step HUD on top. The learner sees what the camera sees, hears each
 * line through Vietnamese TTS, and the hidden ◀ ▶ controls let the operator rescue a live demo.
 */
@Composable
internal fun LiveGuideScreen(
    skillId: String,
    skillTitle: String,
    frames: CameraFrameSource,
    store: GuideStore,
    onCompleted: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onExit: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    val granted = rememberCameraPermission()
    val completionSessionId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted(completionSessionId, state.skillName.ifBlank { skillTitle }.take(100), state.manualAdvanceUsed)
    }
    var region by remember { mutableStateOf<GarmentRegion?>(null) }
    val detector = remember { GarmentDetector { value, _ -> region = value } }
    val analyzer = remember(detector) { { image: androidx.camera.core.ImageProxy, source: CameraFrameSource -> detector.analyze(image, source) } }
    DisposableEffect(detector) { onDispose { detector.close() } }
    LaunchedEffect(granted, skillId) { detector.setActive(granted, fashionOnly = skillId == "shadow-builtin-gap-ao-v1" || skillId == "shadow-builtin-gap-quan-v1") }

    LaunchedEffect(granted, skillId) {
        if (granted) store.dispatch(GuideAction.Start(skillId))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(frames, Modifier.fillMaxSize(), frameAnalyzer = analyzer)
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(language.text(R.string.ui_049),
                    color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onExit) { Text(language.text(R.string.back), color = Accent) }
            }
        }

        region?.let { ObjectDetectionCorners(it, false) }

        // Long-pressing the HUD reveals the rescue controls, as on the web client.
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Scrim).padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        store.dispatch(GuideAction.ShowDebugControls(!state.debugControls))
                    })
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.skillName.ifBlank { skillTitle }, color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { store.dispatch(GuideAction.Stop); onExit() }) {
                    Text(language.text(R.string.ui_158), color = Accent)
                }
            }

            if (skillTitle.endsWith(" · Demo")) {
                Text("DEMO · Kết quả kiểm tra giả lập", color = Color(0xFFFFB547), style = MaterialTheme.typography.labelMedium)
            }
            val step = state.step
            if (state.completed) {
                Text(language.text(R.string.ui_014), color = Accent, style = MaterialTheme.typography.headlineSmall)
            } else if (step != null) {
                Text(language.format(R.string.step_progress, (step.index + 1).toString(), (step.total).toString()), color = Accent,
                    style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(step.instruction, Modifier.weight(1f), color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (skillId in setOf("shadow-builtin-goi-qua-v1", "shadow-builtin-gap-ao-v1", "shadow-builtin-gap-quan-v1")) {
                        BuiltinStepIllustration(skillId == "shadow-builtin-goi-qua-v1", step.index,
                            Modifier.size(width = 112.dp, height = 92.dp), isPants = skillId == "shadow-builtin-gap-quan-v1")
                    }
                }
                if (step.tip.isNotBlank()) {
                    Text(step.tip, color = Color(0xFFD8DCCF), style = MaterialTheme.typography.bodyMedium)
                }
                if (step.warning.isNotBlank()) {
                    Text(language.format(R.string.warning_detail, (step.warning).toString()), color = Color(0xFFFFB547),
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (step.referenceImageUrl.isNotBlank()) {
                    AsyncImage(
                        model = step.referenceImageUrl,
                        contentDescription = language.text(R.string.ui_261),
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            } else {
                Text(language.text(R.string.ui_213), color = Color.White)
            }

            StatusRow(state)
        }

        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Scrim).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.spokenLine.isNotBlank()) {
                Text(state.spokenLine, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
            if (state.hint.isNotBlank()) {
                Text(state.hint, color = Color(0xFFFFB547), style = MaterialTheme.typography.bodyMedium)
            }
            state.error?.let {
                Text(language.systemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (state.running) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if ((state.step?.index ?: 0) > 0) OutlinedButton(onClick = { store.dispatch(GuideAction.Step(forward = false)) }, enabled = state.connection == SocketStatus.OPEN) { Text("Bước trước") }
                    Button(onClick = { store.dispatch(GuideAction.Step(forward = true)) }, enabled = state.connection == SocketStatus.OPEN,
                        modifier = Modifier.weight(1f)) { Text("Tôi đã xong bước này") }
                }
                Text("Chỉ bấm khi đã làm xong; AI vẫn tự kiểm tra.", color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
            if (state.completed) {
                Button(onClick = { store.dispatch(GuideAction.Stop); onExit() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211C09)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Xong") }
            }
        }
    }
}

/** Connection and judging indicator: yellow means the AI round-trip is in flight or degraded. */
@Composable
private fun StatusRow(state: GuideState) {
    val language = LocalAppLanguage.current
    val (color, label) = when {
        state.connection == SocketStatus.CLOSED -> Color(0xFFFFB547) to language.text(R.string.ui_214)
        state.connection == SocketStatus.CONNECTING -> Color(0xFFFFB547) to language.text(R.string.connecting)
        state.checkStatus == "no_frame" -> Color(0xFFFFB547) to "Chưa lấy được ảnh camera"
        state.checkStatus == "error" -> Color(0xFFFFB547) to "Không kiểm tra được ảnh"
        state.checkStatus == "confirming" -> Accent to "Đang xác nhận · giữ nguyên vài giây"
        state.checkStatus == "not_done" -> Color(0xFFFFB547) to "Chưa xác nhận · giữ rõ vật trong khung"
        state.judging -> Accent to "AI đang kiểm tra…"
        state.running -> Color(0xFF7BD88F) to language.text(R.string.ui_227)
        else -> Color(0xFFADB8A4) to language.text(R.string.ui_155)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
        if (state.latencyMs > 0) {
            Text("${state.latencyMs} ms", color = Color(0xFFADB8A4),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Builds the store with Android TTS and the live camera wired in. */
@Composable
internal fun rememberGuideStore(
    container: AppContainer,
    shadow: ShadowStore,
    frames: CameraFrameSource,
): GuideStore {
    val context = LocalContext.current
    val speaker = remember { VietnameseSpeaker(context) }
    val store = remember { container.guideStore(shadow, frames, speaker) }
    DisposableEffect(Unit) {
        onDispose {
            store.close()
            speaker.shutdown()
        }
    }
    return store
}
