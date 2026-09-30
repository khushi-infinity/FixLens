"""AI provider abstraction (spec §4).

Providers implement [AIProvider]. The rest of the backend only knows the
interface, provider selection lives in selector.py + configuration, never
hardcoded at call sites.
"""
import json
import re
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Any, Dict, List, Optional

from ..schemas import (
    ComponentItem,
    ConfidenceBand,
    DiagnosisResult,
    EvidenceKind,
    LikelyCause,
    Mode,
    ProfessionalType,
    RepairPlan,
    RepairStep,
    SafetyLevel,
    VerificationResult,
    VerificationState as _VerificationState,
    VisualEvidenceItem,
)


if TYPE_CHECKING:  # pragma: no cover
    from ..schemas import AssemblyPlan


class ProviderError(Exception):
    """Base class for controlled provider failures."""


class ProviderUnavailable(ProviderError):
    """Timeout, 5xx, quota/rate limit, or network failure."""


class ProviderInvalidResponse(ProviderError):
    """Provider answered but the payload could not be parsed/validated."""


_VALID_CATEGORIES = {
    "furniture",
    "appliance",
    "electronics",
    "mechanical",
    "electrical",
    "structural",
    "vehicle",
    "tool",
    "other",
}


def extract_json_object(text: str) -> Dict[str, Any]:
    """Safe JSON recovery: plain parse, then code-fence strip, then
    first-balanced-brace extraction. Raises ProviderInvalidResponse when
    nothing parseable remains, never fabricates data."""
    if not text or not text.strip():
        raise ProviderInvalidResponse("Provider returned an empty payload")

    candidates: List[str] = [text.strip()]

    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", text, flags=re.DOTALL)
    if fenced:
        candidates.insert(0, fenced.group(1).strip())

    # First balanced {...} block.
    start = text.find("{")
    if start != -1:
        depth = 0
        for i in range(start, len(text)):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    candidates.append(text[start : i + 1])
                    break

    for candidate in candidates:
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return parsed
        except (json.JSONDecodeError, ValueError):
            continue
    raise ProviderInvalidResponse("Provider response contained no parseable JSON object")


def _as_confidence(value: Any, default: float = 0.0) -> float:
    try:
        return max(0.0, min(1.0, float(value)))
    except (TypeError, ValueError):
        return default


