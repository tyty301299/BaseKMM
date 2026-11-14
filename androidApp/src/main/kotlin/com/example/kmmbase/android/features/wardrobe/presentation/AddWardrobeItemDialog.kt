package com.example.kmmbase.android.features.wardrobe.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.kmmbase.android.platform.media.preparePersonPhoto
import com.example.kmmbase.presentation.shadow.ShadowAction
import com.example.kmmbase.presentation.shadow.ShadowStore
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AddWardrobeItemDialog(store: ShadowStore, onDismiss: () -> Unit) {
    val state by store.state.collectAsState()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("top") }
    var menu by remember { mutableStateOf(false) }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var reading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    val busy = state.busy.isNotEmpty()
    fun close() { capturePath?.let { File(it).delete() }; capturePath = null; onDismiss() }
    val categories = listOf("top" to "Áo", "bottom" to "Quần", "outerwear" to "Áo khoác", "dress" to "Váy / đầm", "shoes" to "Giày", "accessory" to "Phụ kiện")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { selected ->
        if (selected != null) { capturePath?.let { File(it).delete() }; capturePath = null; uri = selected.toString() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = capturePath?.let(::File)
        if (success && file != null) uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        else { file?.delete(); capturePath = null }
    }
    fun takePhoto() {
        try {
            capturePath?.let { File(it).delete() }
            val directory = File(context.cacheDir, "wardrobe-capture").apply { mkdirs() }
            val file = File.createTempFile("garment-", ".jpg", directory)
            capturePath = file.path
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (_: Exception) {
            capturePath?.let { File(it).delete() }; capturePath = null
            photoError = "Chưa mở được camera. Bạn có thể chọn ảnh từ thư viện."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) takePhoto() else photoError = "Cho phép camera trong Cài đặt, hoặc chọn ảnh từ thư viện."
    }
    LaunchedEffect(uri) {
        val selected = uri ?: return@LaunchedEffect
        reading = true; jpeg = null; photoError = null
        try { jpeg = withContext(Dispatchers.IO) { preparePersonPhoto(context, Uri.parse(selected)) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { photoError = "Không đọc được ảnh này. Hãy chọn một ảnh khác." }
        finally {
            reading = false
        }
    }
    LaunchedEffect(state.wardrobeAddedId, busy) { if (state.wardrobeAddedId != null && !busy) close() }
    Dialog(onDismissRequest = { if (!busy) close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Thêm vào tủ đồ", style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = ::close, enabled = !busy) { Text("Hủy") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Chụp hoặc chọn ảnh từng món đồ. Ảnh được lưu vào cùng tủ đồ với website.", style = MaterialTheme.typography.bodyMedium)
                    if (jpeg != null) AsyncImage(jpeg, "Ảnh trang phục đã chọn", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(240.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)))
                    else Box(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))) {
                        Text("Chọn ảnh trang phục", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !reading,
                            modifier = Modifier.weight(1f)) { Text("Chọn ảnh") }
                        OutlinedButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                            else permission.launch(Manifest.permission.CAMERA)
                        }, enabled = !busy && !reading, modifier = Modifier.weight(1f)) { Text("Chụp ảnh") }
                    }
                    if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Tên món đồ") }, placeholder = { Text("Ví dụ: Áo sơ mi trắng") },
                        singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text("Loại đồ: ${categories.first { it.first == category }.second}  ▾")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            categories.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { category = key; menu = false }) }
                        }
                    }
                    (photoError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Đang thêm đồ vào tủ…")
                    }
                }
                Button(onClick = {
                    val bytes = jpeg ?: return@Button
                    store.dispatch(ShadowAction.AddWardrobeItem("data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP), name, category))
                }, enabled = jpeg != null && name.isNotBlank() && !busy && !reading && state.connected,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Lưu vào tủ đồ") }
            }
        }
    }
}
