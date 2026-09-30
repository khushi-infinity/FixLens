"""FixLens structured schemas (spec §10).

Every AI provider response is normalized into DiagnosisResult and validated
with Pydantic before it can leave the backend. Raw provider payloads never
flow to the Android client.
"""

from enum import Enum
from typing import List, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class Mode(str, Enum):
    PHOTO = "PHOTO"
    LIVE = "LIVE"
    VERIFY = "VERIFY"


class SafetyLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class ProfessionalType(str, Enum):
    NONE = "NONE"
    ELECTRICIAN = "ELECTRICIAN"
    PLUMBER = "PLUMBER"
    GAS_TECHNICIAN = "GAS_TECHNICIAN"
    STRUCTURAL_ENGINEER = "STRUCTURAL_ENGINEER"
    BATTERY_TECHNICIAN = "BATTERY_TECHNICIAN"
    MACHINERY_TECHNICIAN = "MACHINERY_TECHNICIAN"
    APPLIANCE_TECHNICIAN = "APPLIANCE_TECHNICIAN"
    OTHER = "OTHER"


class EvidenceKind(str, Enum):
    """Distinguishes what the model actually saw from what it assumed."""
    OBSERVED = "OBSERVED"
    INFERRED = "INFERRED"
    UNKNOWN = "UNKNOWN"


class ConfidenceBand(str, Enum):
    """Controlled confidence representation (Phase 3): the UI communicates
    bands rather than pretending the model has calibrated numeric certainty."""
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


@classmethod
def _band_from_score(cls, score: float) -> "ConfidenceBand":
    """Deterministic numeric -> band mapping (single source of truth)."""
    if score >= 0.75:
        return cls.HIGH
    if score >= 0.45:
        return cls.MEDIUM
    return cls.LOW


ConfidenceBand.from_score = _band_from_score


class VisualEvidenceItem(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)
    kind: EvidenceKind


class LikelyCause(BaseModel):
    text: str = Field(..., min_length=1, max_length=500)
    confidence: float = Field(..., ge=0.0, le=1.0)


class ComponentItem(BaseModel):
    """One object part the model names. `status` describes the part's visible
    condition ('intact', 'loose', 'seized', ...); parts the model assumes but
    cannot actually see carry kind=INFERRED (never presented as seen)."""
    name: str = Field(..., min_length=1, max_length=120)
    kind: EvidenceKind = EvidenceKind.OBSERVED
    status: Optional[str] = Field(None, max_length=120)


class DiagnosisResult(BaseModel):
    """The normalized, validated diagnosis, the only diagnosis shape the
    Android app ever receives."""

    object_name: str = Field(..., min_length=1, max_length=200)
    object_category: str = Field(..., min_length=1, max_length=100)
    components: List[ComponentItem] = Field(default_factory=list, max_length=12)
    issue_summary: str = Field(..., min_length=1, max_length=1000)
    # Non-empty enforced conditionally by _causes_required_only_for_real_issues
    # (a "no visible issue" result legitimately has no causes, never fabricate).
    likely_causes: List[LikelyCause] = Field(..., max_length=6)
    confidence: float = Field(..., ge=0.0, le=1.0)
    confidence_band: ConfidenceBand
    safety_level: SafetyLevel
    safety_reason: str = Field(..., min_length=1, max_length=1000)
    observations: List[VisualEvidenceItem] = Field(..., min_length=1, max_length=10)
    needs_better_view: bool
    better_view_instruction: Optional[str] = Field(None, max_length=500)
    professional_type: ProfessionalType = ProfessionalType.NONE
    mode: Mode
    provider_used: Optional[str] = None

    @field_validator("better_view_instruction")
    @classmethod
    def _view_required_when_needed(cls, v: Optional[str], info):
        needs = info.data.get("needs_better_view")
        if needs and not (v and v.strip()):
            raise ValueError(
                "better_view_instruction is required when needs_better_view is true"
            )
        return v

    @field_validator("likely_causes")
    @classmethod
    def _causes_required_only_for_real_issues(cls, v: List[LikelyCause], info):
        """An image with no visible issue legitimately has no causes, the
        model must not fabricate any (spec: never fabricate). A diagnosed
        issue, however, must be supported by at least one cause."""
        no_issue = is_no_visible_issue_text(info.data.get("issue_summary", ""))
        if not v and not no_issue:
            raise ValueError(
                "likely_causes must not be empty when an issue is diagnosed"
            )
        return v


def is_no_visible_issue_text(text: str) -> bool:
    """Single source of truth for 'the model honestly reported no issue'.
    Shared by the likely-causes validator and the Phase 4 plan gate (a
    healthy object must never receive a fabricated repair plan)."""
    issue = (text or "").strip().lower()
    return issue in ("", "none", "no visible issue", "no issue visible") or (
        issue.startswith("no visible issue")
    )


class SafetyDecision(str, Enum):
    """Deterministic action the safety policy layer derives (spec §10 ActionType subset)."""
    GUIDE = "GUIDE"
    LIMITED_GUIDE = "LIMITED_GUIDE"
    SAFETY_STOP = "SAFETY_STOP"
    ASK_FOR_VIEW = "ASK_FOR_VIEW"


