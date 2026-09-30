package com.fixlens.app.ui

import androidx.camera.core.SurfaceRequest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Compose host for the CameraX PreviewView.
 *
 * The latest [SurfaceRequest] from [PreviewSession] is piped into the Preview
 * use case's surface provider; PreviewView renders whatever surface it is given
 * (FILL_CENTER scaling). Wiring via previewView.surfaceProvider is the standard
 * CameraX 1.4 pattern.
 */
@Composable
fun CameraPreview(
    surfaceRequest: SurfaceRequest?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val latestRequest = rememberUpdatedState(surfaceRequest)
    AndroidView(
        factory = { previewView },
        update = {
            // Forward each new SurfaceRequest from PreviewSession to the view.
            latestRequest.value?.let { request ->
                previewView.surfaceProvider.onSurfaceRequested(request)
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
