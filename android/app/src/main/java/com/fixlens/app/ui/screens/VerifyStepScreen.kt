package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface

import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.CaptureEngine
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.VerificationStates
import com.fixlens.app.network.VerifyResponseDto
import com.fixlens.app.ui.theme.FixLensColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Phase 5 camera-based step verification (spec §17.7). Flow:
 *
 * USER ACTION ("Verify this step") -> CAPTURE/SCAN (one explicit frame) ->
 * AI VERIFICATION (single backend call) -> PASS / FAIL / UNCERTAIN result.
 *
 * Verification is explicitly user-triggered: one frame per request, never a
 * stream (spec §15). The expected state comes from the repair step; the
 * result is mapped onto the engine's VerificationResult seam by the caller.
 * Result wordings are the exact spec strings.
 */
@Composable
fun VerifyStepScreen(
    api: ApiClient,
    stepNumber: Int,
    demo: DemoContext? = null,
    totalSteps: Int,
    stepTitle: String,
    stepAction: String,
    targetComponent: String,
    expectedState: String,
    onVerified: (explanation: String) -> Unit,
    onIncomplete: (explanation: String) -> Unit,
    onUncertain: (betterViewInstruction: String, explanation: String) -> Unit,
    onBack: () -> Unit,
) {
    // Local error/loading states so the result view can stay mounted while
    // offering "Try a different view" without double-advancing the engine.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val permission = rememberCameraPermissionState()

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<VerifyResponseDto?>(null) }

    // System BACK during the capture stage returns to the step rather than
    // popping the whole guided-repair route (caught by the Phase 7 demo E2E).
    androidx.activity.compose.BackHandler(enabled = result == null && verifyError == null) {
        onBack()
    }
    // True only while preview AND still-capture are bound: tapping the shutter
    // before bind completes would throw "Not bound to a valid Camera".
    var bindReady by remember { mutableStateOf(false) }

    val previewSession = remember {
        PreviewSession(context, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    val captureEngine = remember { CaptureEngine(context) }

    // (Re)bind the camera while the user is capturing; stop once a frame exists.
    LaunchedEffect(permission.phase, lensFacing, capturedFile, result, verifyError) {
        if (result == null && verifyError == null &&
            permission.phase == CameraPermissionPhase.GRANTED &&
            capturedFile == null
        ) {
            bindReady = false
            // One automatic retry: rapid re-entry after another camera screen
            // can transiently fail to bind on the first attempt.
            val bound = previewSession.start(
                lifecycleOwner = lifecycleOwner,
                lensFacing = lensFacing,
                captureUseCase = captureEngine.useCase(),
            ) { request -> surfaceRequest = request }
                .onSuccess { bindReady = true }
                .recoverCatching {
                    kotlinx.coroutines.delay(600)
                    previewSession.start(
                        lifecycleOwner = lifecycleOwner,
                        lensFacing = lensFacing,
                        captureUseCase = captureEngine.useCase(),
                    ) { request -> surfaceRequest = request }.getOrThrow()
                }
            bound
                .onSuccess { bindReady = true }
                .onFailure { captureError = it.message ?: "Camera unavailable" }
        } else {
            bindReady = false
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
        result != null -> VerificationResultView(
            result = result!!,
            expectedState = expectedState,
            stepLabel = "Step $stepNumber of $totalSteps",
            retrying = verifying,
            onTryDifferentView = {
                // UNCERTAIN retry: fresh capture, never resend the same frame.
                result = null
                verifyError = null
                capturedFile = null
            },
            onKeepWorking = {
                capturedFile?.delete()
                capturedFile = null
                val r = result
                result = null
                verifyError = null
                when {
                    r == null -> onBack()
                    r.state == VerificationStates.PASS -> onVerified(r.explanation)
                    r.state == VerificationStates.FAIL -> onIncomplete(r.explanation)
                    else -> onUncertain(r.betterViewInstruction ?: r.explanation, r.explanation)
                }
            },
        )

        verifyError != null -> ErrorState(
            title = "Verification unavailable",
            detail = verifyError,
            actionLabel = "Try again",
            onAction = {
                verifyError = null
                capturedFile = null
            },
        )

        verifying -> Column(
            modifier = Modifier
                .fillMaxSize()
                .paperSurface()
            .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = FixLensColors.Terracotta, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Comparing with the expected result…",
                style = MaterialTheme.typography.titleMedium,
                color = FixLensColors.Ink,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "One photo, one check, nothing is analyzed continuously.",
                style = MaterialTheme.typography.bodyMedium,
                color = FixLensColors.MutedInk,
                textAlign = TextAlign.Center,
            )
        }

        permission.phase == CameraPermissionPhase.DENIED -> ErrorState(
            title = "Camera permission needed",
            detail = "FixLens uses the camera to check the step you just completed.",
            actionLabel = "Allow camera",
            onAction = { permission.launch() },
        )

        permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED -> ErrorState(
            title = "Camera permission blocked",
            detail = "Camera access is disabled for FixLens in system settings.",
            actionLabel = "Open settings",
            onAction = { permission.openSettings() },
        )

        capturedFile != null -> VerificationReview(
            file = capturedFile!!,
            busy = false,
            onRetake = {
                capturedFile?.delete()
                capturedFile = null
            },
            onConfirm = {
                val file = capturedFile
                if (file == null || verifying) return@VerificationReview
                verifying = true
                scope.launch {
                    try {
                        // Phase 7: Demo Mode returns the scripted outcome with
                        // NO backend call and NO upload of the capture.
                        var uploadFile: File? = null
                        val response = if (demo != null) {
                            com.fixlens.app.demo.DemoMappers.toVerifyResponse(
                                demo.scenario.diagnosis,
                                stepNumber,
                            )
                        } else {
                            // Phase 8: downscale to model resolution before upload.
                            val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                com.fixlens.app.imaging.UploadPrep.prepare(context, file)
                            }
                            uploadFile = prepared
                            api.verify(
                                image = prepared,
                                stepNumber = stepNumber,
                                expectedState = expectedState,
                                stepAction = stepAction,
                                targetComponent = targetComponent,
                                userConfirmsDone = true,
                            )
                        }
                        result = response
                        uploadFile?.takeIf { it != file }?.delete()
                    } catch (e: ApiError) {
                        verifyError = friendlyVerifyMessage(e)
                    } catch (e: Exception) {
                        verifyError = "We couldn't verify this step right now. Please try again."
                    } finally {
                        verifying = false
                    }
                }
            },
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

        permission.phase == CameraPermissionPhase.GRANTED -> VerificationCapture(
            surfaceRequest = surfaceRequest,
            stepLabel = "Step $stepNumber of $totalSteps, $stepTitle",
            expectedState = expectedState,
            isCapturing = isCapturing,
            hasFrontCamera = previewSession.hasFrontCamera(),
            onBack = onBack,
            demo = demo,
            onFlipLens = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            onCapture = {
                if (isCapturing || !bindReady) return@VerificationCapture
                isCapturing = true
                scope.launch {
                    try {
                        capturedFile = captureEngine.captureStill()
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

/** Live capture view with a framing hint naming what the model must see. */
@Composable
private fun VerificationCapture(
    surfaceRequest: SurfaceRequest?,
    stepLabel: String,
    expectedState: String,
    isCapturing: Boolean,
    hasFrontCamera: Boolean,
    onBack: () -> Unit,
    onFlipLens: () -> Unit,
    onCapture: () -> Unit,
    demo: DemoContext? = null,
) {
    com.fixlens.app.ui.WorkshopCamera(
        title = stepLabel, note = "Show the result: $expectedState",
        surfaceRequest = surfaceRequest, isCapturing = isCapturing, hasFrontCamera = hasFrontCamera,
        onBack = onBack, onFlipLens = onFlipLens, onCapture = onCapture,
        captureDescription = "Capture verification photo", scripted = demo != null,
    )
}

/** Review + confirm before the single verification call is spent. */
@Composable
private fun VerificationReview(
    file: File,
    busy: Boolean,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
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
                    contentDescription = "Verification capture",
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
            OutlinedButton(onClick = onRetake, enabled = !busy, shape = RoundedCornerShape(10.dp)) {
                Text("Retake")
            }
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixLensColors.Terracotta,
                    contentColor = FixLensColors.Cream,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Verify This Step")
            }
        }
    }
}

/**
 * The three spec outcomes with their exact wordings:
 *  PASS      -> "Step complete."
 *  FAIL      -> "This step doesn't appear complete. The screw is still loose."
 *  UNCERTAIN -> "I can't verify this clearly. Move the camera closer."
 * The model's explanation is always shown beneath as supporting evidence,
 * the claim and the evidence are never conflated.
 */
@Composable
private fun VerificationResultView(
    result: VerifyResponseDto,
    expectedState: String,
    stepLabel: String,
    retrying: Boolean,
    onTryDifferentView: () -> Unit,
    onKeepWorking: () -> Unit,
) {
    val headline: String
    val bodyText: String
    val tint: Color
    val icon: androidx.compose.ui.graphics.vector.ImageVector
    when (result.state) {
        VerificationStates.PASS -> {
            headline = "Step complete."
            bodyText = result.explanation
            tint = FixLensColors.Terracotta
            icon = Icons.Filled.CheckCircle
        }
        VerificationStates.FAIL -> {
            headline = "This step doesn't appear complete. The screw is still loose."
            bodyText = result.explanation
            tint = FixLensColors.Danger
            icon = Icons.Filled.ErrorOutline
        }
        else -> {
            headline = "I can't verify this clearly. Move the camera closer."
            bodyText = result.betterViewInstruction ?: result.explanation
            tint = FixLensColors.Terracotta
            icon = Icons.Filled.HelpOutline
        }
    }

    // Voice + haptics on the verdict: the result is spoken (on-device TTS)
    // and felt (PASS confirms, FAIL rejects) as well as shown.
    val view = LocalView.current
    val voiceContext = LocalContext.current
    val voice = remember { VoiceGuide(voiceContext) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { voice.shutdown() }
    }
    androidx.compose.runtime.LaunchedEffect(result.state) {
        voice.speak(headline)
        when (result.state) {
            VerificationStates.PASS -> view.hapticConfirm()
            VerificationStates.FAIL -> view.hapticReject()
            else -> view.hapticTick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = FixLensColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
            colors = CardDefaults.cardColors(containerColor = FixLensColors.Cream),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "WHAT FIXLENS SAW",
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.MutedInk,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.Ink,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Expected after this step: $expectedState",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.MutedInk,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stepLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.MutedInk,
        )
        Spacer(modifier = Modifier.height(24.dp))
        when (result.state) {
            VerificationStates.PASS -> Button(
                onClick = onKeepWorking,
                enabled = !retrying,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixLensColors.Terracotta,
                    contentColor = FixLensColors.Cream,
                ),
            ) { Text("Next Step", style = MaterialTheme.typography.labelLarge) }

            VerificationStates.FAIL -> {
                Button(
                    onClick = onKeepWorking,
                    enabled = !retrying,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixLensColors.Terracotta,
                        contentColor = FixLensColors.Cream,
                    ),
                ) { Text("Keep Working On It", style = MaterialTheme.typography.labelLarge) }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onTryDifferentView, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                    Text("Try a different view", color = FixLensColors.MutedInk)
                }
            }

            else -> {
                Button(
                    onClick = onTryDifferentView,
                    enabled = !retrying,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixLensColors.Terracotta,
                        contentColor = FixLensColors.Cream,
                    ),
                ) { Text("Try Again", style = MaterialTheme.typography.labelLarge) }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onKeepWorking, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip verification for now", color = FixLensColors.MutedInk)
                }
            }
        }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

private fun friendlyVerifyMessage(error: ApiError): String = when (error) {
    is ApiError.NotConfigured ->
        "The backend is not configured on this device. See docs/DEVICE_SETUP.md."
    is ApiError.Timeout ->
        "The AI is taking longer than usual right now. Please try again, it often succeeds on a second attempt."
    is ApiError.Unreachable ->
        "Could not reach the FixLens backend. Check your connection and adb reverse, then try again."
    is ApiError.Http -> error.message
    is ApiError.InvalidResponse ->
        "The verification result didn't make sense. Please try again."
}
