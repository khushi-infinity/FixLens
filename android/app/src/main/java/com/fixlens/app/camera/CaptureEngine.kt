package com.fixlens.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Still-capture helper for CameraX. Produces JPEG files in cacheDir/captures —
 * a staging area only. Images become permanent captures only after the user
 * confirms, via [com.fixlens.app.data.CaptureStore.saveConfirmedCapture].
 */
class CaptureEngine(private val context: Context) {

    private val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    val rotation: Int
        get() = imageCapture.targetRotation

    fun useCase(): ImageCapture = imageCapture

    /** Captures a JPEG to the cache staging area; throws on failure. */
    suspend fun captureStill(): File = suspendCancellableCoroutine { continuation ->
        val stagingDir = File(context.cacheDir, "captures").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val file = File(stagingDir, "IMG_$timestamp.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            options,
            { runnable -> runnable.run() },
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // The staged JPEG keeps its EXIF rotation; see exifRotationDegrees().
                    if (continuation.isActive) continuation.resume(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.w(TAG, "Capture failed", exception)
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            },
        )
    }

    /**
     * Reads EXIF rotation from the JPEG. Callers that need pixel-perfect
     * orientation (e.g. review thumbnails) rotate via [rotatedBitmap]; the
     * staged file keeps its EXIF so the backend receives orientation metadata.
     */
    fun exifRotationDegrees(file: File): Int {
        val exif = ExifInterface(file)
        return exif.rotationDegrees
    }

    /** Decodes [file] and applies its EXIF rotation, returning an upright bitmap. */
    fun rotatedBitmap(file: File): android.graphics.Bitmap? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        val rotation = exifRotationDegrees(file)
        if (rotation == 0) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return android.graphics.Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true,
        )
    }

    private companion object {
        const val TAG = "CaptureEngine"
    }
}
