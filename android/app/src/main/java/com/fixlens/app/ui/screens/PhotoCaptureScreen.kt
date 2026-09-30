package com.fixlens.app.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape

import com.fixlens.app.ui.theme.paperSurface

import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fixlens.app.FixLensApp
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.CaptureEngine
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.data.CaptureSource
import com.fixlens.app.data.CaptureStore
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.DiagnoseResponseDto
import com.fixlens.app.ui.BackendStatus
import com.fixlens.app.ui.theme.FixLensColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun rememberAppContainer(): com.fixlens.app.di.AppContainer {
    val context = LocalContext.current.applicationContext
    return remember(context) { (context as FixLensApp).appContainer }
}

/**
 * Photo Mode entry point: capture a photo, review, confirm.
 * Source is tagged PHOTO_MODE in the local repair history.
 * Phase 4: Start Fix routes the confirmed diagnosis into guided repair.
 * Phase 6: monetization gate before analysis — free plan = 3 scans/month;
 * when exhausted, Pro or a credit unlocks the scan (paywall, never a
 * hard block on the camera itself).
 */
@Composable
fun PhotoCaptureScreen(
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onStartFix: (DiagnoseResponseDto) -> Unit = {},
) {
    val container = rememberAppContainer()
    MonetizedCaptureFlow(
        modeTitle = "Scan a Photo",
        source = CaptureSource.PHOTO_MODE,
        container = container,
        onDone = onDone,
        onDismiss = onDismiss,
        onStartFix = onStartFix,
    )
}

/**
 * Live Camera Mode entry point: same capture flow on the live preview.
 * Source is tagged LIVE_CAMERA in the local repair history.
 */
@Composable
fun LiveCameraScreen(
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onStartFix: (DiagnoseResponseDto) -> Unit = {},
) {
    val container = rememberAppContainer()
    MonetizedCaptureFlow(
        modeTitle = "Live Camera",
        source = CaptureSource.LIVE_CAMERA,
        container = container,
        onDone = onDone,
        onDismiss = onDismiss,
        onStartFix = onStartFix,
    )
}

/**
 * Phase 6 monetization wrapper around [CaptureFlowScreen]: evaluates the
 * allowance BEFORE the user invests in capturing, shows the paywall when
 * blocked, counts a scan only after the backend accepted the image, and
 * spends a credit exactly when a non-Pro user scans beyond the allowance.
 */
@Composable
private fun MonetizedCaptureFlow(
    modeTitle: String,
    source: CaptureSource,
    container: com.fixlens.app.di.AppContainer,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onStartFix: (DiagnoseResponseDto) -> Unit,
) {
    val billing = container.billingRepository
    val billingState by billing.state.collectAsState()
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    val scope = rememberCoroutineScope()

    if (paywallReason != null) {
        PaywallScreen(
            repository = billing,
            reason = paywallReason!!, 
            onDismiss = { paywallReason = null },
            onEntitled = { paywallReason = null },
        )
        return
    }

    CaptureFlowScreen(
        modeTitle = modeTitle,
        source = source,
        captureStore = container.captureStore,
        api = container.api,
        onDone = onDone,
        onDismiss = onDismiss,
        onStartFix = onStartFix,
        scansRemaining = if (billingState.isPro) null else billingState.freeScansRemaining,
        onScanBlocked = {
            // User confirmed "Use image" while exhausted: paywall, not analysis.
            paywallReason = PaywallReason.FREE_SCANS_EXHAUSTED
        },
        onScanAllowed = {
            scope.launch {
                when (val decision = billing.evaluateScan()) {
                    is com.fixlens.app.billing.BillingGate.Decision.Allow -> {
                        if (decision.reason == com.fixlens.app.billing.BillingGate.Reason.CREDIT_AVAILABLE) {
                            billing.consumeCredit()
                        }
                        billing.recordScan()
                    }
                    else -> { /* raced to exhausted; the next attempt pays */ }
                }
            }
        },
    )
}

