package com.fixlens.app.ui.screens

import com.fixlens.app.ui.theme.paperSurface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fixlens.app.network.ApiError
import com.fixlens.app.network.PlanStatuses
import com.fixlens.app.network.PlanResponseDto
import com.fixlens.app.network.RepairPlanDto
import com.fixlens.app.network.RepairStepDto
import com.fixlens.app.repair.EnginePhase
import com.fixlens.app.repair.RepairEngine
import com.fixlens.app.repair.RepairIntent
import com.fixlens.app.repair.RepairSessionPlan
import com.fixlens.app.ui.theme.FixLensColors
import androidx.compose.animation.animateContentSize

@Composable
private fun rememberAppContainer(): com.fixlens.app.di.AppContainer {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return remember(context) { (context as com.fixlens.app.FixLensApp).appContainer }
}

/**
 * Phase 4 guided repair flow entry (spec §17.6). Generates the repair plan
 * ONCE from the confirmed diagnosis (explicit user action, no per-frame AI),
 * then hands over to the step-by-step guidance screen. Loading, backend
 * errors, and blocked plans all offer Retry / Back, never a dead end.
 */
@Composable
fun GuidedRepairScreen(
    diagnosis: com.fixlens.app.network.DiagnoseResponseDto,
    onExit: () -> Unit,
    demo: DemoContext? = null,
) {
    val container = rememberAppContainer()
    var planState by remember {
        mutableStateOf<GuidedPlanState>(GuidedPlanState.Loading)
    }
    var retryToken by remember { mutableStateOf(0) }
    var paywallReason by remember { mutableStateOf<PaywallReason?>(null) }
    var creditConsumed by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Phase 7: Demo Mode short-circuits BEFORE any network/billing call,
    // the plan comes from pre-authored content, deterministically.
    androidx.compose.runtime.LaunchedEffect(retryToken, demo) {
        if (demo != null) {
            planState = GuidedPlanState.Ready(
                com.fixlens.app.demo.DemoMappers.toPlanResponse(demo.scenario.diagnosis, demo.scenario.steps),
            )
            return@LaunchedEffect
        }
    }

    // Phase 6 premium gate: guided repair is a Pro/credit feature (spec §12).
    // The plan has not been generated yet at this point, the gate decides
    // BEFORE any generation; a credit is spent only when the plan arrives.
    if (paywallReason != null) {
        PaywallScreen(
            repository = container.billingRepository,
            reason = paywallReason!!,
            onDismiss = onExit,
            onEntitled = {
                paywallReason = null
                planState = GuidedPlanState.Loading
                retryToken++ // resume the guided-repair flow after unlocking
            },
        )
        return
    }

    androidx.compose.runtime.LaunchedEffect(retryToken, demo) {
        if (demo != null) return@LaunchedEffect // demo already resolved above
        planState = GuidedPlanState.Loading
        val decision = container.billingRepository.evaluatePremium()
        if (decision is com.fixlens.app.billing.BillingGate.Decision.Paywall) {
            planState = GuidedPlanState.Blocked
            paywallReason = PaywallReason.PREMIUM_FEATURE
            return@LaunchedEffect
        }
        planState = try {
            GuidedPlanState.Ready(container.api.plan(diagnosis.diagnosis))
        } catch (e: ApiError) {
            GuidedPlanState.Error(friendlyPlanMessage(e))
        } catch (e: Exception) {
            GuidedPlanState.Error("We couldn't prepare repair steps right now. Please try again.")
        }
    }

    when (val state = planState) {
        is GuidedPlanState.Loading -> LoadingState("Preparing your repair plan…")
        is GuidedPlanState.Blocked -> LoadingState("Preparing your repair plan…")
        is GuidedPlanState.Error -> ErrorState(
            title = "Repair plan unavailable",
            detail = state.message,
            actionLabel = "Try again",
            onAction = { retryToken++ },
        )
        is GuidedPlanState.Ready -> {
            // Spend the credit only once, only when the plan actually arrived
            // (free plan: premium actions consume a repair credit). Demo Mode
            // is never metered or gated, it demonstrates the full UX.
            androidx.compose.runtime.LaunchedEffect(state.response) {
                if (demo == null && !creditConsumed &&
                    !container.billingRepository.state.value.isPro &&
                    container.billingRepository.state.value.creditBalance > 0
                ) {
                    container.billingRepository.consumeCredit()
                }
                creditConsumed = true
            }
            GuidedPlanScreen(
                planResponse = state.response,
                onExit = onExit,
                onScanAgain = onExit,
                demo = demo,
            )
        }
    }
}

