package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn

import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.ui.CameraPreview
import com.fixlens.app.ui.theme.FixLensColors

/**
 * Phase 4 "Show Me" overlay (spec §17.6): live camera + sketched framing guide +
 * short instruction. Honest approximate targeting, the ring marks WHERE to
 * look in frame, not a pixel-accurate component localization (no per-frame AI,
 * no ARCore). Explicit user action opens this screen; nothing is analyzed.
 */
@Composable
fun ShowMeCameraScreen(
    targetLabel: String,
    instruction: String,
    stepLabel: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = rememberCameraPermissionState()

    // System BACK closes the overlay (returns to the step) instead of popping
    // the whole navigation route (caught by the Phase 7 demo E2E).
    androidx.activity.compose.BackHandler { onBack() }

    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewSession = remember {
        PreviewSession(context, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(permission.phase, lensFacing) {
        if (permission.phase == CameraPermissionPhase.GRANTED) {
            previewSession.start(
                lifecycleOwner = lifecycleOwner,
                lensFacing = lensFacing,
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

    Box(modifier = Modifier.fillMaxSize().paperSurface().navigationBarsPadding()) {
        when {
            permission.phase == CameraPermissionPhase.DENIED ||
                permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED -> ErrorOverlay(
                title = "Camera permission needed",
                detail = "FixLens uses the camera to point you at the part.",
                actionLabel = if (permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED) {
                    "Open settings"
                } else {
                    "Allow camera"
                },
                onAction = {
                    if (permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED) {
                        permission.openSettings()
                    } else {
                        permission.launch()
                    }
                },
                onBack = onBack,
            )

            cameraError != null -> ErrorOverlay(
                title = "Camera problem",
                detail = cameraError,
                actionLabel = "Back",
                onAction = onBack,
                onBack = onBack,
            )

            else -> {
                CameraPreview(surfaceRequest = surfaceRequest, modifier = Modifier.fillMaxSize())
                CameraPaperBands(bottom = false)
                SketchTarget(
                    annotated = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FixLensColors.Paper)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = FixLensColors.Ink,
                )
            }
            Text(
                text = stepLabel,
                modifier = Modifier.weight(1f),
                color = FixLensColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            } }) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Flip camera",
                    tint = FixLensColors.Ink,
                )
            }
        }

        // Bottom instruction panel
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = FixLensColors.Cream,
        ) {
            Column(modifier = Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    text = targetLabel.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.Terracotta,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.titleMedium,
                    color = FixLensColors.Ink,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Framing guide only, move your camera until the part is inside the drawn circle.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.MutedInk,
                )
            }
        }
    }
}

@Composable
private fun ErrorOverlay(
    title: String,
    detail: String?,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().paperSurface(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = FixLensColors.Ink,
                textAlign = TextAlign.Center,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.MutedInk,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) { Text(actionLabel) }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("Back to steps", color = FixLensColors.MutedInk, fontSize = 13.sp)
            }
        }
    }
}
