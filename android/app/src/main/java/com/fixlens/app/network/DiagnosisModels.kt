package com.fixlens.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire models for POST /api/v1/diagnose — mirrors backend/app/schemas.py.
 * Field names match the backend's JSON exactly; unknown fields are ignored.
 * Phase 3 adds `components`, `observations` (renamed from visual_evidence)
 * and `confidence_band` (controlled confidence representation).
 * Phase 4 adds the repair-plan and assembly wire models (mirrors of the
 * backend's PlanResponse/AssemblyResponse).
 */

@Serializable
data class LikelyCauseDto(
    @SerialName("text") val text: String,
    @SerialName("confidence") val confidence: Double,
)

@Serializable
data class VisualEvidenceDto(
    @SerialName("text") val text: String,
    @SerialName("kind") val kind: String, // OBSERVED | INFERRED | UNKNOWN
)

@Serializable
data class ComponentDto(
    @SerialName("name") val name: String,
    @SerialName("kind") val kind: String = "OBSERVED", // OBSERVED | INFERRED
    @SerialName("status") val status: String? = null,
)

@Serializable
data class DiagnosisDto(
    @SerialName("object_name") val objectName: String,
    @SerialName("object_category") val objectCategory: String,
    @SerialName("components") val components: List<ComponentDto> = emptyList(),
    @SerialName("issue_summary") val issueSummary: String,
    @SerialName("likely_causes") val likelyCauses: List<LikelyCauseDto>,
    @SerialName("confidence") val confidence: Double,
    @SerialName("confidence_band") val confidenceBand: String? = null, // HIGH | MEDIUM | LOW
    @SerialName("safety_level") val safetyLevel: String, // LOW | MEDIUM | HIGH
    @SerialName("safety_reason") val safetyReason: String,
    @SerialName("observations") val observations: List<VisualEvidenceDto>,
    @SerialName("needs_better_view") val needsBetterView: Boolean,
    @SerialName("better_view_instruction") val betterViewInstruction: String? = null,
    @SerialName("professional_type") val professionalType: String? = null,
    @SerialName("mode") val mode: String? = null,
    @SerialName("provider_used") val providerUsed: String? = null,
)

@Serializable
data class SafetyNoticeDto(
    @SerialName("decision") val decision: String, // GUIDE | LIMITED_GUIDE | SAFETY_STOP | ASK_FOR_VIEW
    @SerialName("user_message") val userMessage: String,
    @SerialName("professional_type") val professionalType: String? = null,
)

@Serializable
data class DiagnoseResponseDto(
    @SerialName("diagnosis") val diagnosis: DiagnosisDto,
    @SerialName("safety") val safety: SafetyNoticeDto,
    @SerialName("provider_used") val providerUsed: String,
    @SerialName("duration_ms") val durationMs: Int,
)

// ---------------------------------------------------------------------------
// Phase 4: guided repair planning + assembly (mirrors PlanResponse/AssemblyResponse)
// ---------------------------------------------------------------------------

@Serializable
data class RepairStepDto(
    @SerialName("number") val number: Int,
    @SerialName("title") val title: String,
    @SerialName("action") val action: String,
    @SerialName("instruction") val instruction: String,
    @SerialName("target_component") val targetComponent: String,
    @SerialName("tool_known") val toolKnown: Boolean = false,
    @SerialName("tool") val tool: String? = null,
    @SerialName("tool_note") val toolNote: String? = null,
    @SerialName("warning") val warning: String? = null,
    @SerialName("expected_state") val expectedState: String,
    @SerialName("confirmation_required") val confirmationRequired: Boolean = true,
)

@Serializable
data class RepairPlanDto(
    @SerialName("object_name") val objectName: String,
    @SerialName("issue_summary") val issueSummary: String,
    @SerialName("steps") val steps: List<RepairStepDto>,
    @SerialName("notes") val notes: String? = null,
)

/** Outcome kinds for POST /api/v1/plan. Blocked statuses never carry a plan. */
object PlanStatuses {
    const val PLAN_READY = "PLAN_READY"
    const val BLOCKED_HIGH_RISK = "BLOCKED_HIGH_RISK"
    const val BLOCKED_BETTER_VIEW = "BLOCKED_BETTER_VIEW"
    const val BLOCKED_NO_ISSUE = "BLOCKED_NO_ISSUE"
}

@Serializable
data class PlanResponseDto(
    @SerialName("status") val status: String,
    @SerialName("plan") val plan: RepairPlanDto? = null,
    @SerialName("safety") val safety: SafetyNoticeDto,
    @SerialName("requires_acknowledgement") val requiresAcknowledgement: Boolean = false,
    @SerialName("provider_used") val providerUsed: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

@Serializable
data class AssemblyPlanDto(
    @SerialName("object_name") val objectName: String,
    @SerialName("parts") val parts: List<ComponentDto>,
    @SerialName("steps") val steps: List<RepairStepDto>,
    @SerialName("order_confident") val orderConfident: Boolean,
    @SerialName("requested_view") val requestedView: String? = null,
    @SerialName("safety_level") val safetyLevel: String,
    @SerialName("safety_reason") val safetyReason: String,
)

/** Outcome kinds for POST /api/v1/assembly. */
object AssemblyStatuses {
    const val READY = "READY"
    const val NEEDS_BETTER_VIEW = "NEEDS_BETTER_VIEW"
    const val BLOCKED_HIGH_RISK = "BLOCKED_HIGH_RISK"
}

@Serializable
data class AssemblyResponseDto(
    @SerialName("status") val status: String,
    @SerialName("assembly_plan") val assemblyPlan: AssemblyPlanDto? = null,
    @SerialName("safety") val safety: SafetyNoticeDto,
    @SerialName("provider_used") val providerUsed: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
)

// ---------------------------------------------------------------------------
// Phase 5: camera-based step verification (mirrors VerifyResponse)
// ---------------------------------------------------------------------------

/** Outcome kinds for POST /api/v1/verify (spec §10 VerificationState). */
object VerificationStates {
    const val PASS = "PASS"
    const val FAIL = "FAIL"
    const val UNCERTAIN = "UNCERTAIN"
}

@Serializable
data class VerifyResponseDto(
    @SerialName("step_number") val stepNumber: Int,
    @SerialName("state") val state: String, // PASS | FAIL | UNCERTAIN
    @SerialName("confidence") val confidence: Double,
    @SerialName("explanation") val explanation: String,
    @SerialName("needs_better_view") val needsBetterView: Boolean = false,
    @SerialName("better_view_instruction") val betterViewInstruction: String? = null,
    @SerialName("provider_used") val providerUsed: String? = null,
    @SerialName("duration_ms") val durationMs: Int = 0,
)
