package com.example.kmmbase.android.features.livetryon.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kmmbase.android.di.NativeServices
import com.example.kmmbase.di.AppContainer
import com.example.kmmbase.presentation.livetryon.LiveTryOnPhase
import com.example.kmmbase.presentation.shadow.ShadowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Composition root owns the native adapter and shared store for exactly one screen lifetime. */
@Composable
internal fun LiveTryOnScreen(container: AppContainer, shadow: ShadowStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val engine = remember { NativeServices.liveVideoEngine(context) }
    val store = remember { container.liveTryOnStore(shadow, engine) }
    val state by store.state.collectAsState()
    val owner = LocalLifecycleOwner.current
    var permissionError by remember { mutableStateOf(false) }
    var memoryMB by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            memoryMB = withContext(Dispatchers.Default) { (android.os.Debug.getPss() / 1024).toInt() }
            delay(2000)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionError = !granted
        if (granted && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) store.start()
    }
    DisposableEffect(store, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) store.stop("Đã dừng khi rời màn hình.")
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); store.close() }
    }
    val close = { store.stop("Đã dừng phiên."); onClose() }
    BackHandler(onBack = close)
    val active = state.phase == LiveTryOnPhase.LIVE || state.phase == LiveTryOnPhase.CONNECTING
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = close) { Text("Đóng") }
            Text("Thử áo trực tiếp", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (active) {
                AndroidView(factory = { engine.remoteView }, modifier = Modifier.fillMaxSize())
                if (state.phase != LiveTryOnPhase.LIVE) {
                    Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                        Text("Đang chờ video AI…", color = Color.White)
                    }
                }
                Column(Modifier.align(Alignment.TopEnd).width(100.dp)) {
                    AndroidView(factory = { engine.localView }, modifier = Modifier.height(140.dp))
                    Text("Camera gốc", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Text(if (state.garments.isEmpty()) "Chưa có áo trong tủ đồ. Thêm ảnh áo rồi mở lại màn này."
                    else "Chọn áo bên dưới. Khi bắt đầu, camera sẽ được gửi tới Vidu để tạo video thử đồ.", color = Color.White)
            }
        }
        Text(state.message, color = Color.White)
        Text("RAM app (PSS): $memoryMB MB", color = Color.White, style = MaterialTheme.typography.labelSmall)
        if (active) {
            Text("Video AI: ${state.fps} FPS · còn ${state.remainingSeconds}s", color = Color.White)
            TextButton(onClick = { store.activity() }) { Text("Tôi vẫn đang thử") }
        }
        if (permissionError) Text("Cần quyền camera. Bật quyền trong Cài đặt ứng dụng rồi thử lại.", color = MaterialTheme.colorScheme.error)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.garments, key = { it.id }) { garment ->
                FilterChip(selected = state.selectedId == garment.id, onClick = { store.select(garment.id) },
                    enabled = !state.changing && state.phase != LiveTryOnPhase.CONNECTING,
                    label = { Text(garment.name.ifEmpty { "Áo" }) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active) {
                OutlinedButton(onClick = { store.stop("Đã dừng phiên.") }) { Text("Dừng") }
                if (state.phase == LiveTryOnPhase.LIVE) Button(onClick = { store.applySelection() },
                    enabled = !state.changing && state.selectedId != state.sentGarmentId) { Text(if (state.changing) "Đang đổi…" else "Đổi áo") }
            } else {
                Button(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) store.start()
                    else permission.launch(Manifest.permission.CAMERA)
                }, enabled = state.available && state.selectedId.isNotEmpty() && state.phase != LiveTryOnPhase.LOADING) { Text("Bắt đầu thử") }
                TextButton(onClick = { store.reload() }, enabled = state.phase != LiveTryOnPhase.LOADING) { Text("Kiểm tra lại") }
            }
        }
    }
}
