package com.fixlens.app.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape

import com.fixlens.app.ui.theme.paperSurface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Uniform loading/error/empty states shared by every screen. Content enters
 * with a short fade/slide so state changes feel intentional rather than
 * abrupt (Phase 8 polish).
 */
private const val STATE_ENTER_MS = 220

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().paperSurface(), contentAlignment = Alignment.Center) {
        var visible by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(STATE_ENTER_MS)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    }
}

/** Uniform error state with an optional retry action. */
@Composable
fun ErrorState(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize().paperSurface(), contentAlignment = Alignment.Center) {
        var visible by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(STATE_ENTER_MS)) +
                slideInVertically(tween(STATE_ENTER_MS)) { it / 10 },
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                if (!detail.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) { Text(actionLabel) }
                }
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    }
}

/** Uniform empty state. */
@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().paperSurface(), contentAlignment = Alignment.Center) {
        var visible by remember { mutableStateOf(false) }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(STATE_ENTER_MS)),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    }
}
