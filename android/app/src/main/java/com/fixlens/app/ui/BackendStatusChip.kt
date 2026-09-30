package com.fixlens.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.BackendHealth

/** User-facing backend status. No fake success: every state maps to a real probe outcome. */
sealed class BackendStatus {
    object Idle : BackendStatus()
    object Checking : BackendStatus()
    data class Ok(val health: BackendHealth) : BackendStatus()
    object Unconfigured : BackendStatus()
    data class Unreachable(val detail: String?) : BackendStatus()
    data class Invalid(val detail: String?) : BackendStatus()
}

/** Maps typed API errors to honest status. */
fun statusFor(error: ApiError): BackendStatus = when (error) {
    is ApiError.NotConfigured -> BackendStatus.Unconfigured
    is ApiError.Timeout -> BackendStatus.Unreachable("AI request timed out")
    is ApiError.Unreachable -> BackendStatus.Unreachable(error.message)
    is ApiError.InvalidResponse -> BackendStatus.Invalid(error.message)
    is ApiError.Http -> BackendStatus.Unreachable("Backend returned HTTP ${error.code}")
}

@Composable
fun BackendStatusChip(
    status: BackendStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onRetry,
        label = { Text(statusLabel(status), style = MaterialTheme.typography.labelLarge) },
        modifier = modifier.fillMaxWidth(0.85f),
    )
}

private fun statusLabel(status: BackendStatus): String = when (status) {
    is BackendStatus.Idle -> "Backend: not checked"
    is BackendStatus.Checking -> "Backend: checking…"
    is BackendStatus.Ok -> "Backend: OK" + (status.health.version?.let { " (v$it)" } ?: "")
    is BackendStatus.Unconfigured -> "Backend: not configured"
    is BackendStatus.Unreachable -> "Backend: unreachable"
    is BackendStatus.Invalid -> "Backend: unexpected response"
}
