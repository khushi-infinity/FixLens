package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.camera.core.CameraSelector
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.fixlens.app.camera.CameraPermissionPhase
import com.fixlens.app.camera.PreviewSession
import com.fixlens.app.camera.rememberCameraPermissionState
import com.fixlens.app.demo.DemoScenarios
import com.fixlens.app.ui.CameraPreview
import com.fixlens.app.ui.theme.FixLensColors

/**
 * Phase 7 demo entry (spec §13): pick one of the deterministic scenarios.
 * Uses the real camera preview as the background — Demo Mode keeps the phone
 * camera at the center of the demo — and states its honest boundary up front.
 *
 * The camera here is presentational only: nothing captured is analyzed in
 * Demo Mode (results are pre-authored by design, and the banner says so).
 */
@Composable
fun DemoPickerScreen(
    onPick: (DemoScenarios.Kind) -> Unit,
    onExit: () -> Unit,
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

    Box(modifier = Modifier.fillMaxSize().paperSurface().navigationBarsPadding()) {
        when {
            permission.phase == CameraPermissionPhase.DENIED ||
                permission.phase == CameraPermissionPhase.PERMANENTLY_DENIED -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Camera permission needed",
                    style = MaterialTheme.typography.titleLarge,
                    color = FixLensColors.Ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The demo runs on the real camera view. Allow camera access to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.MutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permission.launch() }, shape = RoundedCornerShape(10.dp)) { Text("Allow camera") }
            }

            else -> {
                CameraPreview(surfaceRequest = surfaceRequest, modifier = Modifier.fillMaxSize())
                CameraPaperBands(bottom = false)
                SketchTarget(Modifier.fillMaxSize())
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = FixLensColors.Ink,
                )
            }
            Text(
                text = "The practice bench",
                color = FixLensColors.Ink,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = FixLensColors.Cream,
        ) {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    text = "TRY A SCRIPTED REPAIR",
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.Terracotta,
                )
                Spacer(modifier = Modifier.height(10.dp))
                DemoScenarios.all.forEach { scenario ->
                    Button(
                        onClick = { onPick(scenario.kind) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FixLensColors.Sage,
                            contentColor = FixLensColors.Ink,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = scenario.title,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = scenario.blurb,
                                style = MaterialTheme.typography.bodySmall,
                                color = FixLensColors.MutedInk,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "Demo Mode replays pre-authored results on the real camera view — it is not live AI. Nothing you capture here is analyzed or stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixLensColors.MutedInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Exit Demo Mode") }
            }
        }
    }
}

/** Persistent honesty banner shown on every Demo Mode screen. */
@Composable
fun DemoBanner(modifier: Modifier = Modifier, detail: String? = null) {
    Surface(
        modifier = modifier
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = FixLensColors.Sage,
    ) {
        Text(
            text = if (detail == null) "DEMO MODE — scripted result, not live AI" else detail,
            style = MaterialTheme.typography.labelMedium,
            color = FixLensColors.Terracotta,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
