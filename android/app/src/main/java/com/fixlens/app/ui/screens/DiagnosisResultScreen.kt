package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fixlens.app.network.ComponentDto
import com.fixlens.app.network.DiagnoseResponseDto
import com.fixlens.app.ui.theme.FixLensColors

/**
 * Purpose-built visual diagnosis experience (Phase 3, spec §10/§11).
 * Layout follows the spec: FIXLENS → I SEE → POSSIBLE ISSUE → WHAT I FOUND →
 * CONFIDENCE → SAFETY. HIGH risk shows the safety-stop variant with NO
 * guided-fix action; insufficient evidence shows the better-view variant.
 * No chat surface, no free-text input.
 */

private fun safetyColor(level: String): Color = when (level.uppercase()) {
    "HIGH" -> FixLensColors.Danger
    "MEDIUM" -> FixLensColors.Terracotta
    else -> FixLensColors.SageInk
}

private fun safetyLabel(level: String): String = when (level.uppercase()) {
    "HIGH" -> "High Risk"
    "MEDIUM" -> "Caution"
    "LOW" -> "Low Risk"
    else -> level.lowercase().replaceFirstChar { it.uppercase() }
}

/** Controlled confidence wording — never a fake-precise percentage (spec §4). */
private fun confidenceWording(band: String?): String = when (band?.uppercase()) {
    "HIGH" -> "High confidence"
    "MEDIUM" -> "Medium confidence"
    "LOW" -> "Low confidence"
    else -> "Uncertain"
}

@Composable
fun DiagnosisResultScreen(
    result: DiagnoseResponseDto,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
    onStartFix: () -> Unit = {},
    demo: DemoContext? = null,
) {
    val diag = result.diagnosis
    val scroll = rememberScrollState()
    val isSafetyStop = result.safety.decision.equals("SAFETY_STOP", ignoreCase = true)
    val needsBetterView = diag.needsBetterView && !diag.betterViewInstruction.isNullOrBlank()

    // Phase 8: the analysis result enters with a short rise-and-fade; the
    // safety icon pops in so the risk level lands visually as well as verbally.
    var entered by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(280),
        label = "resultEnter",
    )
    val safetyIconScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.55f,
        animationSpec = tween(340),
        label = "safetyIconEnter",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                alpha = enterProgress
                translationY = (1f - enterProgress) * 26.dp.toPx()
            },
    ) {
        // Phase 7: Demo Mode banner — the scripted result is never live AI.
        if (demo != null) {
            DemoBanner()
        }
        Text(
            text = "FIELD NOTES / DIAGNOSIS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Safety stop variant (spec §11): no guided-fix action ----------
            if (isSafetyStop) {
                SafetyStopCard(result = result)
            } else {
                // ---------- I SEE ----------
                SectionCard(title = "01 / THE OBJECT") {
                    Text(
                        text = diag.objectName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (diag.components.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Components",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        diag.components.forEach { component ->
                            ComponentRow(component)
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }

                // ---------- POSSIBLE ISSUE ----------
                SectionCard(title = "02 / POSSIBLE ISSUE") {
                    Text(
                        text = diag.issueSummary,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (diag.likelyCauses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Likely causes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        diag.likelyCauses.forEach { cause ->
                            Text(
                                text = "• ${cause.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }

                // ---------- WHAT I FOUND ----------
                SectionCard(title = "03 / OBSERVATIONS") {
                    diag.observations.forEach { item ->
                        Text(
                            text = "• [${item.kind}] ${item.text}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (item.kind.uppercase()) {
                                "OBSERVED" -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                // ---------- CONFIDENCE (controlled band, spec §4) ----------
                SectionCard(title = "CONFIDENCE") {
                    Text(
                        text = confidenceWording(diag.confidenceBand),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "FixLens is not certain — treat this as an assessment, not a measurement.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- SAFETY ----------
            SectionCard(title = "SAFETY") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (diag.safetyLevel.uppercase()) {
                        "HIGH" -> Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            tint = safetyColor(diag.safetyLevel),
                            modifier = Modifier.graphicsLayer {
                                scaleX = safetyIconScale
                                scaleY = safetyIconScale
                            },
                        )
                        "MEDIUM" -> Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = safetyColor(diag.safetyLevel),
                            modifier = Modifier.graphicsLayer {
                                scaleX = safetyIconScale
                                scaleY = safetyIconScale
                            },
                        )
                        else -> Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = safetyColor(diag.safetyLevel),
                            modifier = Modifier.graphicsLayer {
                                scaleX = safetyIconScale
                                scaleY = safetyIconScale
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = safetyLabel(diag.safetyLevel),
                        style = MaterialTheme.typography.titleMedium,
                        color = safetyColor(diag.safetyLevel),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = diag.safetyReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ---------- Better-view variant (spec §5) ----------
            if (needsBetterView && !isSafetyStop) {
                Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
                    colors = CardDefaults.cardColors(
                        containerColor = FixLensColors.Terracotta.copy(alpha = 0.14f),
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "I NEED A BETTER VIEW",
                            style = MaterialTheme.typography.titleMedium,
                            color = FixLensColors.Terracotta,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = diag.betterViewInstruction ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Text(
                text = "Diagnosis complete — start a guided fix to continue step by step.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ---------- Actions per state (spec §10/§11) ----------
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                isSafetyStop -> {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("View What I Detected") }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FixLensColors.Danger,
                            contentColor = FixLensColors.Cream,
                        ),
                shape = RoundedCornerShape(10.dp),
            ) { Text("Done") }
                }
                needsBetterView -> {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Try Again")
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Done") }
                }
                else -> {
                    // GUIDE / LIMITED_GUIDE: offer the guided repair (Phase 4).
                    // SAFETY_STOP never reaches this branch — no instruction path exists.
                    Button(
                        onClick = onStartFix,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Start Fix") }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SafetyStopCard(result: DiagnoseResponseDto) {
    val diag = result.diagnosis
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
        colors = CardDefaults.cardColors(
            containerColor = FixLensColors.Danger.copy(alpha = 0.16f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "STOP",
                style = MaterialTheme.typography.headlineMedium,
                color = FixLensColors.Danger,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.safety.userMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "What FixLens detected: ${diag.objectName} — ${diag.issueSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComponentRow(component: ComponentDto) {
    val prefix = if (component.kind.equals("INFERRED", ignoreCase = true)) {
        "○ " // inferred: not directly seen
    } else {
        "• " // observed
    }
    val status = component.status?.let { " — $it" } ?: ""
    Text(
        text = "$prefix${component.name}$status",
        style = MaterialTheme.typography.bodyMedium,
        color = if (component.kind.equals("INFERRED", ignoreCase = true)) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/** Full-screen loading state while the backend analyzes the image. */
@Composable
fun AnalyzingState(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface(),
        contentAlignment = Alignment.Center,
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(color = FixLensColors.Terracotta)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analyzing your photo…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Looking for the small details. This may take up to two minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
        }
    }
}
