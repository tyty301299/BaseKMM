package com.example.kmmbase.domain.repository

import com.example.kmmbase.domain.model.SceneScan
import com.example.kmmbase.domain.model.StepVerification
import kotlinx.coroutines.flow.Flow

interface KitchenRepository {
    suspend fun verify(stepId: String, imageBase64: String): StepVerification

    /** Finds the objects on the table and where this step's items should go. */
    suspend fun scanScene(stepId: String, imageBase64: String): SceneScan
}

sealed interface VoiceEvent {
    data object Ready : VoiceEvent
    data class Audio(val pcmBase64: String) : VoiceEvent
    data class Caption(val text: String) : VoiceEvent
    data object TurnComplete : VoiceEvent
    data object Interrupted : VoiceEvent

    /** The agent heard "Xong rồi" and wants the current step checked. */
    data class CheckRequested(val callId: String) : VoiceEvent
}

/** [events] completes when the connection ends and can be collected only once. */
interface VoiceSession {
    val events: Flow<VoiceEvent>
    suspend fun sendAudio(pcmBase64: String)
    suspend fun sendFrame(jpegBase64: String)
    suspend fun sendText(text: String)
    suspend fun sendCheckResult(callId: String, result: Map<String, String>)
    fun close()
}

fun interface VoiceConnector {
    suspend fun connect(systemPrompt: String): VoiceSession
}
