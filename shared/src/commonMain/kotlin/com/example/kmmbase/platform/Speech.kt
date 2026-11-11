package com.example.kmmbase.platform

/** Vietnamese text-to-speech. A new line interrupts the current one (see GuideStore barge-in). */
interface Speaker {
    fun speak(text: String)
    fun stop()

    /** Frees the platform engine. Named `shutdown` because `release` collides with ObjC memory rules. */
    fun shutdown()
}

/** Captures one camera frame as a `data:image/jpeg;base64,...` URL, or null if unavailable. */
interface FrameSource {
    suspend fun capture(maxSide: Int): String?
}
