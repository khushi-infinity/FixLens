package com.fixlens.app.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** User-facing phases of the camera permission flow. */
enum class CameraPermissionPhase { UNKNOWN, DENIED, GRANTED, PERMANENTLY_DENIED }

interface CameraPermissionState {
    val phase: CameraPermissionPhase
    fun launch()
    fun openSettings()
}

/**
 * Compose-friendly camera permission gate shared by Photo Mode and Live Camera.
 * Distinguishes "denied" (can re-ask) from "permanently denied" (must open app settings).
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current

    var phase by remember {
        mutableStateOf(
            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                PackageManager.PERMISSION_GRANTED -> CameraPermissionPhase.GRANTED
                else -> CameraPermissionPhase.UNKNOWN
            },
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        phase = if (granted) {
            CameraPermissionPhase.GRANTED
        } else {
            val activity = context as? Activity
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA,
            )
            if (canAskAgain) CameraPermissionPhase.DENIED else CameraPermissionPhase.PERMANENTLY_DENIED
        }
    }

    return object : CameraPermissionState {
        override val phase: CameraPermissionPhase get() = phase
        override fun launch() = launcher.launch(Manifest.permission.CAMERA)
        override fun openSettings() {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
    }
}