private sealed class GuidedPlanState {
    data object Loading : GuidedPlanState()
    data class Ready(val response: PlanResponseDto) : GuidedPlanState()
    data class Error(val message: String) : GuidedPlanState()

    /** Paywall shown; after unlock the plan flow restarts via retryToken. */
    data object Blocked : GuidedPlanState()
}

private fun friendlyPlanMessage(error: ApiError): String = when (error) {
    is ApiError.NotConfigured ->
        "The backend is not configured on this device. See docs/DEVICE_SETUP.md."
    is ApiError.Timeout ->
        "The AI is taking longer than usual right now. Please try again, it often succeeds on a second attempt."
    is ApiError.Unreachable ->
        "Could not reach the FixLens backend. Check your connection and adb reverse, then try again."
    is ApiError.Http -> error.message
    is ApiError.InvalidResponse ->
        "The repair plan didn't make sense. Please try again."
}

/**
 * Renders an already-generated plan. Visual, button-driven guidance,
 * no chat input anywhere. The plan is kept in memory for the session; the
 * state machine ([RepairEngine]) owns all progress logic. When a step reaches
 * READY_FOR_VERIFICATION the verification capture flow intercepts that exact
 * phase (Phase 5): PASS advances, everything else returns to the step.
 */
@Composable
private fun GuidedPlanScreen(
    planResponse: PlanResponseDto,
    onExit: () -> Unit,
    onScanAgain: () -> Unit,
    demo: DemoContext? = null,
) {
    // Defensive routing: this screen is only meaningful for PLAN_READY.
    if (planResponse.status != PlanStatuses.PLAN_READY || planResponse.plan == null) {
        GuidedBlockedState(planResponse = planResponse, onExit = onExit, onRequestBetterView = onScanAgain)
        return
    }

    val engine = remember { RepairEngine() }
    val container = rememberAppContainer()
    val view = androidx.compose.ui.platform.LocalView.current
    val loaded = remember(planResponse) {
        engine.loadPlan(
            planResponse.plan.toSessionPlan(
                safetyLevel = if (planResponse.requiresAcknowledgement) "MEDIUM" else "LOW",
                safetyMessage = planResponse.safety.userMessage,
                requiresAcknowledgement = planResponse.requiresAcknowledgement,
            ),
        )
    }
    val state by engine.state.collectAsState()
    var showMe by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDifficultDialog by remember { mutableStateOf(false) }
    // Set when the user declines verification for the CURRENT step (UNCERTAIN
    // → "Skip verification for now"); resets automatically per step. A skipped
    // step completes on the user's own confirmation, never worded as verified.
    val verificationSkipped = remember(state.currentStepNumber) { mutableStateOf(false) }

    // Voice guide: on-device TTS, one instance for the whole guided session.
    val voiceContext = androidx.compose.ui.platform.LocalContext.current
    val voice = remember { VoiceGuide(voiceContext) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { voice.shutdown() }
    }
    // Silence the voice whenever the step view is left (verification camera,
    // Show Me camera), speech resumes fresh on the next step screen.
    LaunchedEffect(state.phase, showMe) {
        if (showMe || state.phase == EnginePhase.READY_FOR_VERIFICATION) voice.stop()
    }

    if (!loaded) {
        ErrorState(
            title = "Repair plan unavailable",
            detail = "The plan contained no usable steps.",
            actionLabel = "Back",
            onAction = onExit,
        )
        return
    }

    // Auto-start for LOW-risk plans: the engine must be IN_PROGRESS before
    // ConfirmStep/SkipStep become legal (found via E2E, without this the
    // step pointer never advanced). MEDIUM plans start via AcknowledgeSafety.
    LaunchedEffect(state.phase, state.plan.requiresAcknowledgement) {
        if (state.phase == EnginePhase.PLAN_READY &&
            !state.plan.requiresAcknowledgement &&
            state.status == com.fixlens.app.repair.RepairSessionStatus.NOT_STARTED
        ) {
            engine.dispatch(RepairIntent.Start)
        }
    }

    // Phase 5 seam: READY_FOR_VERIFICATION is no longer auto-advanced,
    // it opens the camera verification flow (user action → capture → one
    // AI verification call → result). The step completes only through the
    // engine's Advance intent, which the UI dispatches solely on PASS,
    // except when the user explicitly declined verification for this step.
    if (state.phase == EnginePhase.READY_FOR_VERIFICATION) {
        val step = state.currentStep
        if (step == null || verificationSkipped.value) {
            // Defensive no-step case, or user-declined verification: the
            // user's own confirmation completes the step.
            LaunchedEffect(step?.number) { engine.dispatch(RepairIntent.Advance) }
        } else {
            VerifyStepScreen(
                api = container.api,
                demo = demo,
                stepNumber = step.number,
                totalSteps = state.totalSteps,
                stepTitle = step.title,
                stepAction = step.action,
                targetComponent = step.targetComponent,
                expectedState = step.expectedState,
                onVerified = {
                    engine.dispatch(RepairIntent.Advance) // PASS → step COMPLETE
                },
                onIncomplete = {
                    // FAIL: the capture shows the step is not done, the
                    // user returns to the step with the evidence.
                    engine.dispatch(RepairIntent.MarkAttempted)
                },
                onUncertain = { _, _ ->
                    // "Skip verification for now": the user declines camera
                    // verification for THIS step; the next "I've Done This"
                    // completes on their own confirmation (never worded as
                    // verified). Flag resets automatically on step change.
                    verificationSkipped.value = true
                    engine.dispatch(RepairIntent.MarkAttempted)
                },
                onBack = {
                    engine.dispatch(RepairIntent.MarkAttempted)
                },
            )
        }
        return
    }

    if (showMe) {
        val step = state.currentStep
        if (step != null) {
            ShowMeCameraScreen(
                targetLabel = step.targetComponent,
                instruction = step.action,
                stepLabel = "Step ${step.number} of ${state.totalSteps}, ${step.title}",
                onBack = { showMe = false },
            )
        }
        return
    }

    when {
        state.phase == EnginePhase.PLAN_READY && state.plan.requiresAcknowledgement -> SafetyAcknowledgementView(
            safetyMessage = state.plan.safetyMessage,
            safetyLevel = state.plan.safetyLevel,
            onAcknowledge = { engine.dispatch(RepairIntent.AcknowledgeSafety) },
            onExit = { showCancelDialog = true },
        )

        state.phase == EnginePhase.REPAIR_COMPLETE -> CompletionView(
            message = engine.completionMessage(),
            objectName = state.plan.objectName,
            totalSteps = state.totalSteps,
            demo = demo,
            voice = voice,
            onDone = onExit,
        )

        else -> StepView(
            stepNumber = state.currentStepNumber,
            totalSteps = state.totalSteps,
            completedCount = state.completedCount,
            title = state.currentStep?.title ?: "",
            action = state.currentStep?.action ?: "",
            instruction = state.currentStep?.instruction ?: "",
            targetComponent = state.currentStep?.targetComponent ?: "",
            toolKnown = state.currentStep?.toolKnown ?: false,
            tool = state.currentStep?.tool,
            toolNote = state.currentStep?.toolNote,
            warning = state.currentStep?.warning,
            expectedState = state.currentStep?.expectedState ?: "",
            safetyLevel = state.plan.safetyLevel,
            voice = voice,
            showWhy = showWhy,
            demo = demo,
            onToggleWhy = { showWhy = !showWhy },
            onShowMe = { showMe = true },
            onDone = {
                showWhy = false
                view.hapticTick()
                engine.dispatch(RepairIntent.ConfirmStep)
            },
            onDifficult = { showDifficultDialog = true },
            onBack = { showCancelDialog = true },
        )
    }

    if (showDifficultDialog) {
        AlertDialog(
            onDismissRequest = { showDifficultDialog = false },
            title = { Text("Can't do this step?") },
            text = {
                Text(
                    "FixLens will move to the next step and mark this one as skipped. " +
                        "You can ask someone for help with it, or stop the repair here.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDifficultDialog = false
                    showWhy = false
                    engine.dispatch(RepairIntent.SkipStep)
                }) { Text("Skip this step") }
            },
            dismissButton = {
                TextButton(onClick = { showDifficultDialog = false }) { Text("Keep trying") }
            },
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Leave this repair?") },
            text = { Text("You can pause and continue later from this screen, or cancel the guided repair.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelDialog = false
                    engine.dispatch(RepairIntent.Cancel)
                    onExit()
                }) { Text("Cancel repair") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        showCancelDialog = false
                        engine.dispatch(RepairIntent.Pause)
                        onExit()
                    }) { Text("Pause for now") }
                    TextButton(onClick = { showCancelDialog = false }) { Text("Keep going") }
                }
            },
        )
    }
}

