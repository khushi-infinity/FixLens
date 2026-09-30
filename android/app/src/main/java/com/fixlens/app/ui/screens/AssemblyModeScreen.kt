package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface

import java.io.File
import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.fixlens.app.FixLensApp
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.CaptureEngine
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.network.ApiClient
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.AssemblyResponseDto
import com.fixlens.app.network.AssemblyStatuses
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
 * Phase 4 assembly mode entry: photograph disassembled parts, then receive a
 * structured assembly plan (or an explicit request for the view that would
 * determine the order — the order is never guessed).
 */
@Composable
fun AssemblyCaptureScreen(
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val container = rememberAppContainer()
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }

    if (paywallReason != null) {
        PaywallScreen(
            repository = container.billingRepository,
            reason = paywallReason!!,
            onDismiss = {
                paywallReason = null
                onDismiss()
            },
            onEntitled = { paywallReason = null },
        )
        return
    }

    AssemblyFlowScreen(
        api = container.api,
        onDismiss = onDismiss,
        onDone = onDone,
        evaluatePremium = {
            val decision = container.billingRepository.evaluatePremium()
            if (decision is com.fixlens.app.billing.BillingGate.Decision.Paywall) {
                paywallReason = PaywallReason.PREMIUM_FEATURE
                false
            } else {
                true
            }
        },
        onPremiumActionAllowed = {
            if (!container.billingRepository.state.value.isPro) {
                container.billingRepository.consumeCreditSuspend()
            }
        },
    )
}

