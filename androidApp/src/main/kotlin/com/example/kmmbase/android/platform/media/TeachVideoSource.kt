package com.example.kmmbase.android.platform.media

import android.content.Context
import android.net.Uri
import com.example.kmmbase.domain.model.TeachVideoTicket
import com.example.kmmbase.domain.repository.TeachVideoSource
import com.example.kmmbase.platform.TeachJobPersistence
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class AndroidTeachVideoSource(context: Context, private val uri: Uri) : TeachVideoSource {
    private val descriptor = requireNotNull(context.contentResolver.openFileDescriptor(uri, "r")) { "Không mở được video." }
    private val input = FileInputStream(descriptor.fileDescriptor)
    override val localUri: String get() = uri.toString()
    override val sizeBytes: Long = descriptor.statSize
    override suspend fun readChunk(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) {
        val bytes = ByteArray(maxBytes)
        val count = input.read(bytes)
        if (count < 0) null else bytes.copyOf(count)
    }
    override fun close() { runCatching { input.close() }; runCatching { descriptor.close() } }
}

internal class AndroidTeachJobPersistence(context: Context, endpoint: String) : TeachJobPersistence {
    private val prefs = context.getSharedPreferences("teach-video-jobs", Context.MODE_PRIVATE)
    private val key = endpoint
    override fun load(): TeachVideoTicket? = runCatching {
        val json = JSONObject(prefs.getString(key, null) ?: return null)
        TeachVideoTicket(json.getString("id"), json.getString("token"), json.getString("title"), json.getString("teacher"), json.getString("notes"), json.optString("localUri"))
    }.getOrNull()
    override fun save(ticket: TeachVideoTicket?) {
        if (ticket == null) prefs.edit().remove(key).apply()
        else prefs.edit().putString(key, JSONObject().put("id", ticket.jobId).put("token", ticket.token)
            .put("title", ticket.title).put("teacher", ticket.teacher).put("notes", ticket.notes).put("localUri", ticket.localUri).toString()).apply()
    }
}
