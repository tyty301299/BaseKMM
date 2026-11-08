package com.example.kmmbase.android.features.kitchen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.kmmbase.domain.model.ZoneBox
import com.example.kmmbase.presentation.kitchen.ActionOutput
import com.example.kmmbase.presentation.kitchen.KitchenAction
import com.example.kmmbase.presentation.kitchen.KitchenPhase
import com.example.kmmbase.presentation.kitchen.KitchenState
import com.example.kmmbase.presentation.kitchen.KitchenStore
import com.example.kmmbase.presentation.kitchen.OutputKind
import com.example.kmmbase.presentation.kitchen.VoiceStatus

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

/** Keeps the device and its store alive together across rotation. */
internal class KitchenFeature(val device: AndroidKitchenDevice, val store: KitchenStore) : AutoCloseable {
    override fun close() = store.close()
}

@Composable
internal fun KitchenScreen(feature: KitchenFeature, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it }
    }
    BackHandler(onBack = onBack)

    Scaffold { padding ->
        if (granted) {
            val state by feature.store.state.collectAsState()
            KitchenContent(state, feature, Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("KitchenMate cần camera và micro để nhìn mặt bàn và nghe bạn nói.")
                Button(onClick = { launcher.launch(REQUIRED_PERMISSIONS) }) { Text("Cấp quyền") }
                OutlinedButton(onClick = onBack) { Text("Quay lại") }
            }
        }
    }
}

@Composable
private fun KitchenContent(state: KitchenState, feature: KitchenFeature, modifier: Modifier) {
    val dispatch = feature.store::dispatch
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.phase == KitchenPhase.COMPLETED) {
            Text("Hoàn thành!", style = MaterialTheme.typography.headlineMedium)
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoColumn("Trước", state.beforeImage, Modifier.weight(1f))
                PhotoColumn("Sau", state.afterImage, Modifier.weight(1f))
            }
            state.output?.let { OutputBanner(it) }
            Button(onClick = { dispatch(KitchenAction.Restart) }) { Text("Làm lại") }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPreview(feature.device, Modifier.fillMaxSize())
            if (state.phase == KitchenPhase.WORKING) {
                GuideOverlay(state, feature.device.frameAspect, Modifier.fillMaxSize())
            }
        }
        if (state.phase == KitchenPhase.READY) {
            Text("Hướng camera vào mặt bàn bếp rồi bắt đầu.")
            state.output?.let { OutputBanner(it) }
            Button(onClick = { dispatch(KitchenAction.Start) }) { Text("Bắt đầu") }
            return@Column
        }

        LinearProgressIndicator(
            progress = { state.stepIndex.toFloat() / state.steps.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Bước ${state.stepIndex + 1}/${state.steps.size}: ${state.currentStep.title}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(state.currentStep.instruction)
        if (state.objectSummary.isNotEmpty()) Text("Đang thấy: ${state.objectSummary}")
        if (state.caption.isNotEmpty()) Text("AI: ${state.caption}", maxLines = 3)
        state.output?.let { OutputBanner(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { dispatch(KitchenAction.Check) }, enabled = state.canCheck) {
                Text(if (state.isChecking) "Đang kiểm tra" else "Kiểm tra")
            }
            if (state.voice == VoiceStatus.ERROR) {
                OutlinedButton(onClick = { dispatch(KitchenAction.RetryVoice) }) { Text("Kết nối lại giọng nói") }
            }
        }
        Text(voiceLabel(state.voice), style = MaterialTheme.typography.bodySmall)
    }
}

private fun voiceLabel(voice: VoiceStatus): String = when (voice) {
    VoiceStatus.OFF -> "Giọng nói đang tắt"
    VoiceStatus.CONNECTING -> "Đang kết nối giọng nói"
    VoiceStatus.LISTENING -> "Đang nghe, nói \"Xong rồi\" khi làm xong"
    VoiceStatus.ERROR -> "Mất kết nối giọng nói, vẫn có thể bấm Kiểm tra"
}

@Composable
private fun OutputBanner(output: ActionOutput) {
    val colors = MaterialTheme.colorScheme
    val background = when (output.kind) {
        OutputKind.INFO -> colors.surfaceVariant
        OutputKind.SUCCESS -> colors.primaryContainer
        OutputKind.WARNING -> colors.tertiaryContainer
        OutputKind.ERROR -> colors.errorContainer
    }
    Surface(color = background, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(output.message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
    }
}

private val TARGET_COLOR = Color(0xFF2ECC71)
private val MISPLACED_COLOR = Color(0xFFE74C3C)
private val OBJECT_COLOR = Color(0xFF3498DB)

/** Draws where items belong and which ones are wrong, aligned to the letterboxed preview. */
@Composable
private fun GuideOverlay(state: KitchenState, frameAspect: Float, modifier: Modifier) {
    Canvas(modifier) {
        val frame = fittedFrame(frameAspect)
        state.objects.forEach { detected ->
            val rect = detected.box.toRect(frame)
            drawRect(OBJECT_COLOR, rect.topLeft, rect.size, style = Stroke(width = 2.dp.toPx()))
            drawLabel(detected.label, rect, OBJECT_COLOR)
        }
        state.targetZone?.let { zone ->
            val rect = zone.toRect(frame)
            drawRect(TARGET_COLOR.copy(alpha = 0.2f), rect.topLeft, rect.size)
            drawRect(
                TARGET_COLOR,
                rect.topLeft,
                rect.size,
                style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 12f))),
            )
            drawLabel("Đặt ở đây", rect, TARGET_COLOR)
        }
        state.misplaced.forEach { zone ->
            val rect = zone.toRect(frame)
            drawRect(MISPLACED_COLOR, rect.topLeft, rect.size, style = Stroke(width = 3.dp.toPx()))
            drawLabel("Chuyển món này", rect, MISPLACED_COLOR)
        }
    }
}

private fun DrawScope.fittedFrame(frameAspect: Float): Rect {
    val fitsWidth = size.width / size.height < frameAspect
    val width = if (fitsWidth) size.width else size.height * frameAspect
    val height = if (fitsWidth) size.width / frameAspect else size.height
    return Rect(Offset((size.width - width) / 2, (size.height - height) / 2), Size(width, height))
}

private fun ZoneBox.toRect(frame: Rect) = Rect(
    frame.left + left.toFloat() * frame.width,
    frame.top + top.toFloat() * frame.height,
    frame.left + right.toFloat() * frame.width,
    frame.top + bottom.toFloat() * frame.height,
)

private fun DrawScope.drawLabel(text: String, rect: Rect, color: Color) {
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = 14.dp.toPx()
        isFakeBoldText = true
        isAntiAlias = true
    }
    val y = if (rect.top > paint.textSize + 8) rect.top - 8 else rect.top + paint.textSize + 8
    drawIntoCanvas { it.nativeCanvas.drawText(text, rect.left, y, paint) }
}

@Composable
private fun CameraPreview(device: AndroidKitchenDevice, modifier: Modifier) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).also {
                it.scaleType = PreviewView.ScaleType.FIT_CENTER // The whole frame stays visible.
                device.bindCamera(owner, it.surfaceProvider)
            }
        },
    )
}

@Composable
private fun PhotoColumn(label: String, base64: String?, modifier: Modifier) {
    val bitmap = remember(base64) {
        base64?.let {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        bitmap?.let {
            Image(it, contentDescription = "Ảnh $label", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        }
    }
}
