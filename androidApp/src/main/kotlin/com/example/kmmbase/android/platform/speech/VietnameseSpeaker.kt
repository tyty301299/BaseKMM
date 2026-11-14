package com.example.kmmbase.android.platform.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.kmmbase.platform.Speaker
import java.util.Locale

/**
 * Vietnamese TTS. Lines queued before the engine finishes initialising are kept and spoken once it
 * is ready, so the first line of a step is never silently dropped.
 */
class VietnameseSpeaker(context: Context, private val onAvailability: (Boolean) -> Unit = {}) : Speaker {
    override val available: Boolean get() = ready && !released
    private var ready = false
    private var pending: String? = null
    private var released = false

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (released) return@TextToSpeech
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val language = engine.setLanguage(Locale("vi", "VN"))
            ready = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            if (ready) pending?.let { speakNow(it) }
        }
        pending = null
        onAvailability(ready)
    }

    override fun speak(text: String) {
        if (released || text.isBlank()) return
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        // Only normalize speech: the product name stays SKI in the UI.
        val spoken = text.replace(Regex("\\bSKI\\b"), "sờ ki")
        engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "shadow")
    }

    override fun stop() {
        pending = null
        if (ready) engine.stop()
    }

    override fun shutdown() {
        released = true
        pending = null
        engine.stop()
        engine.shutdown()
    }
}
