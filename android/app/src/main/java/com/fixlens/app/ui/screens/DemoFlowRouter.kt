package com.fixlens.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fixlens.app.demo.DemoMappers
import com.fixlens.app.demo.DemoScenarios

/**
 * Phase 7: the active Demo Mode session, passed down into the production
 * screens. When null, screens run the REAL pipeline untouched.
 */
data class DemoContext(
    val kind: DemoScenarios.Kind,
    val scenario: com.fixlens.app.demo.DemoJourney,
)

/**
 * Router for a scripted demo journey. Deterministic by construction: each
 * branch maps pre-authored content into the SAME production screens the live
 * pipeline uses — camera capture, diagnosis result, guidance + Show Me,
 * verification, completion, safety stop. No network, no AI, no billing here.
 *
 * Journey staging (spec §13: Demo Mode keeps the real camera):
 *   scenario → REAL CAMERA capture → DIAGNOSIS RESULT (§10 layout) →
 *   Start Fix → step guidance → verification → completion.
 *
 * SAFETY STOP journey mirrors production exactly: a STOP diagnosis offers no
 * fix path, and the flow ends with the electrician referral.
 */
@Composable
fun DemoFlowRouter(
    demo: DemoContext,
    onExit: () -> Unit,
) {
    val d = demo.scenario.diagnosis
    var stage by remember { mutableStateOf(DemoStage.CAPTURE) }

    when (stage) {
        DemoStage.CAPTURE -> DemoCameraScreen(
            onCapture = { stage = DemoStage.RESULT },
            onBack = onExit,
        )

        DemoStage.RESULT -> DiagnosisResultScreen(
            result = DemoMappers.toDiagnoseResponse(d),
            onDismiss = onExit,
            onRetry = {},
            onStartFix = {
                if (d.decision != "SAFETY_STOP") stage = DemoStage.GUIDED
            },
            demo = demo,
        )

        DemoStage.GUIDED -> GuidedRepairScreen(
            diagnosis = DemoMappers.toDiagnoseResponse(d),
            onExit = onExit,
            demo = demo,
        )
    }
}

private enum class DemoStage { CAPTURE, RESULT, GUIDED }
