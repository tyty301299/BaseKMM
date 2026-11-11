package com.example.kmmbase.android.common

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.kmmbase.platform.Speaker
import java.util.Locale

/**
 * Vietnamese TTS. Lines queued before the engine finishes initialising are kept and spoken once it
 * is ready, so the first line of a step is never silently dropped.
 */
class VietnameseSpeaker(context: Context) : Speaker {
    private var ready = false
    private var pending: String? = null
    private var released = false

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.language = Locale("vi", "VN")
            pending?.let { speakNow(it) }
        }
        pending = null
    }

    override fun speak(text: String) {
        if (released || text.isBlank()) return
        if (ready) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) {
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "shadow")
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
