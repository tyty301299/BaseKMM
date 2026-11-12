package com.example.kmmbase.android.platform.speech

import com.example.kmmbase.platform.SpeechListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous Vietnamese speech recognition for the teach flow.
 *
 * `SpeechRecognizer` ends a session after each utterance, so it is restarted until [stop]. Each
 * final result is timestamped against the recording clock, which the compiler needs to line
 * sentences up with keyframes.
 */
class VietnameseListener(
    context: Context,
    private val elapsedMs: () -> Long,
    private val onSentence: (t0: Double, t1: Double, String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) : SpeechListener {
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        } else null

    private var listening = false
    private var utteranceStart = 0.0

    override val available: Boolean get() = recognizer != null

    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private val callback = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            utteranceStart = elapsedMs() / 1000.0
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstResult()?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            results.firstResult()?.let { onSentence(utteranceStart, elapsedMs() / 1000.0, it) }
            restart()
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                listening = false
                onUnavailable()
                return
            }
            // Silence and timeouts are normal between sentences: just listen again.
            restart()
        }

        override fun onEndOfSpeech() = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.firstResult(): String? = this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }

    override fun start() {
        val engine = recognizer ?: return onUnavailable()
        listening = true
        engine.setRecognitionListener(callback)
        engine.startListening(intent)
    }

    private fun restart() {
        if (!listening) return
        recognizer?.startListening(intent)
    }

    override fun stop() {
        listening = false
        recognizer?.stopListening()
    }

    override fun shutdown() {
        listening = false
        recognizer?.destroy()
    }
}
