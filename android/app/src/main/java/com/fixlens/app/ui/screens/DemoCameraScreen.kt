package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.*

import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.ui.theme.FixLensColors

/**
 * Phase 7: the real camera view inside Demo Mode. Same CameraX pipeline as
 * the product — the phone camera stays the interface — but the shutter is
 * always ready and nothing captured is ever uploaded: the demo result is
 * deterministic by design. The banner states this on every screen.
 */
@Composable
fun DemoCameraScreen(
    onCapture: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = rememberCameraPermissionState()

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewSession = remember {
        PreviewSession(context, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(permission.phase) {
        if (permission.phase == CameraPermissionPhase.GRANTED) {
            previewSession.start(
                lifecycleOwner = lifecycleOwner,
                lensFacing = CameraSelector.LENS_FACING_BACK,
                captureUseCase = null,
            ) { request -> surfaceRequest = request }
                .onFailure { cameraError = it.message ?: "Camera unavailable" }
        } else if (permission.phase == CameraPermissionPhase.UNKNOWN) {
            permission.launch()
        } else {
            previewSession.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewSession.stop() }
    }

    if (permission.phase == CameraPermissionPhase.GRANTED) {
        com.fixlens.app.ui.WorkshopCamera(
            title = "Guided demo", note = "Aim at the demo object, then capture.",
            surfaceRequest = surfaceRequest, isCapturing = false, hasFrontCamera = false,
            onBack = onBack, onFlipLens = {}, onCapture = onCapture,
            captureDescription = "Capture demo photo", scripted = true,
        )
    } else {
        Column(Modifier.fillMaxSize().paperSurface().statusBarsPadding().navigationBarsPadding()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FixLensColors.Ink)
            }
            ErrorState(
                title = "A closer look starts here",
                detail = "Allow camera access to try the scripted demo. Nothing you capture is analyzed or stored.",
                actionLabel = if (permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED) "Open settings" else "Allow camera",
                onAction = {
                    if (permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED) permission.openSettings()
                    else permission.launch()
                },
            )
        }
    }
}