@Composable
private fun AssemblyFlowScreen(
    api: ApiClient,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    evaluatePremium: () -> Boolean = { true },
    onPremiumActionAllowed: () -> Unit = {},
    demo: DemoContext? = null,
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
    var isPlanning by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var assemblyResult by remember { mutableStateOf<AssemblyResponseDto?>(null) }

    // Phase 7: in Demo Mode the assembly result is resolved as soon as the
    // demo capture is taken — deterministically, with no AI/billing calls.
    LaunchedEffect(demo, capturedFile) {
        if (demo != null && capturedFile != null && assemblyResult == null) {
            val d = demo.scenario.diagnosis
            assemblyResult = com.fixlens.app.network.AssemblyResponseDto(
                status = com.fixlens.app.network.AssemblyStatuses.READY,
                assemblyPlan = com.fixlens.app.network.AssemblyPlanDto(
                    objectName = d.objectName,
                    parts = d.components.map {
                        com.fixlens.app.network.ComponentDto(
                            name = it.name,
                            kind = if (it.observed) "OBSERVED" else "INFERRED",
                            status = it.status,
                        )
                    },
                    steps = demo.scenario.steps.mapIndexed { index, s ->
                        com.fixlens.app.network.RepairStepDto(
                            number = index + 1,
                            title = s.title,
                            action = s.action,
                            instruction = s.instruction,
                            targetComponent = s.targetComponent,
                            toolKnown = false,
                            tool = null,
                            toolNote = "No special tool required beyond what the step names.",
                            warning = s.warning,
                            expectedState = s.expectedState,
                            confirmationRequired = true,
                        )
                    },
                    orderConfident = true,
                    requestedView = null,
                    safetyLevel = d.safetyLevel,
                    safetyReason = d.safetyMessage,
                ),
                safety = com.fixlens.app.network.SafetyNoticeDto(
                    decision = "GUIDE",
                    userMessage = d.safetyMessage,
                    professionalType = "NONE",
                ),
                providerUsed = "demo",
                durationMs = 0,
            )
            capturedFile?.delete()
            capturedFile = null
        }
    }

    val previewSession = remember {
        PreviewSession(context, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    val captureEngine = remember { CaptureEngine(context) }

    LaunchedEffect(permission.phase, lensFacing, capturedFile, assemblyResult, planError) {
        if (assemblyResult == null && planError == null &&
            permission.phase == CameraPermissionPhase.GRANTED &&
            capturedFile == null
        ) {
            previewSession.start(
                lifecycleOwner = lifecycleOwner,
                lensFacing = lensFacing,
                captureUseCase = captureEngine.useCase(),
            ) { request -> surfaceRequest = request }
                .onFailure { captureError = it.message ?: "Camera unavailable" }
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
        assemblyResult != null -> AssemblyPlanScreen(
            response = assemblyResult!!,
            onExit = onDone,
            onRetry = {
                // Same honest fresh-capture rule as diagnosis: never resend
                // the rejected photo.
                assemblyResult = null
                planError = null
                capturedFile = null
            },
        )

        planError != null -> ErrorState(
            title = "Assembly planning unavailable",
            detail = planError,
            actionLabel = "Try again",
            onAction = {
                planError = null
                capturedFile = null
            },
        )

        isPlanning -> LoadingState("Identifying the parts…")

        permission.phase == CameraPermissionPhase.DENIED -> ErrorState(
            title = "Camera permission needed",
            detail = "FixLens uses the camera to see the parts you want to assemble.",
            actionLabel = "Allow camera",
            onAction = { permission.launch() },
        )

        permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED -> ErrorState(
            title = "Camera permission blocked",
            detail = "Camera access is disabled for FixLens in system settings.",
            actionLabel = "Open settings",
            onAction = { permission.openSettings() },
        )

        capturedFile != null -> AssemblyImageReview(
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
                    isPlanning = true
                    // Phase 6 premium gate BEFORE generation; aborts the call
                    // (paywall opens) if the free user has no Pro/credits. The
                    // credit is spent only after the gate allows.
                    if (!evaluatePremium()) {
                        isPlanning = false
                        return@launch
                    }
                    onPremiumActionAllowed()
                    try {
                        // Phase 8: upload at model resolution (faster wire).
                        val uploadFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.fixlens.app.imaging.UploadPrep.prepare(context, file)
                        }
                        val response = api.planAssembly(uploadFile)
                        if (uploadFile != file) uploadFile.delete()
                        file.delete()
                        capturedFile = null
                        assemblyResult = response
                    } catch (e: ApiError) {
                        planError = friendlyAssemblyMessage(e)
                    } catch (e: Exception) {
                        planError = "We couldn't identify these parts. Try again or capture them from a different angle."
                    } finally {
                        isPlanning = false
                    }
                }
            },
            isPlanning = isPlanning,
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

        permission.phase == CameraPermissionPhase.GRANTED -> AssemblyCameraPreview(
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
                if (isCapturing) return@AssemblyCameraPreview
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

/**
 * Renders the assembly plan: guided steps when the order is confident, an
 * explicit view request when it is not, and a hard stop for HIGH risk
 * (the backend already discarded that plan).
 */
@Composable
private fun AssemblyPlanScreen(
    response: AssemblyResponseDto,
    onExit: () -> Unit,
    onRetry: () -> Unit,
) {
    val plan = response.assemblyPlan
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "FIXLENS",
            style = MaterialTheme.typography.labelLarge,
            color = FixLensColors.MutedInk,
            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                response.status == AssemblyStatuses.BLOCKED_HIGH_RISK -> {
                    AssemblyStopCard(message = response.safety.userMessage)
                }

                response.status == AssemblyStatuses.NEEDS_BETTER_VIEW && plan != null -> {
                    AssemblyViewRequest(
                        objectName = plan.objectName,
                        parts = plan.parts.map { it.name },
                        requestedView = plan.requestedView ?: "",
                    )
                }

                plan != null -> {
                    Text(
                        text = "ASSEMBLY PLAN",
                        style = MaterialTheme.typography.headlineSmall,
                        color = FixLensColors.Ink,
                    )
                    Text(
                        text = plan.objectName,
                        style = MaterialTheme.typography.titleMedium,
                        color = FixLensColors.Terracotta,
                    )
                    Text(
                        text = "${plan.parts.size} parts identified",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FixLensColors.MutedInk,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Parts list (• observed / ○ inferred, same convention as diagnosis)
                    plan.parts.forEach { part ->
                        val prefix = if (part.kind.equals("INFERRED", ignoreCase = true)) "○ " else "• "
                        val status = part.status?.let { " — $it" } ?: ""
                        Text(
                            text = "$prefix${part.name}$status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FixLensColors.Ink,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    plan.steps.forEach { step ->
                        AssemblyStepCard(step)
                    }
                    if (plan.safetyLevel.equals("MEDIUM", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = response.safety.userMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FixLensColors.Terracotta,
                        )
                    }
                }

                else -> Text(
                    text = "No assembly plan available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FixLensColors.Ink,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (response.status == AssemblyStatuses.NEEDS_BETTER_VIEW) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Try Again") }
            }
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Done") }
        }
    }
}

@Composable
private fun AssemblyStepCard(step: com.fixlens.app.network.RepairStepDto) {
    Surface(
        border = androidx.compose.foundation.BorderStroke(1.dp, FixLensColors.Rule),
        color = FixLensColors.Cream,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "STEP ${step.number}",
                style = MaterialTheme.typography.labelLarge,
                color = FixLensColors.Terracotta,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = FixLensColors.Ink,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = FixLensColors.Ink,
            )
            step.warning?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠ $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.Danger,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You should see: ${step.expectedState}",
                style = MaterialTheme.typography.bodyMedium,
                color = FixLensColors.MutedInk,
            )
        }
    }
}