class SafetyNotice(BaseModel):
    """Deterministic safety gate applied AFTER the model result (spec §8).
    HIGH risk discards any model instructions and mandates professional help."""
    decision: SafetyDecision
    user_message: str = Field(..., min_length=1, max_length=1200)
    professional_type: ProfessionalType


class DiagnoseResponse(BaseModel):
    """Full endpoint response: diagnosis + deterministic safety decision."""
    diagnosis: DiagnosisResult
    safety: SafetyNotice
    provider_used: str
    duration_ms: int


# ---------------------------------------------------------------------------#
# Phase 4: guided repair + assembly planning (spec §17.6)
# ---------------------------------------------------------------------------#

class RepairStep(BaseModel):
    """One guided step. The guidance screen renders exactly these fields,
    there is no free-form prose block. `tool_known=False` means the required
    tool could NOT be determined from the evidence; the UI must then show the
    generic fallback, never a guessed tool name."""

    number: int = Field(..., ge=1, le=50)
    title: str = Field(..., min_length=1, max_length=120)
    action: str = Field(..., min_length=1, max_length=300)
    instruction: str = Field(..., min_length=1, max_length=1000)
    target_component: str = Field(..., min_length=1, max_length=200)
    tool_known: bool = False
    tool: Optional[str] = Field(None, max_length=120)
    tool_note: Optional[str] = Field(None, max_length=200)
    warning: Optional[str] = Field(None, max_length=300)
    expected_state: str = Field(..., min_length=1, max_length=300)
    confirmation_required: bool = True

    @field_validator("tool", "tool_note", "warning")
    @classmethod
    def _blank_becomes_none(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        stripped = v.strip()
        return stripped if stripped else None

    @model_validator(mode="after")
    def _tool_consistency(self) -> "RepairStep":
        if self.tool_known and not self.tool:
            raise ValueError("tool is required when tool_known is true")
        if not self.tool_known and self.tool:
            raise ValueError("tool must be null when tool_known is false")
        return self


class RepairPlan(BaseModel):
    """Structured repair plan generated from a VALIDATED diagnosis."""

    object_name: str = Field(..., min_length=1, max_length=200)
    issue_summary: str = Field(..., min_length=1, max_length=1000)
    steps: List[RepairStep] = Field(..., min_length=1, max_length=15)
    notes: Optional[str] = Field(None, max_length=500)

    @field_validator("notes")
    @classmethod
    def _blank_notes_none(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        stripped = v.strip()
        return stripped if stripped else None


class AssemblyPlan(BaseModel):
    """Structured assembly plan. `order_confident=False` means the visual
    evidence does not support a sequence: steps MUST be empty and exactly one
    requested_view names the shot that would settle the order."""

    object_name: str = Field(..., min_length=1, max_length=200)
    parts: List[ComponentItem] = Field(..., min_length=1, max_length=20)
    steps: List[RepairStep] = Field(default_factory=list, max_length=20)
    order_confident: bool
    requested_view: Optional[str] = Field(None, max_length=500)
    safety_level: SafetyLevel
    safety_reason: str = Field(..., min_length=1, max_length=1000)

    @field_validator("requested_view")
    @classmethod
    def _blank_view_none(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        stripped = v.strip()
        return stripped if stripped else None

    @model_validator(mode="after")
    def _order_rules(self) -> "AssemblyPlan":
        if self.order_confident:
            if not self.steps:
                raise ValueError("steps are required when order_confident is true")
        else:
            if self.steps:
                raise ValueError(
                    "steps must be empty when order_confident is false (never guess an order)"
                )
            if not self.requested_view:
                raise ValueError(
                    "requested_view is required when order_confident is false"
                )
        return self


class PlanStatus(str, Enum):
    """Outcome of a repair-plan request. Blocked states carry NO plan object,
    there is no code path that can render steps for them."""
    PLAN_READY = "PLAN_READY"
    BLOCKED_HIGH_RISK = "BLOCKED_HIGH_RISK"
    BLOCKED_BETTER_VIEW = "BLOCKED_BETTER_VIEW"
    BLOCKED_NO_ISSUE = "BLOCKED_NO_ISSUE"


class PlanRequest(BaseModel):
    """Body of POST /api/v1/plan: the diagnosis the Android app already
    received from /api/v1/diagnose (re-validated here), plus optional user
    context. The image is NOT re-uploaded, planning consumes the validated
    diagnosis, not the photo."""
    diagnosis: DiagnosisResult
    context: Optional[str] = Field(None, max_length=500)


class PlanResponse(BaseModel):
    status: PlanStatus
    plan: Optional[RepairPlan] = None
    safety: SafetyNotice
    # True when the final safety level is MEDIUM: the app must show the safety
    # warning and get an explicit acknowledgement BEFORE revealing any step.
    requires_acknowledgement: bool = False
    provider_used: Optional[str] = None
    duration_ms: int = 0

    @model_validator(mode="after")
    def _plan_presence_matches_status(self) -> "PlanResponse":
        if self.status == PlanStatus.PLAN_READY and self.plan is None:
            raise ValueError("plan is required when status is PLAN_READY")
        if self.status != PlanStatus.PLAN_READY and self.plan is not None:
            raise ValueError("plan must be null when the request is blocked")
        return self


class AssemblyStatus(str, Enum):
    READY = "READY"
    NEEDS_BETTER_VIEW = "NEEDS_BETTER_VIEW"
    BLOCKED_HIGH_RISK = "BLOCKED_HIGH_RISK"


class AssemblyResponse(BaseModel):
    status: AssemblyStatus
    assembly_plan: Optional[AssemblyPlan] = None
    safety: SafetyNotice
    provider_used: Optional[str] = None
    duration_ms: int = 0

    @model_validator(mode="after")
    def _plan_presence_matches_status(self) -> "AssemblyResponse":
        if self.status == AssemblyStatus.READY and self.assembly_plan is None:
            raise ValueError("assembly_plan is required when status is READY")
        if self.status == AssemblyStatus.BLOCKED_HIGH_RISK and self.assembly_plan is not None:
            raise ValueError(
                "assembly_plan must be discarded when the result is blocked for HIGH risk"
            )
        return self


# ---------------------------------------------------------------------------#
# Phase 5: camera-based verification (spec §18.5)
# ---------------------------------------------------------------------------#

class VerificationState(str, Enum):
    """Result of comparing the current visual state with the expected state
    for a repair step (spec §10 VerificationState)."""
    PASS = "PASS"
    FAIL = "FAIL"
    UNCERTAIN = "UNCERTAIN"


class VerificationRequest(BaseModel):
    """Body of POST /api/v1/verify: the step being verified (its expected
    state, action, and target so the model compares against the full step
    context) plus a fresh capture of the object now.

    Verification is explicitly user-triggered, the Android client uploads
    one frame per explicit request and never streams (spec §15: no
    continuous LLM calls on camera frames)."""
    step_number: int = Field(..., ge=1, le=50)
    expected_state: str = Field(..., min_length=1, max_length=300)
    step_action: Optional[str] = Field(None, max_length=300)
    target_component: Optional[str] = Field(None, max_length=200)
    user_confirms_done: bool = True
    # Marker only: the capture travels as multipart bytes and is quality-gated
    # by validate_upload() before any model call, never re-embedded here.
    current_image: str = Field(..., min_length=1)
    mode: Mode = Mode.VERIFY

    @field_validator("expected_state", "step_action", "target_component")
    @classmethod
    def _blank_becomes_none(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        stripped = v.strip()
        return stripped if stripped else None

    @field_validator("expected_state")
    @classmethod
    def _expected_state_required(cls, v: Optional[str]) -> Optional[str]:
        if not v:
            raise ValueError("expected_state is required for verification")
        return v

    @field_validator("mode")
    @classmethod
    def _mode_is_verify(cls, v: Mode) -> Mode:
        if v != Mode.VERIFY:
            raise ValueError("verification requests must use mode=VERIFY")
        return v


class VerificationResult(BaseModel):
    """One comparison of expected vs current visual state. Deterministic
    law: only UNCERTAIN may request a better view, and it must always do so
    with exactly one instruction."""
    state: VerificationState
    confidence: float = Field(..., ge=0.0, le=1.0)
    explanation: str = Field(..., min_length=1, max_length=500)
    needs_better_view: bool = False
    better_view_instruction: Optional[str] = Field(None, max_length=500)

    @field_validator("explanation")
    @classmethod
    def _explanation_required(cls, v: str) -> str:
        stripped = (v or "").strip()
        if not stripped:
            raise ValueError("explanation is required (cite the visual evidence)")
        return stripped

    @field_validator("better_view_instruction")
    @classmethod
    def _blank_view_none(cls, v: Optional[str]) -> Optional[str]:
        if v is None:
            return None
        stripped = v.strip()
        return stripped if stripped else None

    @model_validator(mode="after")
    def _view_rules(self) -> "VerificationResult":
        if self.state == VerificationState.UNCERTAIN:
            if not self.needs_better_view:
                self.needs_better_view = True
            if not (self.better_view_instruction and self.better_view_instruction.strip()):
                self.better_view_instruction = (
                    "Move the camera closer to "
                    "the area you worked on and fill the frame with it."
                )
        else:
            self.needs_better_view = False
            self.better_view_instruction = None
        return self


class VerifyResponse(BaseModel):
    """Full response from POST /api/v1/verify. Android maps state to its own
    VerificationResult seam (PASS→Verified, FAIL→FailedMismatch,
    UNCERTAIN→Inconclusive); the user-facing wordings live client-side."""
    step_number: int
    state: VerificationState
    confidence: float = Field(..., ge=0.0, le=1.0)
    explanation: str = Field(..., min_length=1, max_length=500)
    needs_better_view: bool = False
    better_view_instruction: Optional[str] = Field(None, max_length=500)
    provider_used: Optional[str] = None
    duration_ms: int = 0