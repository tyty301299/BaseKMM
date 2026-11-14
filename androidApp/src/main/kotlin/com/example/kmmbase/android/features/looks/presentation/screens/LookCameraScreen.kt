package com.example.kmmbase.android.features.looks.presentation.screens

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.example.kmmbase.android.platform.camera.CameraFrameSource
import com.example.kmmbase.android.platform.camera.CameraPreview
import com.example.kmmbase.android.platform.vision.GarmentDetector
import com.example.kmmbase.android.platform.vision.GarmentRegion
import com.example.kmmbase.android.platform.camera.rememberCameraPermission
import com.example.kmmbase.presentation.looks.LookStore
import com.example.kmmbase.domain.model.ShadowSkill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LookCameraScreen(store: LookStore, skills: List<ShadowSkill>, connected: Boolean, modeLabel: String,
    onHistory: () -> Unit, onSettings: () -> Unit, onNetworkRestored: () -> Unit,
    onTeach: () -> Unit, onGuide: (String, String) -> Unit, onAutoGuide: (String) -> Unit, onLiveTryOn: () -> Unit,
    guideBusy: Boolean = false, guideError: String? = null) {
    val context = LocalContext.current
    val connectivity = remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    var online by remember { mutableStateOf<Boolean?>(null) }
    val currentOnNetworkRestored by rememberUpdatedState(onNetworkRestored)
    DisposableEffect(connectivity) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        fun updateConnection() {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            val connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            handler.post { val wasOffline = online == false; online = connected; if (wasOffline && connected) currentOnNetworkRestored() }
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = updateConnection()
            override fun onLost(network: Network) = updateConnection()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = updateConnection()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        updateConnection()
        onDispose { connectivity.unregisterNetworkCallback(callback); handler.removeCallbacksAndMessages(null) }
    }
    val granted = rememberCameraPermission()
    val frames = remember { CameraFrameSource() }
    val scope = rememberCoroutineScope()
    var front by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var timer by remember { mutableIntStateOf(0) }
    var countdown by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }
    var garmentRegion by remember { mutableStateOf<GarmentRegion?>(null) }
    var garmentStatus by remember { mutableStateOf("Đưa áo vào khung hình · đang tìm trang phục") }
    val garmentDetector = remember { GarmentDetector { region, status -> garmentRegion = region; garmentStatus = status } }
    DisposableEffect(garmentDetector) { onDispose { garmentDetector.close() } }
    LaunchedEffect(selectedMode, granted, front) { garmentDetector.setActive(selectedMode in 0..2 && granted, fashionOnly = selectedMode != 0) }
    val analyzeGarment = remember(garmentDetector) { { image: androidx.camera.core.ImageProxy, source: CameraFrameSource -> garmentDetector.analyze(image, source) } }
    var guideStep by rememberSaveable { mutableIntStateOf(-1) }
    var manualSessionId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var showSkills by remember { mutableStateOf(false) }
    val modes = listOf("Gói quà", "Gấp áo", "Gấp quần", "Thay đồ", "Dạy học")
    val hints = listOf("Đặt món quà vào khung hình", "Trải áo trên mặt phẳng", "Trải quần trên mặt phẳng", "Đưa trang phục vào khung hình", "Dạy hoặc làm theo một kỹ năng")
    val steps = if (selectedMode == 0) listOf(
        "Đặt hộp lên giữa mặt trái của giấy gói.", "Kéo hai mép giấy qua hộp, chồng lên nhau và dán lại.",
        "Gấp hai cạnh bên thành hình tam giác, rồi gập và dán đầu hộp.", "Lặp lại ở đầu còn lại, sau đó buộc ruy băng."
    ) else if (selectedMode == 1) listOf(
        "Trải áo phẳng, mặt trước úp xuống và vuốt thẳng.", "Gấp một bên áo và tay áo vào giữa.",
        "Gấp bên còn lại vào giữa để hai mép thẳng hàng.", "Gấp phần thân áo từ dưới lên và vuốt phẳng."
    ) else listOf(
        "Trải quần phẳng, kéo thẳng hai ống quần.", "Gấp đôi quần theo chiều dọc, chồng hai ống lên nhau.",
        "Gấp phần đáy quần gọn vào trong.", "Gấp ống quần lên cạp và vuốt phẳng."
    )
    if (showSkills) AlertDialog(onDismissRequest = { showSkills = false }, title = { Text("Kỹ năng đã học") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            if (!connected) Text("Chưa kết nối SKI. Hãy kiểm tra mạng và cài đặt kết nối.")
            else if (skills.isEmpty()) Text("Chưa có kỹ năng đã học. Hãy dạy một kỹ năng mới.")
            skills.forEach { skill -> TextButton(onClick = { showSkills = false; onGuide(skill.id, skill.title) }) { Text(skill.title) } }
        } }, confirmButton = { TextButton(onClick = { showSkills = false }) { Text("Đóng") } })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraPreview(frames, Modifier.fillMaxSize(), front, torch, analyzeGarment)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .9f)))))
        if (!granted) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Cho phép truy cập camera để bắt đầu", color = Color.White)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Mở cài đặt") }
        }
        if (selectedMode in 0..2 && garmentRegion != null) {
            val region = garmentRegion!!
            ObjectDetectionCorners(region, front)
        }
        if (countdown > 0) Text("$countdown", Modifier.align(Alignment.Center), color = Color.White, style = MaterialTheme.typography.displayLarge)
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CameraButton("◷", "Lịch sử", onHistory)
                CameraButton("⚙", "Cài đặt", onSettings)
            }
            if (online == false) {
                Text("Không có Internet · Vẫn có thể chụp ảnh", Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp).background(Color(0xFF533C20).copy(alpha = .92f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp), color = Color.White,
                    style = MaterialTheme.typography.labelMedium)
            }
            if (guideStep >= 0) {
                Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(Color.Black.copy(alpha = .75f), RoundedCornerShape(20.dp)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("BƯỚC ${guideStep + 1} / ${steps.size}", color = Color.Yellow, style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { guideStep = -1 }) { Text("Kết thúc", color = Color.White) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(steps[guideStep], Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        BuiltinStepIllustration(selectedMode == 0, guideStep, Modifier.size(width = 112.dp, height = 92.dp), isPants = selectedMode == 2)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { guideStep-- }, enabled = guideStep > 0) { Text("Trước") }
                        Button(onClick = {
                            if (guideStep == steps.lastIndex) {
                                val id = when (selectedMode) { 0 -> "shadow-builtin-goi-qua-v1"; 1 -> "shadow-builtin-gap-ao-v1"; else -> "shadow-builtin-gap-quan-v1" }
                                store.dispatch(com.example.kmmbase.presentation.looks.LookAction.SaveGuideCompletion(manualSessionId, id, modes[selectedMode], "manual"))
                                guideStep = -1
                            } else guideStep++
                        }) {
                            Text(if (guideStep == steps.lastIndex) "Hoàn thành" else "Đã xong bước này")
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .5f), CircleShape)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(message.ifEmpty { if (selectedMode in 0..2) garmentStatus else hints[selectedMode] }, Modifier.weight(1f),
                    color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                CameraButton(if (timer == 0) "◷" else "${timer}s", "Hẹn giờ: ${if (timer == 0) "tắt" else "$timer giây"}") {
                    timer = when (timer) { 0 -> 3; 3 -> 10; else -> 0 }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (guideStep < 0 && selectedMode in 0..2) {
                if (selectedMode == 0) {
                    Text("Chuẩn bị hộp, giấy gói, băng dính và ruy băng. Đặt camera nhìn rõ hộp và hai tay.", color = Color.White,
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAutoGuide("gift") }, enabled = connected && !guideBusy) {
                            Text(if (guideBusy) "Đang chuẩn bị…" else "Hướng dẫn gói quà")
                        }
                        OutlinedButton(onClick = { manualSessionId = java.util.UUID.randomUUID().toString(); guideStep = 0; message = "" }) {
                            Text("Tự chuyển bước", color = Color.White)
                        }
                    }
                    Text(if (!connected) "Cần kết nối SKI để hướng dẫn qua camera." else if (modeLabel == "AI")
                        "AI kiểm tra thao tác và tự chuyển bước" else "Demo · kết quả kiểm tra giả lập", color = Color.White,
                        style = MaterialTheme.typography.labelSmall)
                    guideError?.let { Text(it, color = Color(0xFFFFB547), style = MaterialTheme.typography.bodySmall) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { manualSessionId = java.util.UUID.randomUUID().toString(); guideStep = 0; message = "" }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Hướng dẫn mẫu") }
                        OutlinedButton(onClick = {
                            if (connected) onAutoGuide(when (selectedMode) { 0 -> "gift"; 1 -> "fold"; else -> "pants" })
                            else message = "Cần kết nối SKI để tự chuyển bước"
                        }) { Text(if (!connected) "Tự động" else if (modeLabel == "AI") "Tự động AI" else "Tự động demo", color = Color.White) }
                    }
                }
            } else if (guideStep < 0 && selectedMode == 3) {
                Button(onClick = onLiveTryOn, enabled = connected) { Text("Thử áo trực tiếp") }
            } else if (guideStep < 0 && selectedMode == 4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { if (connected) onTeach() else message = "Cần kết nối SKI để dạy kỹ năng" }) { Text("Dạy kỹ năng mới", color = Color.White) }
                    TextButton(onClick = { showSkills = true }) { Text("Kỹ năng đã học", color = Color.White) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 25.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CameraButton(if (torch) "ϟ" else "ϟ̸", if (torch) "Tắt đèn" else "Bật đèn") { torch = !torch && !front }
                Button(enabled = granted && !working, onClick = {
                    working = true; message = ""
                    scope.launch {
                        for (i in timer downTo 1) { countdown = i; delay(1000) }
                        countdown = 0
                        val data = frames.capture(1920)?.substringAfter(',')
                        message = if (data != null && runCatching {
                            val bytes = Base64.decode(data, Base64.DEFAULT)
                            if (Build.VERSION.SDK_INT >= 29) {
                                val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "SHADOW_${System.currentTimeMillis()}.jpg"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SKI") }
                                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create photo")
                                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Cannot save photo")
                            } else {
                                val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: error("No storage")
                                java.io.File(dir, "SHADOW_${System.currentTimeMillis()}.jpg").writeBytes(bytes)
                            }
                        }.isSuccess) "Đã lưu ảnh" else "Chưa chụp được ảnh, hãy thử lại"
                        working = false
                    }
                }, contentPadding = PaddingValues(7.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.size(80.dp).border(3.dp, Color.White, CircleShape)) {
                    Box(Modifier.fillMaxSize().background(Color.White, CircleShape))
                }
                CameraButton("↻", "Đổi camera") { front = !front; torch = false }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f), RoundedCornerShape(24.dp)).horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.Center) {
                modes.forEachIndexed { index, title ->
                    val isSelected = selectedMode == index
                    TextButton(onClick = { selectedMode = index; guideStep = -1; message = "" },
                        modifier = Modifier.padding(horizontal = 2.dp)
                            .background(if (isSelected) Color.White else Color.Transparent, CircleShape)
                            .semantics { selected = isSelected },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(title, color = if (isSelected) Color.Black else Color.White.copy(alpha = .8f),
                            style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraButton(icon: String, label: String, action: () -> Unit) {
    TextButton(onClick = action, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = .4f), CircleShape).semantics { contentDescription = label }) {
        Text(icon, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun ObjectDetectionCorners(region: GarmentRegion, front: Boolean) {
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Vùng trang phục ước lượng" }) {
                val imageHeight = maxOf(size.height, size.width / region.aspect)
                val imageWidth = imageHeight * region.aspect
                val offsetX = (size.width - imageWidth) / 2
                val offsetY = (size.height - imageHeight) / 2
                val left = if (front) 1f - region.right else region.left
                val right = if (front) 1f - region.left else region.right
                val x1 = offsetX + left * imageWidth
                val x2 = offsetX + right * imageWidth
                val y1 = offsetY + region.top * imageHeight
                val y2 = offsetY + region.bottom * imageHeight
                val radius = minOf(16.dp.toPx(), (x2 - x1) / 4, (y2 - y1) / 4).coerceAtLeast(0f)
                val length = minOf(38.dp.toPx(), (x2 - x1) / 3, (y2 - y1) / 3).coerceAtLeast(0f)
                val corners = Path().apply {
                    moveTo(x1, y1 + length); lineTo(x1, y1 + radius); quadraticTo(x1, y1, x1 + radius, y1); lineTo(x1 + length, y1)
                    moveTo(x2 - length, y1); lineTo(x2 - radius, y1); quadraticTo(x2, y1, x2, y1 + radius); lineTo(x2, y1 + length)
                    moveTo(x2, y2 - length); lineTo(x2, y2 - radius); quadraticTo(x2, y2, x2 - radius, y2); lineTo(x2 - length, y2)
                    moveTo(x1 + length, y2); lineTo(x1 + radius, y2); quadraticTo(x1, y2, x1, y2 - radius); lineTo(x1, y2 - length)
                }
                drawPath(corners, Color(0x558AF72B), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(corners, Color(0xFF8AF72B), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
}

/** Static reference drawing, not a picture taken by the camera. */
@Composable
internal fun BuiltinStepIllustration(isGift: Boolean, step: Int, modifier: Modifier = Modifier, isPants: Boolean = false) {
    val paper = Color(0xFFDECAA6)
    val fold = Color(0xFFB0C2A8)
    val ink = Color(0xFF405247)
    val accent = Color(0xFFF5A63B)
    Box(modifier.background(Color(0xFFF0EBDE), RoundedCornerShape(12.dp))
        .semantics { contentDescription = "Minh họa bước ${step + 1}" }) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 120f
            val sy = size.height / 100f
            fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun polygon(vararg coordinates: Pair<Float, Float>) = Path().apply {
                coordinates.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x * sx, y * sy) else lineTo(x * sx, y * sy) }
                close()
            }
            fun rectangle(x: Float, y: Float, w: Float, h: Float, color: Color) {
                drawRoundRect(color, p(x, y), androidx.compose.ui.geometry.Size(w * sx, h * sy), androidx.compose.ui.geometry.CornerRadius(4f * sx, 4f * sy))
            }
            if (isGift) {
                rectangle(9f, 18f, 102f, 69f, paper)
                rectangle(35f, 31f, 50f, 39f, fold)
                if (step >= 1) drawPath(polygon(9f to 20f, 57f to 31f, 57f to 70f, 9f to 84f), paper)
                if (step >= 2) {
                    drawPath(polygon(85f to 31f, 110f to 18f, 99f to 51f), fold)
                    drawPath(polygon(85f to 70f, 110f to 86f, 99f to 51f), fold)
                }
                if (step >= 3) {
                    drawLine(accent, p(59f, 31f), p(59f, 70f), 5f * sx)
                    drawLine(accent, p(35f, 51f), p(85f, 51f), 5f * sx)
                    drawCircle(accent, 5f * sx, p(53f, 45f), style = Stroke(2.5f * sx))
                    drawCircle(accent, 5f * sx, p(65f, 45f), style = Stroke(2.5f * sx))
                }
            } else if (isPants) {
                val pants = polygon(36f to 15f, 84f to 15f, 88f to 86f, 65f to 86f, 60f to 48f, 55f to 86f, 32f to 86f)
                drawPath(pants, fold); drawPath(pants, ink.copy(alpha = .55f), style = Stroke(1.5f * sx))
                if (step >= 1) rectangle(60f, 18f, 23f, 68f, ink.copy(alpha = .18f))
                if (step >= 2) rectangle(54f, 38f, 12f, 12f, paper)
                if (step >= 3) rectangle(38f, 51f, 43f, 33f, paper.copy(alpha = .8f))
            } else {
                val shirt = polygon(39f to 17f, 50f to 12f, 70f to 12f, 81f to 17f, 104f to 35f,
                    91f to 49f, 80f to 42f, 80f to 86f, 40f to 86f, 40f to 42f, 29f to 49f, 16f to 35f)
                drawPath(shirt, fold); drawPath(shirt, ink.copy(alpha = .55f), style = Stroke(1.5f * sx))
                if (step >= 1) rectangle(40f, 28f, 13f, 58f, ink.copy(alpha = .17f))
                if (step >= 2) rectangle(67f, 28f, 13f, 58f, ink.copy(alpha = .17f))
                if (step >= 3) rectangle(43f, 48f, 34f, 36f, paper.copy(alpha = .8f))
            }
            if (step in 1..2) {
                val left = step == 1
                val start = if (left) 12f else 108f
                val end = if (left) 40f else 80f
                val tail = if (left) -7f else 7f
                drawLine(accent, p(start, 53f), p(end, 53f), 3f * sx, cap = StrokeCap.Round)
                drawLine(accent, p(end, 53f), p(end + tail, 46f), 3f * sx, cap = StrokeCap.Round)
                drawLine(accent, p(end, 53f), p(end + tail, 60f), 3f * sx, cap = StrokeCap.Round)
            }
        }
        Text("MINH HỌA", Modifier.align(Alignment.BottomEnd).padding(5.dp), color = Color.Black.copy(alpha = .55f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified))
    }
}
