package com.example.kmmbase.domain.device

/**
 * Camera, microphone and speaker, implemented natively on each platform.
 * Media is passed as base64 so Swift never has to convert a Kotlin ByteArray.
 */
interface KitchenDevice {
    /** Newest JPEG from the camera, or null if no frame has arrived yet. Must return quickly. */
    fun latestFrameJpegBase64(): String?

    /** [onChunk] receives 16 kHz, 16-bit mono PCM and may be called from any thread. */
    fun startMicrophone(onChunk: (String) -> Unit)
    fun stopMicrophone()

    /** Queues 24 kHz, 16-bit mono PCM for playback. */
    fun playAudio(pcmBase64: String)

    /** Drops everything queued, used when the user talks over the agent. */
    fun stopPlayback()

    fun shutdown()
}
