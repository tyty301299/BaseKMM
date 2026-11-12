package com.example.kmmbase.android.di

import com.example.kmmbase.platform.SpeechListener
import com.example.kmmbase.android.platform.speech.VietnameseListener
import com.example.kmmbase.android.platform.video.NativeLiveVideoEngine
import com.example.kmmbase.android.features.livetryon.data.AgoraLiveVideoEngine
import android.content.Context
import com.example.kmmbase.platform.Speaker
import com.example.kmmbase.android.platform.speech.VietnameseSpeaker

/** Native TTS selection is independent of guide business logic. */
internal object NativeServices {
    fun liveVideoEngine(context: Context): NativeLiveVideoEngine = AgoraLiveVideoEngine(context)
    fun speaker(context: Context, onAvailability: (Boolean) -> Unit = {}): Speaker = VietnameseSpeaker(context, onAvailability)
    fun listener(context: Context, elapsedMs: () -> Long,
                 onSentence: (Double, Double, String) -> Unit,
                 onPartial: (String) -> Unit, onUnavailable: () -> Unit): SpeechListener =
        VietnameseListener(context, elapsedMs, onSentence, onPartial, onUnavailable)
}