@Composable
private fun StepView(
    stepNumber: Int,
    totalSteps: Int,
    completedCount: Int,
    title: String,
    action: String,
    instruction: String,
    targetComponent: String,
    toolKnown: Boolean,
    tool: String?,
    toolNote: String?,
    warning: String?,
    expectedState: String,
    safetyLevel: String,
    voice: VoiceGuide,
    showWhy: Boolean,
    demo: DemoContext? = null,
    onToggleWhy: () -> Unit,
    onShowMe: () -> Unit,
    onDone: () -> Unit,
    onDifficult: () -> Unit,
    onBack: () -> Unit,
) {
    var voiceMuted by remember { mutableStateOf(false) }

    // Voice guide: read the step aloud once per step (on-device TTS).
    LaunchedEffect(stepNumber) {
        voice.speak("Step $stepNumber of $totalSteps. $title. $action")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = FixLensColors.Ink,
                )
            }
            Column {
                Text(
                    text = "REPAIR NOTEBOOK",
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.MutedInk,
                )
                Text(
                    text = "Step $stepNumber of $totalSteps",
                    style = MaterialTheme.typography.titleMedium,
                    color = FixLensColors.Ink,
                )
            }
            Spacer(Modifier.weight(1f))
            // Voice guide mute toggle, on-device speech, never a network call.
            IconButton(onClick = {
                voiceMuted = !voiceMuted
                voice.setMuted(voiceMuted)
                if (!voiceMuted) voice.speak("Step $stepNumber of $totalSteps. $title. $action")
            }) {
                Icon(
                    imageVector = if (voiceMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    contentDescription = if (voiceMuted) "Turn voice guide on" else "Turn voice guide off",
                    tint = FixLensColors.Ink,
                )
            }
            // Phase 8: the scripted result must never pass for live AI,
            // the demo banner rides on every guided step too.
        }
        if (demo != null) DemoBanner()
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (completedCount.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            color = FixLensColors.Terracotta,
            trackColor = FixLensColors.Cream,
        )

        // ---------- Step content ----------
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = FixLensColors.Ink,
            )
            Text(
                text = action,
                style = MaterialTheme.typography.titleMedium,
                color = FixLensColors.Terracotta,
            )

            StepIllustration(action = action)

            GuidedCard(title = "01 / YOUR NEXT MOVE") {
                Text(instruction, style = MaterialTheme.typography.bodyMedium, color = FixLensColors.Ink)
            }

            // Tool: only a determined tool is named; otherwise the honest generic.
            GuidedCard(title = if (toolKnown) "TOOL" else "TOOL, NOT SURE WHICH") {
                if (toolKnown && !tool.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            tint = FixLensColors.Terracotta,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tool, style = MaterialTheme.typography.titleMedium, color = FixLensColors.Ink)
                    }
                    toolNote?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = FixLensColors.MutedInk)
                    }
                } else {
                    Text(
                        text = toolNote?.takeIf { it.isNotBlank() }
                            ?: "Use the appropriate screwdriver for this screw.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FixLensColors.Ink,
                    )
                }
            }

            warning?.let {
                Card(
                    border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
                    colors = CardDefaults.cardColors(containerColor = FixLensColors.Danger.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = FixLensColors.Danger,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("CAREFUL", style = MaterialTheme.typography.labelLarge, color = FixLensColors.Danger)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = FixLensColors.Ink)
                        }
                    }
                }
            }

            if (showWhy) {
                GuidedCard(title = "WHY IT HELPS") {
                    Text(
                        text = "You are working on: $targetComponent.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FixLensColors.Ink,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "After this step you should see: $expectedState",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FixLensColors.MutedInk,
                    )
                }
            }

            GuidedCard(title = "LOOK FOR THIS") {
                Text(expectedState, style = MaterialTheme.typography.bodyMedium, color = FixLensColors.MutedInk)
            }

            if (safetyLevel.equals("MEDIUM", ignoreCase = true)) {
                Text(
                    text = "Limited guidance, work slowly and stop if anything looks unsafe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FixLensColors.Terracotta,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ---------- Actions: buttons only, no free-text (spec §17.6) ----------
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixLensColors.Terracotta,
                    contentColor = FixLensColors.Cream,
                ),
            ) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("I've Done This", style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShowMe,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(imageVector = Icons.Filled.Visibility, contentDescription = null, tint = FixLensColors.Terracotta)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show Me", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onToggleWhy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(imageVector = Icons.Filled.HelpOutline, contentDescription = null, tint = FixLensColors.Ink)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showWhy) "Hide Why" else "Why?", style = MaterialTheme.typography.labelLarge)
                }
            }
            TextButton(onClick = onDifficult, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "I can't do this",
                    style = MaterialTheme.typography.labelLarge,
                    color = FixLensColors.MutedInk,
                )
            }
        }
    }
}

