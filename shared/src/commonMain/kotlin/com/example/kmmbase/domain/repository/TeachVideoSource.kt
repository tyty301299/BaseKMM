package com.example.kmmbase.domain.repository

/** Sequential native file reader. Upload streams chunks instead of retaining a video in RAM. */
interface TeachVideoSource {
    val localUri: String get() = ""
    val sizeBytes: Long
    suspend fun readChunk(maxBytes: Int): ByteArray?
    fun close()
}
