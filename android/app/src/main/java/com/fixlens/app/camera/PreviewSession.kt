package com.fixlens.app.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Lifecycle-safe CameraX preview shared by Photo Mode and Live Camera Mode.
 *
 * The [SurfaceRequest] flow is consumed by the Compose camera preview; re-binding
 * (lens flip) produces a new request. Unbinds happen through the lifecycle owner,
 * so backgrounding the app releases the camera correctly.
 */
class PreviewSession(
    private val context: Context,
    private val mainExecutor: Executor,
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: androidx.camera.core.Camera? = null
    /** Use cases THIS session bound, stop() unbinds only these, never the
     * whole provider (unbindAll() would kill another screen's live binding
     * during screen transitions that overlap by a frame). */
    private var boundUseCases: List<androidx.camera.core.UseCase> = emptyList()

    /** Starts (or restarts) the preview with the given lens, optionally binding still capture. */
    suspend fun start(
        lifecycleOwner: LifecycleOwner,
        lensFacing: Int,
        captureUseCase: ImageCapture? = null,
        onRequest: (SurfaceRequest) -> Unit,
    ): Result<Unit> {
        val provider: ProcessCameraProvider = cameraProvider ?: run {
            val fetched: ProcessCameraProvider = try {
                suspendCancellableCoroutine { continuation ->
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener(
                        {
                            try {
                                continuation.resume(future.get())
                            } catch (t: Throwable) {
                                continuation.resumeWithException(t)
                            }
                        },
                        mainExecutor,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Camera provider unavailable", t)
                return Result.failure(t)
            }
            cameraProvider = fetched
            fetched
        }

        return try {
            // Release only our previous use cases, then rebind fresh.
            cameraProvider?.unbind(*boundUseCases.toTypedArray())
            boundUseCases = emptyList()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(mainExecutor) { request ->
                    onRequest(request)
                }
            }
            val useCases = buildList {
                add(preview)
                captureUseCase?.let { add(it) }
            }
            activeCamera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                *useCases.toTypedArray(),
            )
            boundUseCases = useCases
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "Camera bind failed", t)
            Result.failure(t)
        }
    }

    fun hasFrontCamera(): Boolean =
        cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true

    fun stop() {
        activeCamera = null
        // Targeted unbind: another camera screen may have just bound its own
        // use cases on the shared provider, a global unbindAll() here would
        // race with and kill its binding (observed on the emulator as
        // "Not bound to a valid Camera [ImageCapture:...]" right after Show Me
        // closed and the verification capture screen opened).
        cameraProvider?.unbind(*boundUseCases.toTypedArray())
        boundUseCases = emptyList()
    }

    private companion object {
        const val TAG = "PreviewSession"
    }
}
