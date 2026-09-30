"""Deterministic safety policy layer (spec §8).

The LLM proposes a safety_level; this layer DECIDES what the app may show.
It never calls a model. HIGH risk always produces SAFETY_STOP and discards
any model-authored instructions (Phase 2 only ever returns a diagnosis, but
the gate already enforces the final behavior so later phases inherit it).
"""
import re
from typing import Tuple

from .schemas import (
    AssemblyPlan,
    DiagnosisResult,
    ProfessionalType,
    SafetyDecision,
    SafetyLevel,
    SafetyNotice,
)

# High-risk categories (spec §8 examples). Keyword matching is deliberately
# conservative: when in doubt, escalate. Patterns are lowercase substrings.
HIGH_RISK_PATTERNS = [
    r"exposed (mains |main )?(electrical )?wiring",
    r"exposed wire",
    r"bare wire",
    r"\bwiring hazard\b",
    r"gas leak",
    r"smell of gas",
    r"lithium[- ]?ion battery (damage|damaged|swollen|punctured|venting)",
    r"swollen battery",
    r"punctured battery",
    r"battery (fire|smoke|venting|thermal)",
    r"high[- ]?voltage",
    r"mains voltage",
    r"breaker panel (open|damaged|exposed)",
    r"structural (damage|crack|failure|instability|collapse)",
    r"load[- ]bearing .*(crack|damage|broken)",
    r"ceiling (sag|sagging|collapse)",
    r"(jagged|exposed) (metal|edge)",
    r"dangerous machinery",
    r"live (electrical|wire|circuit)",
    r"electrical (fire|spark|arcing|burn)",
    r"scorch(ing|ed)? (mark|wire|outlet|plug)",
    r"burnt (plug|outlet|wire|smell)",
    r"water .*(electrical|outlet|wiring|panel)",
]

MEDIUM_ESCALATION_PATTERNS = [
    r"sharp edge",
    r"pinch (point|hazard)",
    r"hot surface",
    r"water (near|close to) (outlet|electrical)",
    r"wobbly .*(ladder|shelf|stand)",
]


def _matches(text: str, patterns) -> bool:
    return any(re.search(p, text, flags=re.IGNORECASE) for p in patterns)


def apply_safety_policy(diagnosis: DiagnosisResult) -> Tuple[SafetyNotice, SafetyLevel]:
    """Applies the deterministic gate. Returns (notice, final_safety_level).

    Rules (spec §8):
      - HIGH  -> SAFETY_STOP, professional help required, no instructions.
      - MEDIUM -> LIMITED_GUIDE with explicit warnings.
      - LOW   -> GUIDE.
      - needs_better_view=True overrides to ASK_FOR_VIEW (evidence first).
      - Policy may ESCALATE the model's level; it never de-escalates HIGH.
    """
    corpus = " ".join(
        [
            diagnosis.issue_summary,
            diagnosis.safety_reason,
            diagnosis.object_category,
            *[e.text for e in diagnosis.observations],
            *[f"{c.name} {c.status or ''}" for c in diagnosis.components],
        ]
    )

    model_level = diagnosis.safety_level
    final_level = model_level

    # Deterministic escalation on known high-risk patterns.
    if _matches(corpus, HIGH_RISK_PATTERNS):
        final_level = SafetyLevel.HIGH

    # Insufficient evidence takes priority: ask for one specific view.
    if diagnosis.needs_better_view:
        instruction = diagnosis.better_view_instruction or (
            "Capture one clearer view of the object so FixLens can assess it safely."
        )
        return (
            SafetyNotice(
                decision=SafetyDecision.ASK_FOR_VIEW,
                user_message=f"FixLens needs a better view before it can decide safely. {instruction}",
                professional_type=ProfessionalType.NONE,
            ),
            final_level,
        )

    if final_level == SafetyLevel.HIGH:
        professional = (
            diagnosis.professional_type
            if diagnosis.professional_type != ProfessionalType.NONE
            else ProfessionalType.OTHER
        )
        professional_text = professional.value.replace("_", " ").lower()
        return (
            SafetyNotice(
                decision=SafetyDecision.SAFETY_STOP,
                user_message=(
                    f"This appears to involve a serious hazard ({professional_text}). "
                    "FixLens won't provide instructions for manipulating it because of "
                    f"the risk of serious injury. Please contact a qualified {professional_text}."
                ),
                professional_type=professional,
            ),
            final_level,
        )

    if final_level == SafetyLevel.MEDIUM or _matches(corpus, MEDIUM_ESCALATION_PATTERNS):
        return (
            SafetyNotice(
                decision=SafetyDecision.LIMITED_GUIDE,
                user_message=(
                    "FixLens can only offer limited guidance here. Work slowly, keep "
                    "hands clear of anything sharp, hot, or energized, and stop if "
                    "anything looks unsafe."
                ),
                professional_type=ProfessionalType.NONE,
            ),
            final_level,
        )

    return (
        SafetyNotice(
            decision=SafetyDecision.GUIDE,
            user_message="No significant hazards detected in this image.",
            professional_type=ProfessionalType.NONE,
        ),
        final_level,
    )


def apply_assembly_safety_policy(assembly: AssemblyPlan) -> Tuple[SafetyNotice, SafetyLevel]:
    """Phase 4 assembly gate: same deterministic rules as apply_safety_policy,
    applied to the assembly plan's own safety_level + corpus (safety happens
    BEFORE any step is shown, a blocked assembly plan is discarded)."""
    corpus = " ".join(
        [
            assembly.object_name,
            assembly.safety_reason,
            *[f"{p.name} {p.status or ''}" for p in assembly.parts],
            *[s.target_component for s in assembly.steps],
        ]
    )

    final_level = assembly.safety_level
    if _matches(corpus, HIGH_RISK_PATTERNS):
        final_level = SafetyLevel.HIGH

    if final_level == SafetyLevel.HIGH:
        return (
            SafetyNotice(
                decision=SafetyDecision.SAFETY_STOP,
                user_message=(
                    "This appears to involve a serious hazard. FixLens won't provide "
                    "assembly instructions because of the risk of serious injury. "
                    "Please contact a qualified professional."
                ),
                professional_type=ProfessionalType.OTHER,
            ),
            final_level,
        )

    if final_level == SafetyLevel.MEDIUM or _matches(corpus, MEDIUM_ESCALATION_PATTERNS):
        return (
            SafetyNotice(
                decision=SafetyDecision.LIMITED_GUIDE,
                user_message=(
                    "FixLens can only offer limited guidance here. Work slowly, keep "
                    "hands clear of anything sharp, hot, or energized, and stop if "
                    "anything looks unsafe."
                ),
                professional_type=ProfessionalType.NONE,
            ),
            final_level,
        )

    return (
        SafetyNotice(
            decision=SafetyDecision.GUIDE,
            user_message="No significant hazards detected in this image.",
            professional_type=ProfessionalType.NONE,
        ),
        final_level,
    )
