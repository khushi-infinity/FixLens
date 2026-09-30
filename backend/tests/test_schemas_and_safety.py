"""Offline tests: strict schema validation + deterministic safety policy.

No network, no provider, the model payload is simulated. These prove that
malformed model output can never silently pass and that HIGH risk always
stops.
"""
import pytest
from pydantic import ValidationError

from app.providers.base import ProviderInvalidResponse, parse_diagnosis_payload
from app.safety import apply_safety_policy
from app.schemas import Mode, SafetyDecision, SafetyLevel


def _valid_payload(**overrides):
    payload = {
        "object_name": "office chair",
        "object_category": "furniture",
        "issue_summary": "The chair does not swivel; the base appears seized.",
        "likely_causes": [{"text": "Debris in the tilt mechanism", "confidence": 0.6}],
        "confidence": 0.8,
        "safety_level": "LOW",
        "safety_reason": "The image shows only normal surface wear.",
        "visual_evidence": [{"text": "Grime around the base joints", "kind": "OBSERVED"}],
        "needs_better_view": False,
        "better_view_instruction": None,
        "professional_type_if_needed": "NONE",
    }
    payload.update(overrides)
    return payload


class TestSchemaValidation:
    def test_valid_payload_parses(self):
        result = parse_diagnosis_payload(_valid_payload(), Mode.PHOTO, "gemini")
        assert result.object_name == "office chair"
        assert result.safety_level == SafetyLevel.LOW
        assert result.confidence == 0.8

    def test_invalid_safety_level_rejected(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_diagnosis_payload(
                _valid_payload(safety_level="EXTREMELY_DANGEROUS"), Mode.PHOTO, "test"
            )

    def test_out_of_range_confidence_clamped(self):
        """Numeric normalization policy: confidences are bounded to [0,1]
        (a percent-style 85.0 would also clamp). Structural violations,
        missing fields, bad enums, empty lists, are rejected outright."""
        result = parse_diagnosis_payload(_valid_payload(confidence=1.7), Mode.PHOTO, "test")
        assert result.confidence == 1.0
        result = parse_diagnosis_payload(_valid_payload(confidence=-0.4), Mode.PHOTO, "test")
        assert result.confidence == 0.0
        result = parse_diagnosis_payload(_valid_payload(confidence="nonsense"), Mode.PHOTO, "test")
        assert result.confidence == 0.0

    def test_missing_object_name_rejected(self):
        payload = _valid_payload()
        del payload["object_name"]
        with pytest.raises(ProviderInvalidResponse):
            parse_diagnosis_payload(payload, Mode.PHOTO, "test")

    def test_empty_causes_rejected(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_diagnosis_payload(_valid_payload(likely_causes=[]), Mode.PHOTO, "test")

    def test_better_view_true_without_instruction_rejected(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_diagnosis_payload(
                _valid_payload(needs_better_view=True, better_view_instruction=None),
                Mode.PHOTO,
                "test",
            )

    def test_unknown_category_normalized_to_other(self):
        result = parse_diagnosis_payload(
            _valid_payload(object_category="spacecraft"), Mode.PHOTO, "test"
        )
        assert result.object_category == "other"

    def test_evidence_kind_normalized(self):
        result = parse_diagnosis_payload(
            _valid_payload(visual_evidence=[{"text": "grime", "kind": "observed"}]),
            Mode.PHOTO,
            "test",
        )
        assert result.observations[0].kind.value == "OBSERVED"  # renamed in Phase 3 (§9)


class TestJsonRecovery:
    def test_plain_json(self):
        from app.providers.base import extract_json_object

        assert extract_json_object('{"a": 1}') == {"a": 1}

    def test_fenced_json(self):
        from app.providers.base import extract_json_object

        text = "Here is the analysis:\n```json\n{\"a\": 2}\n```"
        assert extract_json_object(text) == {"a": 2}

    def test_json_with_leading_prose(self):
        from app.providers.base import extract_json_object

        text = 'Sure! {"a": {"b": 3}} hope that helps'
        assert extract_json_object(text) == {"a": {"b": 3}}

    def test_garbage_raises_controlled_error(self):
        from app.providers.base import extract_json_object

        with pytest.raises(ProviderInvalidResponse):
            extract_json_object("the model babbled with no json at all")


class TestSafetyPolicy:
    def _diagnosis(self, **overrides):
        return parse_diagnosis_payload(_valid_payload(**overrides), Mode.PHOTO, "test")

    def test_low_guides(self):
        notice, level = apply_safety_policy(self._diagnosis())
        assert notice.decision == SafetyDecision.GUIDE
        assert level == SafetyLevel.LOW

    def test_medium_limited(self):
        notice, level = apply_safety_policy(self._diagnosis(safety_level="MEDIUM"))
        assert notice.decision == SafetyDecision.LIMITED_GUIDE

    def test_high_from_model_stops(self):
        payload = _valid_payload(
            issue_summary="Exposed mains wiring near the panel.",
            safety_level="HIGH",
            professional_type_if_needed="ELECTRICIAN",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert level == SafetyLevel.HIGH
        assert notice.decision == SafetyDecision.SAFETY_STOP
        assert "electrician" in notice.user_message.lower()

    def test_policy_escalates_hidden_high_risk(self):
        """Model says LOW but the corpus contains an exposure pattern,
        the deterministic layer must escalate."""
        payload = _valid_payload(
            issue_summary="Loose faceplate with exposed wiring behind it.",
            safety_level="LOW",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert level == SafetyLevel.HIGH
        assert notice.decision == SafetyDecision.SAFETY_STOP

    def test_better_view_overrides_guide(self):
        payload = _valid_payload(
            needs_better_view=True,
            better_view_instruction="Move the camera underneath the chair and show the central rotation mechanism.",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert notice.decision == SafetyDecision.ASK_FOR_VIEW
        assert "underneath" in notice.user_message

    def test_gas_leak_escalates_and_names_professional(self):
        payload = _valid_payload(
            object_category="appliance",
            issue_summary="Strong smell of gas near the stove connection.",
            safety_level="MEDIUM",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert level == SafetyLevel.HIGH
        assert notice.decision == SafetyDecision.SAFETY_STOP

    def test_preventive_tip_over_degrades_to_limited(self):
        """A missing screw whose only cited risk is a FUTURE tip-over is a
        warning, not a professional referral. Regression: a laptop stand with
        a missing screw was flagged hazardous because the model rated the
        hypothetical future state instead of the present one."""
        payload = _valid_payload(
            issue_summary="The stand is missing one mounting screw.",
            safety_level="HIGH",
            safety_reason="A loose stand could eventually present a tip-over hazard.",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert level == SafetyLevel.MEDIUM
        assert notice.decision == SafetyDecision.LIMITED_GUIDE

    def test_present_hazard_still_stops_despite_preventive_words(self):
        """Preventive wording must not shield a real present-tense hazard."""
        payload = _valid_payload(
            issue_summary="Loose faceplate with exposed wiring behind it.",
            safety_level="LOW",
            safety_reason="Could eventually cause a shock if touched.",
        )
        notice, level = apply_safety_policy(parse_diagnosis_payload(payload, Mode.PHOTO, "test"))
        assert level == SafetyLevel.HIGH
        assert notice.decision == SafetyDecision.SAFETY_STOP
