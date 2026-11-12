package com.example.kmmbase.android.platform.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Decode to at most 1024px; never retain a full-resolution bitmap for the preview or upload. */
internal fun preparePersonPhoto(context: Context, uri: Uri): ByteArray {
    val resolver = context.contentResolver
    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val ratio = minOf(1.0, 1024.0 / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * ratio).toInt()), max(1, (info.size.height * ratio).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (max(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Invalid photo")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val transform = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        if (transform.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
            .also { if (it !== decoded) decoded.recycle() }
    }
    return try {
        ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            output.toByteArray()
        }
    } finally { bitmap.recycle() }
}

internal fun personVideoDuration(context: Context, uri: Uri): Double {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0) / 1000.0
    } finally { retriever.release() }
}

internal fun preparePersonVideoFrame(context: Context, uri: Uri, seconds: Double): ByteArray {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val time = (seconds.coerceAtLeast(0.0) * 1_000_000).toLong()
        val width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1024
        val height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1024
        val ratio = minOf(1.0, 1024.0 / max(width, height).coerceAtLeast(1))
        val frame = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(time,
            android.media.MediaMetadataRetriever.OPTION_CLOSEST, max(1, (width * ratio).toInt()), max(1, (height * ratio).toInt()))
        else retriever.getFrameAtTime(time, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
        requireNotNull(frame)
        val outputRatio = minOf(1.0, 1024.0 / max(frame.width, frame.height))
        val scaled = if (outputRatio < 1) Bitmap.createScaledBitmap(frame, max(1, (frame.width * outputRatio).toInt()), max(1, (frame.height * outputRatio).toInt()), true) else frame
        try {
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 85, output))
                output.toByteArray()
            }
        } finally {
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
        }
    } finally { retriever.release() }
}
