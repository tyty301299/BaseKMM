package com.example.kmmbase.android.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.kmmbase.di.AppContainer

internal class StylingLocationControl(val label: String, val request: () -> Unit)

/** Approximate foreground location is sufficient for weather. No background tracking. */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberStylingLocation(container: AppContainer): StylingLocationControl {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var label by remember { mutableStateOf("Bật GPS") }
    var listener by remember { mutableStateOf<LocationListener?>(null) }
    val permission = Manifest.permission.ACCESS_COARSE_LOCATION
    fun allowed() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun stop() { listener?.let { manager.removeUpdates(it) }; listener = null; handler.removeCallbacksAndMessages(null) }
    fun accept(location: Location): Boolean {
        if (location.accuracy < 0 || SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos > 10 * 60 * 1_000_000_000L) return false
        container.updateLocation(location.latitude, location.longitude)
        label = "GPS ✓"; stop(); return true
    }
    fun refresh() {
        stop()
        if (!allowed()) { container.clearLocation(); label = "Bật GPS"; return }
        label = "Đang lấy GPS…"
        try {
            val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = manager.getProviders(true).filter {
                it != LocationManager.PASSIVE_PROVIDER && (precise || it != LocationManager.GPS_PROVIDER)
            }
            val cached = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
            if (cached != null && accept(cached)) return
            val updates = object : LocationListener {
                override fun onLocationChanged(location: Location) { accept(location) }
                override fun onProviderDisabled(provider: String) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            listener = updates
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, updates, Looper.getMainLooper()) }
            handler.postDelayed({ stop(); container.clearLocation(); label = "Thử lại GPS" }, 10_000)
        } catch (_: Exception) { stop(); container.clearLocation(); label = "Thử lại GPS" }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) refresh() else { container.clearLocation(); label = "Bật GPS" }
    }
    val refreshNow by rememberUpdatedState({ refresh() })
    DisposableEffect(lifecycle, container) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshNow()
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        lifecycle.addObserver(observer)
        refresh()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    return StylingLocationControl(label) {
        if (allowed()) refresh()
        else {
            val prefs = context.getSharedPreferences("shadow", Context.MODE_PRIVATE)
            if (prefs.getBoolean("location_asked", false)) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            } else {
                prefs.edit().putBoolean("location_asked", true).apply()
                launcher.launch(arrayOf(permission, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        }
    }
}
