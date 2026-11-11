package com.example.kmmbase.android.features.teach

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.kmmbase.android.common.*
import com.example.kmmbase.presentation.teach.*

private val Accent = Color(0xFFFFD23F)

/**
 * Teaching flow: record a demonstration, let the compiler turn it into steps, review, save.
 *
 * The server takes keyframes plus a timestamped transcript, never a video file, so recording here
 * means sampling frames and listening for Vietnamese sentences at the same time.
 */
@Composable
internal fun LiveTeachScreen(
    frames: CameraFrameSource,
    store: TeachStore,
    onDone: (skillId: String) -> Unit,
    onExit: () -> Unit,
) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val cameraGranted = rememberCameraPermission()
    var micGranted by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
        if (!it) store.dispatch(TeachAction.SpeechAvailable(false))
    }
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val listener = remember {
        VietnameseListener(
            context = context,
            elapsedMs = { store.currentState.elapsedMs },
            onSentence = { t0, t1, text -> store.dispatch(TeachAction.Sentence(t0, t1, text)) },
            onPartial = { store.dispatch(TeachAction.PartialSentence(it)) },
            onUnavailable = { store.dispatch(TeachAction.SpeechAvailable(false)) },
        )
    }
    DisposableEffect(Unit) {
        store.dispatch(TeachAction.SpeechAvailable(listener.available))
        onDispose { listener.release() }
    }
    LaunchedEffect(state.phase) {
        if (state.phase == TeachPhase.RECORDING && micGranted) listener.start() else listener.stop()
    }
    LaunchedEffect(state.savedSkillId) {
        state.savedSkillId?.let(onDone)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Dạy kỹ năng mới", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onExit) { Text("Đóng", color = Accent) }
        }

        when (state.phase) {
            TeachPhase.RECORDING, TeachPhase.IDLE -> RecordStage(frames, store, state, cameraGranted)
            TeachPhase.COMPILING -> CompileStage(store, state)
            TeachPhase.REVIEW -> ReviewStage(store, state)
            TeachPhase.SAVED -> Text("Đã lưu kỹ năng.", color = Accent,
                style = MaterialTheme.typography.headlineSmall)
        }

        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RecordStage(
    frames: CameraFrameSource,
    store: TeachStore,
    state: TeachState,
    cameraGranted: Boolean,
) {
    if (cameraGranted) {
        CameraPreview(frames, Modifier.fillMaxWidth().height(260.dp))
    } else {
        Text("Cần quyền camera để ghi lại thao tác mẫu.")
    }

    if (state.phase == TeachPhase.RECORDING) {
        Text("Đang ghi · ${state.elapsedMs / 1000}s · ${state.frameCount} khung hình",
            color = Accent, style = MaterialTheme.typography.titleMedium)
        Text("Vừa làm vừa nói: \"đầu tiên…\", \"nhớ là…\", \"đừng…\"")
        if (state.liveTranscript.isNotBlank()) {
            Text(state.liveTranscript, color = Color(0xFFADB8A4))
        }
        state.lines.takeLast(3).forEach { Text("· ${it.text}", style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { store.dispatch(TeachAction.StopRecording) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Dừng ghi") }
    } else {
        if (!state.speechAvailable) {
            Text("Thiết bị chưa nhận giọng nói tiếng Việt — hãy gõ ghi chú bên dưới, AI vẫn học được.",
                color = Color(0xFFFFB547), style = MaterialTheme.typography.bodySmall)
        }
        if (state.frameCount > 0) {
            Text("Đã ghi ${state.frameCount} khung hình · ${state.lines.size} câu",
                color = Accent, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(state.title, { store.dispatch(TeachAction.Title(it)) },
            label = { Text("Tên kỹ năng") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.teacher, { store.dispatch(TeachAction.Teacher(it)) },
            label = { Text("Người dạy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.notes, { store.dispatch(TeachAction.Notes(it)) },
            label = { Text("Ghi chú thêm (hoặc mô tả bằng chữ)") }, minLines = 2,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.dispatch(TeachAction.StartRecording) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (state.frameCount > 0) "Ghi lại" else "Bắt đầu ghi")
        }
        Button(onClick = { store.dispatch(TeachAction.Compile) }, enabled = state.canCompile,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211C09)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("AI học ngay") }
    }
}

@Composable
private fun CompileStage(store: TeachStore, state: TeachState) {
    val label = when (state.compilePhase) {
        "verifying" -> "AI đang tự kiểm tra lại kỹ năng…"
        else -> "AI đang chia thao tác thành từng bước…"
    }
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text("Thường mất 10–30 giây.", color = Color(0xFFADB8A4))
    TextButton(onClick = { store.dispatch(TeachAction.CancelCompile) }) { Text("Huỷ", color = Accent) }
}

@Composable
private fun ReviewStage(store: TeachStore, state: TeachState) {
    val draft = state.draft ?: return
    Text("AI đã học xong", style = MaterialTheme.typography.headlineSmall)
    Text(draft.title, color = Accent, style = MaterialTheme.typography.titleMedium)
    draft.steps.forEachIndexed { index, step ->
        OutlinedTextField(
            step.instruction,
            { store.dispatch(TeachAction.EditStep(index, it)) },
            label = { Text("Bước ${index + 1}") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (step.tip.isNotBlank()) {
            Text("Mẹo: ${step.tip}", style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFADB8A4))
        }
        if (step.warning.isNotBlank()) {
            Text("Lưu ý: ${step.warning}", style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB547))
        }
    }
    Button(onClick = { store.dispatch(TeachAction.Save) },
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211C09)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Lưu kỹ năng") }
    TextButton(onClick = { store.dispatch(TeachAction.Reset) }) { Text("Dạy lại", color = Accent) }
}

@Composable
internal fun rememberTeachStore(
    container: com.example.kmmbase.di.AppContainer,
    shadow: com.example.kmmbase.presentation.shadow.ShadowStore,
    frames: CameraFrameSource,
): TeachStore {
    val store = remember { container.teachStore(shadow, frames) }
    DisposableEffect(Unit) { onDispose { store.close() } }
    return store
}
