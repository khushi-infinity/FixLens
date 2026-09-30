package com.fixlens.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Phase 8 performance: uploads are downscaled client-side to the same
 * resolution the backend sends to the vision model (backend
 * MODEL_MAX_DIMENSION = 1280 px longest edge). Uploading multi-megabyte
 * camera originals adds seconds of transfer with zero effect on AI results —
 * the backend would resize them to this exact size anyway.
 *
 * The prepared file is written to cacheDir and can be deleted by the caller
 * after the upload completes; the original capture is untouched.
 */
object UploadPrep {

    /** Matches backend/app/imaging.py MODEL_MAX_DIMENSION. */
    const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 85

    /**
     * Returns a downscaled JPEG copy of [source] (longest edge ≤
     * [MAX_DIMENSION]). If the source is already small enough or cannot be
     * decoded, the original file is returned unchanged — upload never fails
     * because of preparation.
     */
    fun prepare(context: Context, source: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0 || longest <= MAX_DIMENSION) return source

        // Power-of-two sample first (fast, low memory), then exact-scale.
        var sample = 1
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, decodeOpts) ?: return source

        val exactScale = MAX_DIMENSION.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (exactScale < 1f) {
            val matrix = Matrix().apply { postScale(exactScale, exactScale) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }

        val dest = File(context.cacheDir, "upload_${source.name}")
        runCatching {
            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return if (dest.exists() && dest.length() > 0) dest else source
    }
}
