"""Phase 3 tests: components perception, confidence bands, and image quality gate."""
import io
import pytest
from PIL import Image

from app.imaging import ImageValidationError, prepare_for_model
from app.providers.base import ProviderInvalidResponse, parse_diagnosis_payload
from app.safety import apply_safety_policy
from app.schemas import (
    ConfidenceBand,
    EvidenceKind,
    Mode,
    SafetyDecision,
)


def _payload(**overrides):
    payload = {
        "object_name": "office chair",
        "object_category": "furniture",
        "components": [
            {"name": "seat", "kind": "OBSERVED", "status": "intact"},
            {"name": "central cylinder", "kind": "OBSERVED", "status": None},
            "casters",
            {"name": "tilt mechanism", "kind": "INFERRED", "status": "suspected seized"},
        ],
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


class TestComponents:
    def test_components_parsed_with_kinds(self):
        result = parse_diagnosis_payload(_payload(), Mode.PHOTO, "test")
        assert len(result.components) == 4
        assert result.components[0].name == "seat"
        assert result.components[0].kind == EvidenceKind.OBSERVED
        assert result.components[0].status == "intact"
        assert result.components[2].name == "casters"  # bare string -> OBSERVED
        assert result.components[3].kind == EvidenceKind.INFERRED

    def test_components_absent_defaults_empty(self):
        payload = _payload()
        del payload["components"]
        result = parse_diagnosis_payload(payload, Mode.PHOTO, "test")
        assert result.components == []

    def test_components_alias_components_visible(self):
        result = parse_diagnosis_payload(
            _payload(components=None, components_visible=["leg", "hinge"]),
            Mode.PHOTO,
            "test",
        )
        assert [c.name for c in result.components] == ["leg", "hinge"]

    def test_junk_component_entries_skipped(self):
        result = parse_diagnosis_payload(
            _payload(components=[42, {"noname": True}, "", {"name": "  "}]),
            Mode.PHOTO,
            "test",
        )
        assert result.components == []


class TestEmptyCausesPolicy:
    """An image with no visible issue may legitimately have no causes (the
    model must not fabricate). A diagnosed issue requires at least one cause."""

    def test_empty_causes_allowed_for_no_issue(self):
        result = parse_diagnosis_payload(
            _payload(
                issue_summary="no visible issue",
                likely_causes=[],
                needs_better_view=False,
            ),
            Mode.PHOTO,
            "test",
        )
        assert result.likely_causes == []

    def test_empty_causes_rejected_for_real_issue(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_diagnosis_payload(
                _payload(
                    issue_summary="The hinge is visibly bent and loose.",
                    likely_causes=[],
                ),
                Mode.PHOTO,
                "test",
            )


class TestConfidenceBand:
    def test_band_mapping_deterministic(self):
        assert ConfidenceBand.from_score(0.9) == ConfidenceBand.HIGH
        assert ConfidenceBand.from_score(0.75) == ConfidenceBand.HIGH
        assert ConfidenceBand.from_score(0.6) == ConfidenceBand.MEDIUM
        assert ConfidenceBand.from_score(0.45) == ConfidenceBand.MEDIUM
        assert ConfidenceBand.from_score(0.2) == ConfidenceBand.LOW
        assert ConfidenceBand.from_score(0.0) == ConfidenceBand.LOW

    def test_band_set_from_score(self):
        result = parse_diagnosis_payload(_payload(confidence=0.5), Mode.PHOTO, "test")
        assert result.confidence_band == ConfidenceBand.MEDIUM
        result = parse_diagnosis_payload(_payload(confidence=0.1), Mode.PHOTO, "test")
        assert result.confidence_band == ConfidenceBand.LOW

    def test_observations_field_populated(self):
        result = parse_diagnosis_payload(_payload(), Mode.PHOTO, "test")
        assert result.observations[0].text == "Grime around the base joints"
        assert not hasattr(result, "visual_evidence") or "visual_evidence" not in result.model_fields


class TestQualityGate:
    def _jpeg(self, size=(1000, 800), color=(128, 128, 128), noise=False):
        img = Image.new("RGB", size, color)
        if noise:
            # deterministic pseudo-noise so stddev is high
            px = img.load()
            for y in range(0, size[1], 3):
                for x in range(0, size[0], 3):
                    v = (x * 7 + y * 13) % 256
                    px[x, y] = (v, v, v)
        buf = io.BytesIO()
        img.save(buf, format="JPEG")
        return buf.getvalue()

    def test_normal_image_passes(self):
        jpeg, w, h = prepare_for_model(self._jpeg(noise=True))
        assert jpeg and w > 0 and h > 0

    def test_pitch_black_rejected(self):
        with pytest.raises(ImageValidationError) as e:
            prepare_for_model(self._jpeg(color=(0, 0, 0)))
        assert "too dark" in str(e.value)

    def test_blown_out_white_rejected(self):
        with pytest.raises(ImageValidationError) as e:
            prepare_for_model(self._jpeg(color=(255, 255, 255)))
        assert "too bright" in str(e.value)

    def test_flat_lens_blocked_rejected(self):
        # uniform mid-gray: neither dark nor bright, but zero detail
        with pytest.raises(ImageValidationError) as e:
            prepare_for_model(self._jpeg(color=(128, 128, 128)))
        assert "no visible detail" in str(e.value)

    def test_tiny_image_rejected(self):
        with pytest.raises(ImageValidationError) as e:
            prepare_for_model(self._jpeg(size=(100, 80), noise=True))
        assert "too small" in str(e.value)


class TestSafetyStillGatesPhase3:
    def test_high_risk_still_stops_with_components_present(self):
        payload = _payload(
            issue_summary="Exposed mains wiring hanging from the wall.",
            safety_level="HIGH",
            professional_type_if_needed="ELECTRICIAN",
        )
        result = parse_diagnosis_payload(payload, Mode.PHOTO, "test")
        notice, level = apply_safety_policy(result)
        assert level.value == "HIGH"
        assert notice.decision == SafetyDecision.SAFETY_STOP
        assert "electrician" in notice.user_message.lower()

    def test_low_risk_with_observations_guides(self):
        result = parse_diagnosis_payload(_payload(), Mode.PHOTO, "test")
        notice, _ = apply_safety_policy(result)
        assert notice.decision == SafetyDecision.GUIDE