@Composable
private fun SafetyAcknowledgementView(
    safetyMessage: String,
    safetyLevel: String,
    onAcknowledge: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = FixLensColors.Terracotta,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("BEFORE YOU START", style = MaterialTheme.typography.titleLarge, color = FixLensColors.Terracotta)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
            colors = CardDefaults.cardColors(containerColor = FixLensColors.Terracotta.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = safetyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = FixLensColors.Ink,
                modifier = Modifier.padding(16.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "FixLens will show limited guidance for this repair. You must accept the warning to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.MutedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FixLensColors.Terracotta,
                contentColor = FixLensColors.Cream,
            ),
        ) {
            Text("I Understand, Continue", style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Back") }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun CompletionView(
    message: String,
    objectName: String,
    totalSteps: Int,
    onDone: () -> Unit,
    demo: DemoContext? = null,
    voice: VoiceGuide? = null,
) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        view.hapticConfirm()
        voice?.speak(message)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (demo != null) {
            DemoBanner(modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
        }
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = FixLensColors.Terracotta,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.headlineSmall,
            color = FixLensColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "$objectName, $totalSteps guided steps",
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.MutedInk,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // Honest boundary: steps were camera-verified where the user chose to;
        // FixLens never claims the overall repair is professionally sound.
        Text(
            text = "Steps were checked with your camera where you chose to. Double-check the repair yourself before relying on it.",
            style = MaterialTheme.typography.bodyMedium,
            color = FixLensColors.MutedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

/** Blocked plan (defensive): HIGH risk / better view / no issue never show steps. */
@Composable
private fun GuidedBlockedState(
    planResponse: PlanResponseDto,
    onExit: () -> Unit,
    onRequestBetterView: () -> Unit,
) {
    val isBetterView = planResponse.status == PlanStatuses.BLOCKED_BETTER_VIEW
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.LaunchedEffect(planResponse.status) {
        if (planResponse.status == PlanStatuses.BLOCKED_HIGH_RISK) view.hapticReject()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paperSurface()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = when (planResponse.status) {
                PlanStatuses.BLOCKED_HIGH_RISK -> "STOP"
                PlanStatuses.BLOCKED_BETTER_VIEW -> "I NEED A BETTER VIEW"
                PlanStatuses.BLOCKED_NO_ISSUE -> "NOTHING TO FIX"
                else -> "NO PLAN AVAILABLE"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = if (planResponse.status == PlanStatuses.BLOCKED_HIGH_RISK) {
                FixLensColors.Danger
            } else {
                FixLensColors.Terracotta
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = planResponse.safety.userMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = FixLensColors.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isBetterView) {
            Button(
                onClick = onRequestBetterView,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Try Again") }
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Done") }
        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun GuidedCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, com.fixlens.app.ui.theme.FixLensColors.Rule),
        colors = CardDefaults.cardColors(containerColor = FixLensColors.Cream),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = FixLensColors.MutedInk)
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// DTO -> engine session mapping (no AI knowledge in the engine)
// ---------------------------------------------------------------------------
/**
 * Maps the wire plan into the engine's session model. The safety level shown
 * in-session comes from the backend's requires_acknowledgement flag (true
 * exactly when the final level is MEDIUM); the acknowledgement message is the
 * backend's safety notice text.
 */
fun RepairPlanDto.toSessionPlan(
    safetyLevel: String,
    safetyMessage: String,
    requiresAcknowledgement: Boolean,
): RepairSessionPlan = RepairSessionPlan(
    objectName = objectName,
    issueSummary = issueSummary,
    steps = steps.map { it.toSessionStep() },
    safetyLevel = safetyLevel,
    safetyMessage = safetyMessage,
    requiresAcknowledgement = requiresAcknowledgement,
)

private fun RepairStepDto.toSessionStep() = com.fixlens.app.repair.RepairStepSession(
    number = number,
    title = title,
    action = action,
    instruction = instruction,
    targetComponent = targetComponent,
    toolKnown = toolKnown,
    tool = tool,
    toolNote = toolNote,
    warning = warning,
    expectedState = expectedState,
    confirmationRequired = confirmationRequired,
)
