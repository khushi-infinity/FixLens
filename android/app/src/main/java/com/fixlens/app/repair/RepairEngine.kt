package com.fixlens.app.repair

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 4 repair state machine (spec §17.6).
 *
 * Pure Kotlin: no Android imports, no network, no AI provider references.
 * The engine consumes an already-validated [RepairSessionPlan] and advances
 * through explicit user intents only. Every transition is legal-state
 * guarded — an invalid intent from an unreachable state is a no-op, never a
 * crash. Phase 5 hooks: [RepairStepState] and [VerificationResult] expose the
 * exact seam where visual verification plugs in later; the engine never
 * calls a model itself.
 */

/** UI-facing per-step progress state. */
enum class RepairStepState {
    /** The user is being shown this step's instruction. */
    ACTIVE,

    /** The user tapped "I've Done This" — step awaits confirmation/verification. */
    AWAITING_CONFIRMATION,

    /** The step is finished (user-confirmed; camera verification attaches at
     * the READY_FOR_VERIFICATION seam and gates the Advance intent). */
    COMPLETE,

    /** The user said they cannot do this step. */
    BLOCKED_BY_USER,
}

/**
 * Outcome of visual verification at the READY_FOR_VERIFICATION seam
 * (Phase 5). The engine never produces these itself and never calls a model:
 * the UI performs the capture + backend call, then dispatches [RepairIntent.Advance]
 * only for [Verified]. FAIL and UNCERTAIN keep the step open — the engine has
 * no code path that completes a step without an explicit user-driven Advance
 * following a PASS.
 */
sealed class VerificationResult {
    /** No verification was performed (legacy flow / user declined). */
    object NotVerified : VerificationResult()

    /** The capture clearly shows the expected state achieved. */
    data class Verified(val explanation: String) : VerificationResult()

    /** The capture clearly shows the expected state NOT achieved. */
    data class FailedMismatch(
        val explanation: String,
        val betterViewInstruction: String? = null,
    ) : VerificationResult()

    /** The capture does not settle the comparison; always carries ONE view request. */
    data class Inconclusive(
        val explanation: String,
        val betterViewInstruction: String,
    ) : VerificationResult()
}

enum class RepairSessionStatus { NOT_STARTED, IN_PROGRESS, PAUSED, COMPLETED, ABORTED }

/** The full repair experience state the guidance screen renders. */
data class RepairSessionState(
    val plan: RepairSessionPlan,
    val stepStates: Map<Int, RepairStepState>,
    val currentStepNumber: Int,
    val phase: EnginePhase,
    val status: RepairSessionStatus,
    val lastCompletedAtMillis: Long? = null,
) {
    val currentStep: RepairStepSession? get() = plan.steps.firstOrNull { it.number == currentStepNumber }
    val completedCount: Int get() = stepStates.values.count { it == RepairStepState.COMPLETE }
    val totalSteps: Int get() = plan.steps.size
    val isLastStep: Boolean get() = currentStepNumber >= plan.steps.maxOfOrNull { it.number } ?: 1
}

enum class EnginePhase {
    PLAN_READY,
    STEP_ACTIVE,
    WAITING_FOR_USER,
    READY_FOR_VERIFICATION,
    STEP_COMPLETE,
    NEXT_STEP,
    REPAIR_COMPLETE,
}

/** Provider-agnostic plan model for the engine (decoupled from DTOs). */
data class RepairSessionPlan(
    val objectName: String,
    val issueSummary: String,
    val steps: List<RepairStepSession>,
    val safetyLevel: String,
    val safetyMessage: String,
    val requiresAcknowledgement: Boolean,
)

data class RepairStepSession(
    val number: Int,
    val title: String,
    val action: String,
    val instruction: String,
    val targetComponent: String,
    val toolKnown: Boolean,
    val tool: String?,
    val toolNote: String?,
    val warning: String?,
    val expectedState: String,
    val confirmationRequired: Boolean,
)

/** Immutable record for "My Repairs" persistence (kept locally, spec §17.6). */
data class RepairSessionRecord(
    val sessionId: String,
    val objectName: String,
    val diagnosisIssueSummary: String,
    val safetyLevel: String,
    val planJson: String,
    val currentStepNumber: Int,
    val completedStepNumbers: List<Int>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val status: RepairSessionStatus,
)

/** User intents — the only way the state machine moves. */
sealed class RepairIntent {
    object Start : RepairIntent()
    object AcknowledgeSafety : RepairIntent()
    object OpenStep : RepairIntent()
    object MarkAttempted : RepairIntent()
    object ConfirmStep : RepairIntent()
    object Advance : RepairIntent()
    object SkipStep : RepairIntent()
    object Pause : RepairIntent()
    object Resume : RepairIntent()
    object Cancel : RepairIntent()
}

class RepairEngine {
    private val _state = MutableStateFlow(
        RepairSessionState(
            plan = RepairSessionPlan("", "", emptyList(), "LOW", "", false),
            stepStates = emptyMap(),
            currentStepNumber = 0,
            phase = EnginePhase.PLAN_READY,
            status = RepairSessionStatus.NOT_STARTED,
        ),
    )
    val state: StateFlow<RepairSessionState> = _state

    /** Loads a plan; resets any previous session. Returns false if the plan is unusable. */
    fun loadPlan(plan: RepairSessionPlan): Boolean {
        if (plan.steps.isEmpty()) return false
        _state.value = RepairSessionState(
            plan = plan,
            // Every step starts ACTIVE (pending); progress is expressed by the
            // current step pointer + COMPLETE flags, so re-entry stays simple.
            stepStates = plan.steps.associate { it.number to RepairStepState.ACTIVE },
            currentStepNumber = plan.steps.minOf { it.number },
            phase = EnginePhase.PLAN_READY,
            status = RepairSessionStatus.NOT_STARTED,
        )
        return true
    }

