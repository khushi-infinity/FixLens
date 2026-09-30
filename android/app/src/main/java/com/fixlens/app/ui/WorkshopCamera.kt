package com.fixlens.app.ui

import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fixlens.app.ui.theme.*

/** Shared camera-first layout, with opaque paper controls outside the viewfinder. */
@Composable
fun WorkshopCamera(
    title: String,
    note: String,
    surfaceRequest: SurfaceRequest?,
    isCapturing: Boolean,
    hasFrontCamera: Boolean,
    onBack: () -> Unit,
    onFlipLens: () -> Unit,
    onCapture: () -> Unit,
    captureDescription: String = "Capture photo",
    scripted: Boolean = false,
) {
    Column(Modifier.fillMaxSize().paperSurface().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = FixLensColors.Ink) }
            Column(Modifier.weight(1f)) {
                NotebookEyebrow(if (scripted) "FIELD GUIDE / SCRIPTED DEMO" else "FIELD GUIDE / CAMERA")
                Text(title, style = MaterialTheme.typography.titleLarge, color = FixLensColors.Ink)
            }
            if (hasFrontCamera) IconButton(onClick = onFlipLens, enabled = !isCapturing) {
                Icon(Icons.Filled.Cameraswitch, "Flip camera", tint = FixLensColors.Ink)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPreview(surfaceRequest, Modifier.fillMaxSize())
            SketchTarget(Modifier.fillMaxSize())
            CameraFieldNote(note, Modifier.align(Alignment.TopCenter).padding(16.dp)
                .heightIn(max = 120.dp).verticalScroll(rememberScrollState()))
            if (isCapturing) CircularProgressIndicator(Modifier.align(Alignment.Center), color = FixLensColors.Cream)
            CameraFieldNote(if (scripted) "Scripted result • not live AI" else "Frame the part inside the guide", Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (isCapturing) "Capturing…" else "A closer look", style = MaterialTheme.typography.titleLarge, color = FixLensColors.Ink)
                Text(if (scripted) "Try the sample repair journey." else "Hold steady, then tap to capture.", style = MaterialTheme.typography.bodySmall, color = FixLensColors.MutedInk)
            }
            Surface(onClick = onCapture, enabled = !isCapturing, shape = CircleShape, color = FixLensColors.Terracotta,
                border = BorderStroke(3.dp, FixLensColors.ClayWash),
                modifier = Modifier.size(72.dp).semantics { contentDescription = captureDescription }) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhotoCamera, null, tint = FixLensColors.Cream, modifier = Modifier.size(28.dp)) }
            }
        }
    }
}