/**
 * Capture flow shared by Photo Mode and Live Camera Mode.
 *
 * Phase 1 boundary: the confirmed image is stored locally and the backend is
 * probed for liveness only; no AI diagnosis is requested or shown.
 */
@Composable
fun CaptureFlowScreen(
    modeTitle: String,
    source: CaptureSource,
    captureStore: CaptureStore,
    api: ApiClient,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    onStartFix: (DiagnoseResponseDto) -> Unit = {},
    scansRemaining: Int? = null,
    onScanBlocked: () -> Unit = {},
    onScanAllowed: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val permission = rememberCameraPermissionState()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var backendStatus by remember { mutableStateOf<BackendStatus>(BackendStatus.Idle) }
    var diagnosisResult by remember { mutableStateOf<DiagnoseResponseDto?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    val billing = LocalContext.current.applicationContext.let { ctx ->
        (ctx as? com.fixlens.app.FixLensApp)?.appContainer?.billingRepository
    }

    val previewSession = remember {
        PreviewSession(context, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    val captureEngine = remember { CaptureEngine(context) }

    // (Re)bind the camera whenever permission, lens, or flow state changes.
    LaunchedEffect(permission.phase, lensFacing, capturedFile, diagnosisResult, analysisError) {
        if (diagnosisResult == null && analysisError == null &&
            permission.phase == CameraPermissionPhase.GRANTED &&
            capturedFile == null
        ) {
            val result = previewSession.start(
                lifecycleOwner = lifecycleOwner,
                lensFacing = lensFacing,
                captureUseCase = captureEngine.useCase(),
            ) { request -> surfaceRequest = request }
            result.onFailure { captureError = it.message ?: "Camera unavailable" }
        } else {
            previewSession.stop()
            if (permission.phase == CameraPermissionPhase.UNKNOWN) permission.launch()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewSession.stop()
            capturedFile?.delete()
        }
    }

    when {
        diagnosisResult != null -> DiagnosisResultScreen(
            result = diagnosisResult!!,
            onStartFix = { onStartFix(diagnosisResult!!) },
            onDismiss = onDone,
            // Better-view "Try Again" returns to the live camera for a fresh
            // capture; the stored capture is intentionally not reused so the
            // model always sees a new, intentional frame (spec §13).
            onRetry = {
                diagnosisResult = null
                analysisError = null
                capturedFile = null
            },
        )
        analysisError != null -> ErrorState(
            title = "Analysis unavailable",
            detail = analysisError,
            actionLabel = "Try again",
            onAction = {
                analysisError = null
                capturedFile = null
            },
        )
        backendStatus == BackendStatus.Checking -> AnalyzingState(onCancel = onDismiss)
        permission.phase == CameraPermissionPhase.DENIED -> ErrorState(
            title = "Camera permission needed",
            detail = "FixLens uses the camera to scan the object you want help with.",
            actionLabel = "Allow camera",
            onAction = { permission.launch() },
        )
        permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED -> ErrorState(
            title = "Camera permission blocked",
            detail = "Camera access is disabled for FixLens in system settings.",
            actionLabel = "Open settings",
            onAction = { permission.openSettings() },
        )
        capturedFile != null -> CapturedImageReview(
            file = capturedFile!!,
            onRetake = {
                capturedFile?.delete()
                capturedFile = null
            },
            onConfirm = {
                val file = capturedFile
                if (file == null) {
                    captureError = "No capture to save"
                } else scope.launch {
                    // Phase 6: allowance check at the moment of spend.
                    val decision = billing?.evaluateScan()
                    if (decision is com.fixlens.app.billing.BillingGate.Decision.Paywall) {
                        onScanBlocked()
                        return@launch
                    }
                    backendStatus = BackendStatus.Checking
                    val wireMode = if (source == CaptureSource.LIVE_CAMERA) "LIVE" else "PHOTO"
                    try {
                        captureStore.saveConfirmedCapture(file, source)
                        // Phase 8: upload at model resolution, not camera
                        // resolution — identical AI results, much faster wire.
                        val uploadFile = withContext(Dispatchers.IO) {
                            com.fixlens.app.imaging.UploadPrep.prepare(context, file)
                        }
                        val response = api.diagnose(uploadFile, mode = wireMode)
                        if (uploadFile != file) uploadFile.delete()
                        file.delete()
                        capturedFile = null
                        onScanAllowed() // counts scan / spends credit (backend accepted)
                        diagnosisResult = response
                    } catch (e: ApiError) {
                        analysisError = friendlyAnalysisMessage(e)
                        backendStatus = BackendStatus.Idle
                    } catch (e: Exception) {
                        analysisError =
                            "We couldn't analyze this image. Try again or capture the object from a different angle."
                        backendStatus = BackendStatus.Idle
                    }
                }
            },
            backendStatus = backendStatus,
        )
        captureError != null -> ErrorState(
            title = "Camera problem",
            detail = captureError,
            actionLabel = "Try again",
            onAction = {
                captureError = null
                capturedFile = null
            },
        )
        permission.phase == CameraPermissionPhase.GRANTED -> CameraLivePreview(
            modeTitle = modeTitle,
            surfaceRequest = surfaceRequest,
            isCapturing = isCapturing,
            hasFrontCamera = previewSession.hasFrontCamera(),
            onBack = onDismiss,
            onFlipLens = {
                lensFacing =
                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
            },
            onCapture = {
                if (isCapturing) return@CameraLivePreview
                isCapturing = true
                scope.launch {
                    try {
                        val file = captureEngine.captureStill()
                        capturedFile = file
                    } catch (e: Exception) {
                        captureError = e.message ?: "Capture failed"
                    } finally {
                        isCapturing = false
                    }
                }
            },
        )
        else -> LoadingState("Preparing camera…")
    }
}

@Composable
private fun CameraLivePreview(
    modeTitle: String,
    surfaceRequest: SurfaceRequest?,
    isCapturing: Boolean,
    hasFrontCamera: Boolean,
    onBack: () -> Unit,
    onFlipLens: () -> Unit,
    onCapture: () -> Unit,
) {
    com.fixlens.app.ui.WorkshopCamera(
        title = modeTitle, note = "Keep the object and any damaged parts in view.",
        surfaceRequest = surfaceRequest, isCapturing = isCapturing, hasFrontCamera = hasFrontCamera,
        onBack = onBack, onFlipLens = onFlipLens, onCapture = onCapture,
    )
}

@Composable
private fun CapturedImageReview(
    file: File,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    backendStatus: BackendStatus,
) {
    Column(modifier = Modifier.fillMaxSize().paperSurface().statusBarsPadding()) {
        val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = file) {
            value = withContext(Dispatchers.IO) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val imageBitmap: ImageBitmap? = bitmap?.asImageBitmap()
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Captured image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            OutlinedButton(onClick = onRetake, enabled = backendStatus !is BackendStatus.Checking, shape = RoundedCornerShape(10.dp)) {
                Text("Retake")
            }
            Button(
                onClick = onConfirm,
                enabled = backendStatus !is BackendStatus.Checking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Use image")
            }
        }
        if (backendStatus is BackendStatus.Checking) {
            Text(
                text = "Analyzing — one photo, one check. Nothing is scanned continuously.",
                color = FixLensColors.Ink,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}

/**
 * Maps typed transport/backend errors to user-appropriate messages.
 * The backend's controlled error text is already user-friendly; everything
 * else gets a generic honest message. Raw stack details never reach the UI.
 */
private fun friendlyAnalysisMessage(error: ApiError): String = when (error) {
    is ApiError.NotConfigured ->
        "The backend is not configured on this device. See docs/DEVICE_SETUP.md."
    is ApiError.Timeout ->
        "The AI is taking longer than usual right now. Please try again — it often succeeds on a second attempt."
    is ApiError.Unreachable ->
        "Could not reach the FixLens backend. Check your connection and adb reverse, then try again."
    is ApiError.Http -> error.message // backend controlled detail (400/502/503)
    is ApiError.InvalidResponse ->
        "We couldn't analyze this image reliably. Try again or capture it from a different angle."
}
