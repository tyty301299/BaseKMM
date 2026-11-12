package com.example.kmmbase.platform

/** Captures one camera frame as a `data:image/jpeg;base64,...` URL, or null if unavailable. */
interface FrameSource {
    suspend fun capture(maxSide: Int): String?
}