def _as_str_list(value: Any) -> List[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return [str(item) for item in value]
    return []


def _as_items(value: Any, text_key: str, extra_key: Optional[str] = None) -> List[Dict[str, Any]]:
    """Normalizes causes/evidence given as ['a', {'text': 'b'}, ...]."""
    items: List[Dict[str, Any]] = []
    if isinstance(value, dict):
        value = [value]
    if not isinstance(value, list):
        return items
    for entry in value:
        if isinstance(entry, str) and entry.strip():
            item: Dict[str, Any] = {text_key: entry.strip()}
            items.append(item)
        elif isinstance(entry, dict) and str(entry.get(text_key, "")).strip():
            item = {text_key: str(entry[text_key]).strip()}
            if extra_key is not None and extra_key in entry:
                item[extra_key] = entry[extra_key]
            items.append(item)
    return items


def _as_components(value: Any) -> List[ComponentItem]:
    """Normalizes components given as ['seat', {'name': 'base'}, ...].
    String entries are treated as OBSERVED (the model saw them); explicit
    dicts may carry kind/status."""
    items: List[ComponentItem] = []
    if not isinstance(value, list):
        return items
    for entry in value[:12]:
        if isinstance(entry, str) and entry.strip():
            items.append(ComponentItem(name=entry.strip()))
        elif isinstance(entry, dict) and str(entry.get("name", "")).strip():
            try:
                kind = EvidenceKind(str(entry.get("kind", "OBSERVED")).upper())
            except ValueError:
                kind = EvidenceKind.OBSERVED
            status = entry.get("status")
            items.append(
                ComponentItem(
                    name=str(entry["name"]).strip(),
                    kind=kind,
                    status=str(status).strip() if status else None,
                )
            )
    return items


def parse_diagnosis_payload(
    payload: Dict[str, Any],
    mode: Mode,
    provider_name: str,
) -> DiagnosisResult:
    """Maps a model JSON object into the strict schema. Validation errors
    raise ProviderInvalidResponse, malformed model output never passes."""
    try:
        causes = [
            LikelyCause(
                text=item["text"],
                confidence=_as_confidence(item.get("confidence"), 0.5),
            )
            for item in _as_items(payload.get("likely_causes"), "text", "confidence")
        ]
        evidence_items = _as_items(payload.get("visual_evidence"), "text", "kind")
        evidence = [
            VisualEvidenceItem(
                text=item["text"],
                kind=EvidenceKind(str(item.get("kind", "OBSERVED")).upper()),
            )
            for item in evidence_items
        ]
        category = str(payload.get("object_category", "other")).strip().lower()
        if category not in _VALID_CATEGORIES:
            category = "other"

        professional_raw = str(
            payload.get("professional_type_if_needed")
            or payload.get("professional_category_if_needed")
            or "NONE"
        ).strip().upper().replace(" ", "_")
        try:
            professional = ProfessionalType(professional_raw)
        except ValueError:
            professional = ProfessionalType.OTHER if professional_raw not in ("", "NONE") else ProfessionalType.NONE

        needs_better_view = bool(payload.get("needs_better_view", False))
        better_view = payload.get("better_view_instruction") or payload.get("requested_view")

        confidence_score = _as_confidence(payload.get("confidence"), 0.0)

        diagnosis = DiagnosisResult(
            object_name=str(payload.get("object_name", "")).strip(),
            object_category=category,
            components=_as_components(
                payload.get("components") or payload.get("components_visible")
            ),
            issue_summary=str(payload.get("issue_summary") or payload.get("issue") or "").strip(),
            likely_causes=causes,
            confidence=confidence_score,
            confidence_band=ConfidenceBand.from_score(confidence_score),
            safety_level=SafetyLevel(str(payload.get("safety_level", "")).strip().upper()),
            safety_reason=str(payload.get("safety_reason") or payload.get("notes") or "").strip(),
            observations=evidence,
            needs_better_view=needs_better_view,
            better_view_instruction=(str(better_view).strip() if better_view else None),
            professional_type=professional,
            mode=mode,
            provider_used=provider_name,
        )
    except ProviderInvalidResponse:
        raise
    except Exception as exc:  # pydantic ValidationError and mapping gaps
        raise ProviderInvalidResponse(f"Model payload failed schema validation: {exc}") from exc

    return diagnosis


class AIProvider(ABC):
    """Interface every vision provider implements."""

    @property
    @abstractmethod
    def name(self) -> str:
        """Provider identifier used in logs and responses."""

    @abstractmethod
    def diagnose(self, image_jpeg: bytes, mode: Mode, user_context: Optional[str] = None) -> DiagnosisResult:
        """Analyzes one JPEG image and returns a validated DiagnosisResult.

        Implementations must raise ProviderUnavailable for transport-style
        failures and ProviderInvalidResponse for unusable payloads.
        """


def parse_plan_payload(payload: Dict[str, Any], provider_name: str) -> RepairPlan:
    """Maps a model JSON object into a strict RepairPlan. Validation errors
    raise ProviderInvalidResponse, malformed model output never passes."""
    try:
        steps = [_step_from_payload(entry, index) for index, entry in enumerate(payload.get("steps") or [])]
        if not steps:
            raise ProviderInvalidResponse("Plan contained no steps")
        plan = RepairPlan(
            object_name=str(payload.get("object_name", "")).strip(),
            issue_summary=str(payload.get("issue_summary") or payload.get("issue") or "").strip(),
            steps=steps,
            notes=str(payload["notes"]).strip() if payload.get("notes") else None,
        )
    except ProviderInvalidResponse:
        raise
    except Exception as exc:  # pydantic ValidationError and mapping gaps
        raise ProviderInvalidResponse(f"Plan payload failed schema validation: {exc}") from exc
    _ = provider_name  # provider identity is attached by the caller
    return plan


def _step_from_payload(entry: Any, index: int) -> RepairStep:
    if not isinstance(entry, dict):
        raise ProviderInvalidResponse(f"Step {index + 1} is not an object")
    try:
        tool_known = bool(entry.get("tool_known", False))
        tool = str(entry.get("tool")).strip() if entry.get("tool") else None
        if tool and not tool_known:
            # A model that names a tool has effectively identified one.
            tool_known = True
        return RepairStep(
            # Step numbers are positional by construction, the model's own
            # numbering is informational only and is never validated or shown.
            number=index + 1,
            title=str(entry.get("title", "")).strip(),
            action=str(entry.get("action", "")).strip(),
            instruction=str(entry.get("instruction") or entry.get("detailed_instruction") or "").strip(),
            target_component=str(entry.get("target_component") or entry.get("target") or "").strip(),
            tool_known=tool_known,
            tool=tool,
            tool_note=str(entry["tool_note"]).strip() if entry.get("tool_note") else None,
            warning=str(entry["warning"]).strip() if entry.get("warning") else None,
            expected_state=str(entry.get("expected_state") or entry.get("expected_visual_state") or "").strip(),
            confirmation_required=bool(entry.get("confirmation_required", True)),
        )
    except Exception as exc:
        raise ProviderInvalidResponse(f"Step {index + 1} failed schema validation: {exc}") from exc


def parse_assembly_payload(
    payload: Dict[str, Any],
    provider_name: str,
) -> "AssemblyPlan":
    """Maps a model JSON object into a validated AssemblyPlan. The AssemblyPlan
    import is kept local to avoid a circular import at module load."""
    from ..schemas import AssemblyPlan

    try:
        parts = _as_components(payload.get("parts"))
        if not parts:
            raise ProviderInvalidResponse("Assembly plan contained no parts")
        steps = [_step_from_payload(entry, index) for index, entry in enumerate(payload.get("steps") or [])]
        order_confident = bool(payload.get("order_confident", False))
        if order_confident and not steps:
            # Model claimed confidence but produced no sequence, treat as
            # insufficient evidence rather than trusting the flag.
            order_confident = False
        requested_view = payload.get("requested_view")
        if not order_confident and not (requested_view and str(requested_view).strip()):
            requested_view = (
                "Show the connection points or instruction side of the parts so "
                "FixLens can determine the assembly order."
            )
        assembly = AssemblyPlan(
            object_name=str(payload.get("object_name", "")).strip(),
            parts=parts,
            steps=steps,
            order_confident=order_confident,
            requested_view=str(requested_view).strip() if requested_view else None,
            safety_level=SafetyLevel(str(payload.get("safety_level", "")).strip().upper()),
            safety_reason=str(payload.get("safety_reason") or payload.get("notes") or "").strip(),
        )
    except ProviderInvalidResponse:
        raise
    except Exception as exc:
        raise ProviderInvalidResponse(f"Assembly payload failed schema validation: {exc}") from exc
    for index, step in enumerate(assembly.steps, start=1):
        step.number = index
    _ = provider_name
    return assembly


def parse_verify_payload(payload: Dict[str, Any], provider_name: str) -> VerificationResult:
    """Maps a model JSON object into a validated VerificationResult. The
    deterministic view rules (only UNCERTAIN may request a better view, and
    it must always carry one) are enforced by the schema itself, a model
    that returns UNCERTAIN without an instruction still yields a valid,
    honest result with the generic fallback view request."""
    try:
        state_raw = str(payload.get("state", "")).strip().upper()
        # Accept plain-English synonyms the model may emit rather than failing:
        # COMPLETE->PASS, INCOMPLETE->FAIL map from the spec wording.
        state_aliases = {
            "PASS": "PASS",
            "COMPLETE": "PASS",
            "FAIL": "FAIL",
            "FAILED": "FAIL",
            "INCOMPLETE": "FAIL",
            "UNCERTAIN": "UNCERTAIN",
            "UNKNOWN": "UNCERTAIN",
            "UNVERIFIABLE": "UNCERTAIN",
        }
        state = _VerificationState(state_aliases.get(state_raw, ""))
        result = VerificationResult(
            state=state,
            confidence=_as_confidence(payload.get("confidence"), 0.0),
            explanation=str(payload.get("explanation") or payload.get("evidence") or "").strip(),
            needs_better_view=bool(payload.get("needs_better_view", False)),
            better_view_instruction=(
                str(payload["better_view_instruction"]).strip()
                if payload.get("better_view_instruction")
                else None
            ),
        )
    except ProviderInvalidResponse:
        raise
    except Exception as exc:  # pydantic ValidationError and mapping gaps
        raise ProviderInvalidResponse(f"Verification payload failed schema validation: {exc}") from exc
    _ = provider_name  # provider identity is attached by the caller
    return result