@Composable
private fun AssemblyViewRequest(
    objectName: String,
    parts: List<String>,
    requestedView: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "I CAN'T DETERMINE THE ORDER YET",
            style = MaterialTheme.typography.headlineSmall,
            color = FixLensColors.Terracotta,
        )
        Text(
            text = objectName,
            style = MaterialTheme.typography.titleMedium,
            color = FixLensColors.Ink,
        )
        Text(
            text = "I can identify these pieces, but I can't safely determine the assembly order from this view.",
            style = MaterialTheme.typography.bodyLarge,
            color = FixLensColors.Ink,
        )
        if (parts.isNotEmpty()) {
            Text(
                text = "Parts I found: ${parts.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = FixLensColors.MutedInk,
            )
        }
        Surface(
        border = androidx.compose.foundation.BorderStroke(1.dp, FixLensColors.Rule),
            color = FixLensColors.Terracotta.copy(alpha = 0.12f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "SHOW ME THE CONNECTION POINT",
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.Terracotta,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = requestedView,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.Ink,
                )
            }
        }
    }
}

@Composable
private fun AssemblyStopCard(message: String) {
    Surface(
        border = androidx.compose.foundation.BorderStroke(1.dp, FixLensColors.Rule),
        color = FixLensColors.Danger.copy(alpha = 0.16f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("STOP", style = MaterialTheme.typography.headlineMedium, color = FixLensColors.Danger)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, color = FixLensColors.Ink)
        }
    }
}

@Composable
private fun AssemblyCameraPreview(
    surfaceRequest: SurfaceRequest?,
    isCapturing: Boolean,
    hasFrontCamera: Boolean,
    onBack: () -> Unit,
    onFlipLens: () -> Unit,
    onCapture: () -> Unit,
) {
    com.fixlens.app.ui.WorkshopCamera(
        title = "Assembly", note = "Lay out the parts with all pieces and holes visible.",
        surfaceRequest = surfaceRequest, isCapturing = isCapturing, hasFrontCamera = hasFrontCamera,
        onBack = onBack, onFlipLens = onFlipLens, onCapture = onCapture,
        captureDescription = "Capture assembly photo",
    )
}

@Composable
private fun AssemblyImageReview(
    file: File,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    isPlanning: Boolean,
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
                    contentDescription = "Captured parts photo",
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
            OutlinedButton(onClick = onRetake, enabled = !isPlanning, shape = RoundedCornerShape(10.dp)) { Text("Retake") }
            Button(
                onClick = onConfirm,
                enabled = !isPlanning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Get assembly steps") }
        }
    }
}

private fun friendlyAssemblyMessage(error: ApiError): String = when (error) {
    is ApiError.NotConfigured ->
        "The backend is not configured on this device. See docs/DEVICE_SETUP.md."
    is ApiError.Timeout ->
        "The AI is taking longer than usual right now. Please try again — it often succeeds on a second attempt."
    is ApiError.Unreachable ->
        "Could not reach the FixLens backend. Check your connection and adb reverse, then try again."
    is ApiError.Http -> error.message
    is ApiError.InvalidResponse ->
        "We couldn't identify these parts reliably. Try again or capture them from a different angle."
}
