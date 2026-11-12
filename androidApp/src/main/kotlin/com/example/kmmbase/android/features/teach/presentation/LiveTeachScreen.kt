package com.example.kmmbase.android.features.teach.presentation

import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.R
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
import com.example.kmmbase.android.platform.speech.*
import com.example.kmmbase.android.platform.camera.*
import com.example.kmmbase.android.platform.media.*
import com.example.kmmbase.android.platform.vision.*
import com.example.kmmbase.presentation.teach.*

private val Accent = Color(0xFFFFD23F)

/**
 * Teaching flow: record a demonstration, let the compiler turn it into steps, review, save.
 *
 * Supports live keyframes/dictation and persisted video-learning jobs.
 */
@Composable
internal fun LiveTeachScreen(
    frames: CameraFrameSource,
    store: TeachStore,
    imageHeaders: (String) -> Map<String, String>,
    onDone: (skillId: String) -> Unit,
    onExit: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val state by store.state.collectAsState()
    val context = LocalContext.current
    val cameraGranted = rememberCameraPermission()
    var videoError by remember { mutableStateOf<String?>(null) }
    var recordedPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val upload: (android.net.Uri) -> Unit = { uri ->
        runCatching { AndroidTeachVideoSource(context, uri) }.onSuccess {
            store.dispatch(TeachAction.UploadVideo(it))
        }.onFailure { videoError = "Không mở được video. Chọn video lưu trên thiết bị." }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            upload(it)
        }
    }
    val recorder = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val path = recordedPath
        if (success && path != null) upload(android.net.Uri.fromFile(java.io.File(path)))
    }
    val recordVideo: () -> Unit = {
        runCatching {
            val folder = java.io.File(context.filesDir, "teach-videos").apply { mkdirs() }
            val file = java.io.File.createTempFile("teaching-", ".mp4", folder)
            recordedPath = file.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
            recorder.launch(uri)
        }.onFailure { videoError = "Không mở được ứng dụng quay video." }
    }
    var micGranted by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        micGranted = it
        if (!it) store.dispatch(TeachAction.SpeechAvailable(false))
    }
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    val listener = remember {
        com.example.kmmbase.android.di.NativeServices.listener(
            context = context,
            elapsedMs = { store.currentState.elapsedMs },
            onSentence = { t0, t1, text -> store.dispatch(TeachAction.Sentence(t0, t1, text)) },
            onPartial = { store.dispatch(TeachAction.PartialSentence(it)) },
            onUnavailable = { store.dispatch(TeachAction.SpeechAvailable(false)) },
        )
    }
    DisposableEffect(Unit) {
        store.dispatch(TeachAction.SpeechAvailable(listener.available))
        onDispose { listener.shutdown() }
    }
    LaunchedEffect(state.phase, state.stopping, micGranted) {
        if (state.phase == TeachPhase.RECORDING && !state.stopping && micGranted) listener.start() else listener.stop()
    }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner, store, listener) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                listener.stop()
                store.dispatch(TeachAction.StopRecording)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
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
            Text(language.text(R.string.ui_058), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onExit) { Text(language.text(R.string.close), color = Accent) }
        }

        if (state.phase == TeachPhase.IDLE) {
            Text("Học từ video: nhập tên và ghi chú bên dưới, rồi quay hoặc chọn clip tối đa 180 giây / 32 MB.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("video/*")) }, enabled = !state.videoBusy && !state.hasVideoJob && state.title.isNotBlank()) { Text("Chọn video") }
                OutlinedButton(onClick = recordVideo, enabled = cameraGranted && !state.videoBusy && !state.hasVideoJob && state.title.isNotBlank()) { Text("Quay video") }
            }
        }
        if (state.videoLocalUri.isNotBlank()) {
            TextButton(onClick = {
                runCatching {
                    var uri = android.net.Uri.parse(state.videoLocalUri)
                    if (uri.scheme == "file") uri = androidx.core.content.FileProvider.getUriForFile(context,
                        context.packageName + ".files", java.io.File(requireNotNull(uri.path)))
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri, "video/*")
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { videoError = "Không mở được video gốc trên thiết bị." }
            }) { Text("Xem video gốc") }
        }
        if (state.videoStatus.isNotBlank()) {
            Text(state.videoStatusLabel)
            if (state.videoBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.hasVideoJob) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { store.dispatch(TeachAction.ResumeVideo) }, enabled = !state.videoBusy && !state.saving) { Text("Mở lại kết quả") }
                if (state.videoStatus in listOf("failed", "interrupted", "cancelled")) {
                    TextButton(onClick = { store.dispatch(TeachAction.RetryVideo) }, enabled = !state.videoBusy) { Text("Thử lại") }
                }
                TextButton(onClick = { store.dispatch(TeachAction.CancelVideo) }, enabled = !state.saving) { Text("Dừng xử lý") }
            }
            var confirmDelete by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmDelete = true }, enabled = !state.videoBusy && !state.saving) { Text("Xóa video/tác vụ trên máy chủ") }
            if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Xóa video trên máy chủ?") },
                text = { Text("Bài đã lưu không bị xóa. Không thể mở lại video và kết quả tác vụ này.") },
                confirmButton = { TextButton(onClick = { confirmDelete = false; store.dispatch(TeachAction.DeleteVideo) }) { Text("Xóa") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Giữ lại") } })
        }
        videoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when (state.phase) {
            TeachPhase.RECORDING, TeachPhase.IDLE -> RecordStage(frames, store, state, cameraGranted)
            TeachPhase.COMPILING -> CompileStage(store, state)
            TeachPhase.REVIEW -> ReviewStage(store, state, imageHeaders)
            TeachPhase.SAVED -> Text(language.text(R.string.ui_244), color = Accent,
                style = MaterialTheme.typography.headlineSmall)
        }

        state.error?.let { Text(language.systemText(it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun RecordStage(
    frames: CameraFrameSource,
    store: TeachStore,
    state: TeachState,
    cameraGranted: Boolean,
) {
    val language = LocalAppLanguage.current
    if (cameraGranted) {
        CameraPreview(frames, Modifier.fillMaxWidth().height(260.dp))
    } else {
        Text(language.text(R.string.ui_050))
    }

    if (state.phase == TeachPhase.RECORDING) {
        Text(language.format(R.string.recording_counts, (state.elapsedMs / 1000).toString(), (state.frameCount).toString()),
            color = Accent, style = MaterialTheme.typography.titleMedium)
        Text(language.text(R.string.ui_189))
        if (state.liveTranscript.isNotBlank()) {
            Text(state.liveTranscript, color = Color(0xFFADB8A4))
        }
        state.lines.takeLast(3).forEach { Text("· ${it.text}", style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { store.dispatch(TeachAction.StopRecording) }, enabled = !state.stopping,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(language.text(R.string.ui_061)) }
    } else {
        if (!state.speechAvailable) {
            Text(language.text(R.string.ui_157),
                color = Color(0xFFFFB547), style = MaterialTheme.typography.bodySmall)
        }
        if (state.frameCount > 0) {
            Text(language.format(R.string.recorded_counts, (state.frameCount).toString(), (state.lines.size).toString()),
                color = Accent, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(state.title, { store.dispatch(TeachAction.Title(it)) },
            label = { Text(language.text(R.string.ui_176)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.teacher, { store.dispatch(TeachAction.Teacher(it)) },
            label = { Text(language.text(R.string.ui_135)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.notes, { store.dispatch(TeachAction.Notes(it)) },
            label = { Text(language.text(R.string.ui_068)) }, minLines = 2,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { store.dispatch(TeachAction.StartRecording) }, enabled = cameraGranted && !state.stopping && !state.videoBusy && !state.hasVideoJob,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (state.frameCount > 0) language.text(R.string.ui_069) else language.text(R.string.ui_018))
        }
        Button(onClick = { store.dispatch(TeachAction.Compile) }, enabled = state.canCompile,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211C09)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(language.text(R.string.ui_005)) }
    }
}

@Composable
private fun CompileStage(store: TeachStore, state: TeachState) {
    val language = LocalAppLanguage.current
    val label = when (state.compilePhase) {
        "verifying" -> language.text(R.string.ui_008)
        else -> language.text(R.string.ui_007)
    }
    LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(label, style = MaterialTheme.typography.titleMedium)
    Text(language.text(R.string.ui_164), color = Color(0xFFADB8A4))
    TextButton(onClick = { store.dispatch(TeachAction.CancelCompile) }) { Text(language.text(R.string.ui_076), color = Accent) }
}

@Composable
private fun ReviewStage(store: TeachStore, state: TeachState, imageHeaders: (String) -> Map<String, String>) {
    val language = LocalAppLanguage.current
    val draft = state.draft ?: return
    Text(language.text(R.string.ui_009), style = MaterialTheme.typography.headlineSmall)
    if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text(draft.title, color = Accent, style = MaterialTheme.typography.titleMedium)
    if (draft.summary.isNotBlank()) Text(draft.summary)
    Text("Nguồn: " + com.example.kmmbase.domain.model.shadowSourceLabel(draft.compiledBy))
    if (draft.compiledBy != "ai" || draft.fallbackReason.isNotBlank()) {
        Text("Bản nháp chưa được xác nhận là kết quả AI thật. Hãy kiểm tra kỹ trước khi lưu.", color = Color(0xFFFFB547))
    }
    draft.warnings.forEach { Text(it, color = Color(0xFFFFB547)) }
    if (state.dirtyStepIds.isNotEmpty()) Text("Bước đã sửa cần kiểm chứng lại trước khi lưu.", color = Color(0xFFFFB547))
    draft.steps.forEachIndexed { index, step ->
        OutlinedTextField(
            step.instruction,
            { store.dispatch(TeachAction.EditStep(index, it)) },
            label = { Text(language.format(R.string.step_number, (index + 1).toString())) },
            enabled = !state.saving && state.verifyingStep == null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (step.referenceImageUrl.isNotBlank()) {
            coil.compose.AsyncImage(model = shadowImageRequest(LocalContext.current, step.referenceImageUrl,
                imageHeaders(step.referenceImageUrl)), contentDescription = "Ảnh tham chiếu", modifier = Modifier.size(100.dp))
        }
        val check = draft.checks.firstOrNull { it.stepId == step.id }
        Text(check?.reason?.takeIf { it.isNotBlank() } ?: "Chưa kiểm chứng bước này.", style = MaterialTheme.typography.bodySmall)
        val verifying = state.verifyingStep == index
        if (verifying) LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { store.dispatch(TeachAction.VerifyStep(index)) },
            enabled = !state.saving && state.verifyingStep == null && step.instruction.isNotBlank()) { Text("Kiểm chứng lại bước") }
        var showFrames by remember(step.id) { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showFrames = true }, enabled = draft.frames.isNotEmpty() && !state.saving && state.verifyingStep == null) {
                Text("Chọn ảnh khác và kiểm chứng")
            }
            DropdownMenu(expanded = showFrames, onDismissRequest = { showFrames = false }) {
                draft.frames.forEach { frame ->
                    DropdownMenuItem(text = { Text("Ảnh ${frame.index + 1} · ${frame.seconds.toInt()} giây") }, onClick = {
                        showFrames = false
                        store.dispatch(TeachAction.VerifyStep(index, frame.index))
                    })
                }
            }
        }
        if (step.tip.isNotBlank()) {
            Text(language.format(R.string.tip_detail, (step.tip).toString()), style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFADB8A4))
        }
        if (step.warning.isNotBlank()) {
            Text(language.format(R.string.warning_detail, (step.warning).toString()), style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB547))
        }
    }
    Button(onClick = { store.dispatch(TeachAction.Save) }, enabled = state.canSave,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF211C09)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(language.text(R.string.ui_111)) }
    TextButton(onClick = { store.dispatch(TeachAction.Reset) }) { Text(language.text(R.string.ui_059), color = Accent) }
}

@Composable
internal fun rememberTeachStore(
    container: com.example.kmmbase.di.AppContainer,
    shadow: com.example.kmmbase.presentation.shadow.ShadowStore,
    frames: CameraFrameSource,
): TeachStore {
    val context = LocalContext.current
    val store = remember { container.teachStoreWithVideo(shadow, frames, AndroidTeachJobPersistence(context, shadow.currentState.endpoint)) }
    DisposableEffect(Unit) { onDispose { store.close() } }
    return store
}
