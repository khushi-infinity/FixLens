package com.fixlens.app.demo

import com.fixlens.app.network.ComponentDto
import com.fixlens.app.network.DiagnoseResponseDto
import com.fixlens.app.network.DiagnosisDto
import com.fixlens.app.network.LikelyCauseDto
import com.fixlens.app.network.PlanResponseDto
import com.fixlens.app.network.RepairPlanDto
import com.fixlens.app.network.RepairStepDto
import com.fixlens.app.network.SafetyNoticeDto
import com.fixlens.app.network.VerificationStates
import com.fixlens.app.network.VerifyResponseDto
import com.fixlens.app.network.VisualEvidenceDto
import com.fixlens.app.repair.RepairSessionPlan
import com.fixlens.app.repair.RepairStepSession

/** Pre-authored diagnosis content (provider-agnostic, no SDK/AI types). */
data class DemoDiagnosis(
    val objectName: String,
    val objectCategory: String,
    val issueSummary: String,
    val components: List<DemoComponent>,
    val likelyCauses: List<DemoCause>,
    val confidence: Double,
    val observations: List<DemoObservation>,
    val safetyLevel: String,
    val safetyMessage: String,
    val decision: String,
    val professionalType: String? = null,
    /** Deterministic verification outcome; null for the safety-stop journey. */
    val verificationState: String?,
    val verificationExplanation: String?,
)

data class DemoComponent(val name: String, val observed: Boolean, val status: String?)

data class DemoCause(val text: String, val confidence: Double)

data class DemoObservation(val text: String, val observed: Boolean)

data class DemoStep(
    val title: String,
    val action: String,
    val instruction: String,
    val targetComponent: String,
    val warning: String?,
    val expectedState: String,
)

data class DemoJourney(
    val diagnosis: DemoDiagnosis,
    val steps: List<DemoStep>,
)

/**
 * Mappers into the EXACT wire DTOs the live pipeline emits, so demo runs flow
 * through the unmodified production screens (spec §13: demo uses the real UX).
 */
object DemoMappers {

    fun toDiagnoseResponse(d: DemoDiagnosis): DiagnoseResponseDto = DiagnoseResponseDto(
        diagnosis = DiagnosisDto(
            objectName = d.objectName,
            objectCategory = d.objectCategory,
            components = d.components.map {
                ComponentDto(name = it.name, kind = if (it.observed) "OBSERVED" else "INFERRED", status = it.status)
            },
            issueSummary = d.issueSummary,
            likelyCauses = d.likelyCauses.map { LikelyCauseDto(text = it.text, confidence = it.confidence) },
            confidence = d.confidence,
            confidenceBand = if (d.confidence >= 0.75) "HIGH" else if (d.confidence >= 0.45) "MEDIUM" else "LOW",
            safetyLevel = d.safetyLevel,
            safetyReason = d.safetyMessage,
            observations = d.observations.map {
                VisualEvidenceDto(text = it.text, kind = if (it.observed) "OBSERVED" else "INFERRED")
            },
            needsBetterView = false,
            betterViewInstruction = null,
            professionalType = d.professionalType ?: "NONE",
            mode = "PHOTO",
            providerUsed = "demo",
        ),
        safety = SafetyNoticeDto(
            decision = d.decision,
            userMessage = d.safetyMessage,
            professionalType = d.professionalType ?: "NONE",
        ),
        providerUsed = "demo",
        durationMs = 0,
    )

    fun toPlanResponse(d: DemoDiagnosis, steps: List<DemoStep>): PlanResponseDto = PlanResponseDto(
        status = com.fixlens.app.network.PlanStatuses.PLAN_READY,
        plan = RepairPlanDto(
            objectName = d.objectName,
            issueSummary = d.issueSummary,
            steps = steps.mapIndexed { index, s ->
                RepairStepDto(
                    number = index + 1,
                    title = s.title,
                    action = s.action,
                    instruction = s.instruction,
                    targetComponent = s.targetComponent,
                    toolKnown = false,
                    tool = null,
                    toolNote = "No special tool required beyond what the step names.",
                    warning = s.warning,
                    expectedState = s.expectedState,
                    confirmationRequired = true,
                )
            },
            notes = null,
        ),
        safety = SafetyNoticeDto(
            decision = "GUIDE",
            userMessage = d.safetyMessage,
            professionalType = "NONE",
        ),
        requiresAcknowledgement = false,
        providerUsed = "demo",
        durationMs = 0,
    )

    fun toSessionPlan(d: DemoDiagnosis, steps: List<DemoStep>): RepairSessionPlan = RepairSessionPlan(
        objectName = d.objectName,
        issueSummary = d.issueSummary,
        steps = steps.mapIndexed { index, s ->
            RepairStepSession(
                number = index + 1,
                title = s.title,
                action = s.action,
                instruction = s.instruction,
                targetComponent = s.targetComponent,
                toolKnown = false,
                tool = null,
                toolNote = "No special tool required beyond what the step names.",
                warning = s.warning,
                expectedState = s.expectedState,
                confirmationRequired = true,
            )
        },
        safetyLevel = "LOW",
        safetyMessage = d.safetyMessage,
        requiresAcknowledgement = false,
    )

    /** Deterministic verification result (PASS for demo guided journeys). */
    fun toVerifyResponse(d: DemoDiagnosis, stepNumber: Int): VerifyResponseDto =
        VerifyResponseDto(
            stepNumber = stepNumber,
            state = VerificationStates.PASS,
            confidence = d.confidence,
            explanation = d.verificationExplanation ?: "The step's expected result is visible.",
            needsBetterView = false,
            betterViewInstruction = null,
            providerUsed = "demo",
            durationMs = 0,
        )
}
