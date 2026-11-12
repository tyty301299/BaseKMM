package com.example.kmmbase.android.platform.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import androidx.camera.core.ImageProxy
import com.example.kmmbase.platform.FrameSource
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

private const val JPEG_QUALITY = 70

/**
 * Holds the newest camera frame so [capture] can encode on demand. CameraX delivers frames
 * continuously; only the ones the guide loop asks for are ever compressed.
 */
class CameraFrameSource : FrameSource {
    private val latest = AtomicReference<Bitmap?>(null)

    /** Called from CameraX's analyzer thread for every frame. */
    @Synchronized
    fun offer(image: ImageProxy) {
        val bitmap = image.toBitmap(image.imageInfo.rotationDegrees)
        latest.getAndSet(bitmap)?.takeIf { it != bitmap }?.recycle()
    }

    override suspend fun capture(maxSide: Int): String? = captureFrame(maxSide)

    @Synchronized
    private fun captureFrame(maxSide: Int): String? {
        val source = latest.get() ?: return null
        val scaled = source.scaledTo(maxSide)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        if (scaled != source) scaled.recycle()
        val encoded = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$encoded"
    }

    @Synchronized
    fun clear() {
        latest.getAndSet(null)?.recycle()
    }
}

private fun Bitmap.scaledTo(maxSide: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxSide) return this
    val ratio = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
}

/**
 * YUV_420_888 is the only format guaranteed for `ImageAnalysis`, so convert via NV21 + JPEG rather
 * than assuming RGBA. The frame is rotated upright because the model is shown a single still.
 */
private fun ImageProxy.toBitmap(rotationDegrees: Int): Bitmap {
    val nv21 = when (format) {
        ImageFormat.YUV_420_888 -> yuvToNv21()
        else -> null
    }
    val decoded = if (nv21 != null) {
        val stream = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), 95, stream)
        val bytes = stream.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } else {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    close()
    if (decoded == null) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    if (rotationDegrees == 0) return decoded
    val rotated = Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true,
    )
    if (rotated != decoded) decoded.recycle()
    return rotated
}

private fun ImageProxy.yuvToNv21(): ByteArray {
    val y = planes[0]
    val u = planes[1]
    val v = planes[2]
    val output = ByteArray(width * height * 3 / 2)

    var offset = 0
    val yBuffer = y.buffer
    if (y.rowStride == width) {
        yBuffer.get(output, 0, width * height)
        offset = width * height
    } else {
        for (row in 0 until height) {
            yBuffer.position(row * y.rowStride)
            yBuffer.get(output, offset, width)
            offset += width
        }
    }

    // NV21 interleaves V then U at half resolution.
    val vBuffer = v.buffer
    val uBuffer = u.buffer
    val chromaHeight = height / 2
    val chromaWidth = width / 2
    for (row in 0 until chromaHeight) {
        for (col in 0 until chromaWidth) {
            val index = row * v.rowStride + col * v.pixelStride
            output[offset++] = vBuffer.get(index)
            output[offset++] = uBuffer.get(row * u.rowStride + col * u.pixelStride)
        }
    }
    return output
}
