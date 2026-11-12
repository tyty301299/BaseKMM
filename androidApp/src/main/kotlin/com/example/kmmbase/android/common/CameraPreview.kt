package com.example.kmmbase.android.common

import android.Manifest
import android.view.MotionEvent
import androidx.camera.core.FocusMeteringAction
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/** True once the user has granted camera access, asking on first composition if needed. */
@Composable
fun rememberCameraPermission(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    return granted
}

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Live camera preview that also feeds [frames]. Uses the back camera for guiding work on a table;
 * pass `front = true` for try-on.
 */
@Composable
fun CameraPreview(frames: CameraFrameSource, modifier: Modifier = Modifier, front: Boolean = false, torch: Boolean = false,
                  frameAnalyzer: ((ImageProxy, CameraFrameSource) -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestAnalyzer = rememberUpdatedState(frameAnalyzer)
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }

    val previewView = remember(context) { PreviewView(context) }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            frames.clear()
        }
    }
    DisposableEffect(previewView, lifecycleOwner, front, torch) {
        var disposed = false
        providerFuture.addListener({
            if (!disposed) {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(analysisExecutor) { image ->
                        val analyzer = latestAnalyzer.value
                        if (analyzer == null) frames.offer(image) else analyzer(image, frames)
                    } }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis).also { camera ->
                        if (camera.cameraInfo.hasFlashUnit()) camera.cameraControl.enableTorch(torch)
                        previewView.setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                                camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                                previewView.performClick()
                                true
                            } else event.action == MotionEvent.ACTION_DOWN
                        }
                    }
                } catch (_: Exception) {
                    // A missing lens or an unavailable camera leaves the preview empty.
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            previewView.setOnTouchListener(null)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }
    AndroidView(modifier = modifier, factory = { previewView })
}