    fun dispatch(intent: RepairIntent) {
        val s = _state.value
        val next: RepairSessionState? = when (intent) {
            is RepairIntent.Start -> if (
                s.phase == EnginePhase.PLAN_READY &&
                s.status == RepairSessionStatus.NOT_STARTED &&
                // MEDIUM risk: no step may become active before the user
                // explicitly accepts the safety warning (spec §17.6).
                !s.plan.requiresAcknowledgement
            ) {
                s.copy(phase = EnginePhase.STEP_ACTIVE, status = RepairSessionStatus.IN_PROGRESS)
            } else {
                null
            }

            is RepairIntent.AcknowledgeSafety -> if (s.phase == EnginePhase.PLAN_READY && s.plan.requiresAcknowledgement) {
                // Accepting the warning starts the guided session.
                s.copy(
                    plan = s.plan.copy(requiresAcknowledgement = false),
                    phase = EnginePhase.STEP_ACTIVE,
                    status = RepairSessionStatus.IN_PROGRESS,
                )
            } else {
                null
            }

            is RepairIntent.OpenStep -> null // informational; UI reads state directly

            is RepairIntent.MarkAttempted -> if (
                s.phase == EnginePhase.STEP_ACTIVE ||
                s.phase == EnginePhase.WAITING_FOR_USER ||
                s.phase == EnginePhase.NEXT_STEP ||
                // Withdrawing a confirmation while verification is pending
                // (user backs out of the verification capture) returns the
                // step to the working state — it never completes the step.
                s.phase == EnginePhase.READY_FOR_VERIFICATION
            ) {
                s.copy(phase = EnginePhase.WAITING_FOR_USER)
            } else {
                null
            }

            is RepairIntent.ConfirmStep -> if (
                s.phase == EnginePhase.STEP_ACTIVE ||
                s.phase == EnginePhase.WAITING_FOR_USER ||
                s.phase == EnginePhase.NEXT_STEP
            ) {
                s.copy(phase = EnginePhase.READY_FOR_VERIFICATION)
            } else {
                null
            }

            is RepairIntent.Advance -> advance(s)

            is RepairIntent.SkipStep -> skipStep(s)

            is RepairIntent.Pause -> if (s.status == RepairSessionStatus.IN_PROGRESS) {
                s.copy(status = RepairSessionStatus.PAUSED)
            } else {
                null
            }

            is RepairIntent.Resume -> if (s.status == RepairSessionStatus.PAUSED) {
                s.copy(status = RepairSessionStatus.IN_PROGRESS)
            } else {
                null
            }

            is RepairIntent.Cancel -> if (s.status != RepairSessionStatus.COMPLETED) {
                s.copy(status = RepairSessionStatus.ABORTED, phase = EnginePhase.PLAN_READY)
            } else {
                null
            }
        }
        if (next != null) _state.value = next
    }

    /** Difficult-step path: the user marks the step as beyond them; the
     * pointer moves on without marking it COMPLETE. */
    private fun skipStep(s: RepairSessionState): RepairSessionState? {
        if (s.phase != EnginePhase.STEP_ACTIVE &&
            s.phase != EnginePhase.WAITING_FOR_USER &&
            s.phase != EnginePhase.NEXT_STEP
        ) {
            return null
        }
        val skipped = s.stepStates + (s.currentStepNumber to RepairStepState.BLOCKED_BY_USER)
        val nextNumber = s.plan.steps
            .map { it.number }
            .filter { it > s.currentStepNumber }
            .minOrNull()
        return if (nextNumber != null) {
            s.copy(
                stepStates = skipped,
                currentStepNumber = nextNumber,
                phase = EnginePhase.NEXT_STEP,
                lastCompletedAtMillis = System.currentTimeMillis(),
            )
        } else {
            s.copy(
                stepStates = skipped,
                phase = EnginePhase.REPAIR_COMPLETE,
                status = RepairSessionStatus.COMPLETED,
                lastCompletedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    private fun advance(s: RepairSessionState): RepairSessionState? {
        if (s.phase != EnginePhase.READY_FOR_VERIFICATION) return null
        val completed = s.stepStates + (s.currentStepNumber to RepairStepState.COMPLETE)
        val nextNumber = s.plan.steps
            .map { it.number }
            .filter { it > s.currentStepNumber }
            .minOrNull()
        return if (nextNumber != null) {
            s.copy(
                stepStates = completed,
                currentStepNumber = nextNumber,
                phase = EnginePhase.NEXT_STEP,
                lastCompletedAtMillis = System.currentTimeMillis(),
            )
        } else {
            s.copy(
                stepStates = completed,
                phase = EnginePhase.REPAIR_COMPLETE,
                status = RepairSessionStatus.COMPLETED,
                lastCompletedAtMillis = System.currentTimeMillis(),
            )
        }
    }

    /** Completion wording is controlled here — never "Repair confirmed" (spec).
     * Skipped steps are stated honestly instead of being counted as done. */
    fun completionMessage(): String {
        val skipped = _state.value.stepStates.values.count { it == RepairStepState.BLOCKED_BY_USER }
        return if (skipped == 0) {
            "All guided steps completed."
        } else {
            "Guided steps finished — $skipped step${if (skipped == 1) "" else "s"} skipped."
        }
    }

    companion object {
        fun newSessionId(): String = "rep_${UUID.randomUUID()}"
    }
}
