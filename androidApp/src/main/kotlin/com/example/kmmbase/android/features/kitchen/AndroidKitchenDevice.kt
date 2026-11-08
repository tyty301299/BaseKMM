package com.example.kmmbase.android.features.kitchen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.kmmbase.domain.device.KitchenDevice
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private const val MIC_RATE = 16_000
private const val SPEAKER_RATE = 24_000
private const val MIC_CHUNK_BYTES = 3_200 // 100 ms of 16-bit mono audio.
private const val FRAME_INTERVAL_MS = 500L
private const val JPEG_QUALITY = 70

internal class AndroidKitchenDevice(context: Context) : KitchenDevice {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val playbackExecutor = Executors.newSingleThreadExecutor()
    private val playbackGeneration = AtomicInteger()

    @Volatile private var latestFrame: String? = null

    /** Width / height of the frames sent to Gemini, needed to place boxes over the preview. */
    @Volatile var frameAspect: Float = 9f / 16f
        private set
    @Volatile private var lastFrameAt = 0L
    @Volatile private var micRunning = false
    private var micThread: Thread? = null
    private var speaker: AudioTrack? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Rebinding replaces the previous use cases, so this is safe after rotation. */
    fun bindCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val provider = future.get().also { cameraProvider = it }
                val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                // Same selector on both, so overlay boxes computed from frames line up with the preview.
                val resolution = ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    ),
                ).build()
                val preview = Preview.Builder()
                    .setResolutionSelector(resolution)
                    .build()
                    .also { it.surfaceProvider = surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor, ::onFrame) }
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, analysis)
            } catch (e: Exception) {
                // No frames arrive; the store then reports AppError.CAMERA.
                Log.e("KitchenDevice", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun onFrame(image: ImageProxy) {
        image.use {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_INTERVAL_MS) return
            lastFrameAt = now
            latestFrame = encode(it)
        }
    }

    private fun encode(image: ImageProxy): String {
        val source = image.toBitmap()
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        frameAspect = rotated.width.toFloat() / rotated.height
        val bytes = ByteArrayOutputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    override fun latestFrameJpegBase64(): String? = latestFrame

    // KitchenScreen only dispatches Start once RECORD_AUDIO is granted.
    @SuppressLint("MissingPermission")
    override fun startMicrophone(onChunk: (String) -> Unit) {
        if (micRunning) return
        val minBuffer = AudioRecord.getMinBufferSize(MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Enables echo cancellation.
            MIC_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, MIC_CHUNK_BYTES * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e("KitchenDevice", "Microphone unavailable")
            return
        }
        useSpeakerphone(true)
        micRunning = true
        micThread = thread(name = "kitchen-mic") {
            val buffer = ByteArray(MIC_CHUNK_BYTES)
            recorder.startRecording()
            while (micRunning) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) onChunk(Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP))
            }
            recorder.stop()
            recorder.release()
        }
    }

    override fun stopMicrophone() {
        if (!micRunning) return
        micRunning = false
        micThread?.join(500)
        micThread = null
        useSpeakerphone(false)
    }

    // Communication mode would otherwise route the agent's voice to the earpiece.
    @Suppress("DEPRECATION")
    private fun useSpeakerphone(enabled: Boolean) {
        audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = enabled
    }

    override fun playAudio(pcmBase64: String) {
        val generation = playbackGeneration.get()
        playbackExecutor.execute {
            if (generation != playbackGeneration.get()) return@execute
            val bytes = Base64.decode(pcmBase64, Base64.NO_WRAP)
            speakerTrack().write(bytes, 0, bytes.size)
        }
    }

    override fun stopPlayback() {
        val generation = playbackGeneration.incrementAndGet()
        playbackExecutor.execute {
            if (generation != playbackGeneration.get()) return@execute
            speaker?.run {
                pause()
                flush()
                play()
            }
        }
    }

    private fun speakerTrack(): AudioTrack = speaker ?: AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SPEAKER_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(SPEAKER_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                SPEAKER_RATE,
            ),
        )
        .build()
        .also {
            it.play()
            speaker = it
        }

    override fun shutdown() {
        stopMicrophone()
        ContextCompat.getMainExecutor(appContext).execute { cameraProvider?.unbindAll() }
        cameraExecutor.shutdown()
        playbackGeneration.incrementAndGet()
        playbackExecutor.execute {
            speaker?.release()
            speaker = null
        }
        playbackExecutor.shutdown()
    }
}
