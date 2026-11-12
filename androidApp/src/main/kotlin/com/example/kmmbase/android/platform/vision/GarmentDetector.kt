package com.example.kmmbase.android.platform.vision

import com.example.kmmbase.android.platform.camera.CameraFrameSource
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import java.util.concurrent.Executor

/** A broad fashion-goods detector: it cannot distinguish a shirt from all other accessories. */
internal data class GarmentRegion(val left: Float, val top: Float, val right: Float, val bottom: Float, val aspect: Float)

internal class GarmentDetector(private val onUpdate: (GarmentRegion?, String) -> Unit) : AutoCloseable {
    private val detector = ObjectDetection.getClient(ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableClassification().build())
    private val main = Handler(Looper.getMainLooper())
    private val direct = Executor { it.run() }
    @Volatile private var active = false
    @Volatile private var fashionOnly = true
    @Volatile private var generation = 0
    private var misses = 0

    fun setActive(value: Boolean, fashionOnly: Boolean = true) {
        if (active == value && this.fashionOnly == fashionOnly) return
        active = value
        this.fashionOnly = fashionOnly
        generation++
        misses = 0
        onUpdate(null, if (fashionOnly) "Đưa trang phục vào khung hình · đang tìm" else "Đưa vật thể vào khung hình · đang tìm")
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun analyze(image: ImageProxy, frames: CameraFrameSource) {
        val media = image.image
        if (!active || media == null) { frames.offer(image); return }
        val token = generation
        val needsFashion = fashionOnly
        val rotation = image.imageInfo.rotationDegrees
        val width = if (rotation == 90 || rotation == 270) image.height else image.width
        val height = if (rotation == 90 || rotation == 270) image.width else image.height
        try {
            detector.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener(direct) { objects ->
                    if (!active || generation != token) return@addOnSuccessListener
                    val match = objects.firstOrNull { obj -> !needsFashion || obj.labels.any {
                        it.text == PredefinedCategory.FASHION_GOOD && it.confidence >= 0.5f
                    } }
                    if (match != null && width > 0 && height > 0) {
                        misses = 0
                        val rect = match.boundingBox
                        val region = GarmentRegion(rect.left.toFloat() / width, rect.top.toFloat() / height,
                            rect.right.toFloat() / width, rect.bottom.toFloat() / height, width.toFloat() / height)
                        publish(token, region, if (needsFashion) "Đã thấy trang phục · vùng nhận diện ước lượng" else "Đã thấy vật thể · vùng nhận diện ước lượng")
                    } else if (++misses >= 5) {
                        publish(token, null, if (needsFashion) "Chưa thấy trang phục · trải phẳng và đủ sáng" else "Chưa thấy vật thể · đặt vật ở nơi đủ sáng")
                    }
                }
                .addOnFailureListener(direct) { publish(token, null, "Không nhận diện được · hãy thử lại") }
                .addOnCompleteListener(direct) { frames.offer(image) }
        } catch (_: Exception) {
            publish(token, null, "Không nhận diện được · hãy thử lại")
            frames.offer(image)
        }
    }

    private fun publish(token: Int, region: GarmentRegion?, message: String) {
        main.post { if (active && token == generation) onUpdate(region, message) }
    }

    override fun close() { active = false; generation++; detector.close(); main.removeCallbacksAndMessages(null) }
}
