"""Phase 5 tests: camera-based verification (schemas, parser, endpoint).

No real API calls — providers are injected fakes via the patch_chain
fixture (same pattern as test_phase4_planning.py).

Key invariants under test:
  - POST /api/v1/verify normalizes model output into VerifyResponse; the
    raw provider payload never reaches the client.
  - Only UNCERTAIN may request a better view, and it must always carry one
    (schema law: forced True + fallback instruction; PASS/FAIL stripped).
  - Plain-English model wording (COMPLETE/INCOMPLETE) is aliased, never
    trusted as-is; unknown states are rejected as invalid responses (502).
  - Verification is separate from diagnosis: no safety gate and no plan
    generation touches this endpoint.
  - Transport failures map to 503, invalid payloads to 502, bad requests
    to 400 — all with friendly messages.
"""
import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.providers.base import (
    ProviderInvalidResponse,
    ProviderUnavailable,
    parse_verify_payload,
)
from app.schemas import Mode, VerificationState

client = TestClient(app)


def _jpeg_bytes(color=(120, 90, 60), size=(640, 480)) -> bytes:
    img = Image.new("RGB", size, color)
    px = img.load()
    for y in range(0, size[1], 3):
        for x in range(0, size[0], 3):
            v = (x * 7 + y * 13 + color[0]) % 256
            px[x, y] = (v, v, v)
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def _verify_payload(state="PASS", confidence=0.9, explanation="screw head sits flush", **extra):
    payload = {
        "state": state,
        "confidence": confidence,
        "explanation": explanation,
        "needs_better_view": False,
        "better_view_instruction": None,
    }
    payload.update(extra)
    return payload


# ---------------------------------------------------------------------------
# Fake providers (verify-capable)
# ---------------------------------------------------------------------------
class FakeVerifyProvider:
    name = "fake-verify"

    def __init__(self, payload=None, error=None):
        self.payload = payload or _verify_payload()
        self.error = error
        self.verify_calls = 0
        self.last_request = None
        self.last_image = None

    def verify(self, request, image_jpeg):
        self.verify_calls += 1
        self.last_request = request
        self.last_image = image_jpeg
        if self.error is not None:
            raise self.error
        return parse_verify_payload(self.payload, self.name)

    def diagnose(self, image_jpeg, mode, user_context=None):  # pragma: no cover
        raise ProviderUnavailable("not used in these tests")


class FakePlanOnlyProvider(FakeVerifyProvider):
    """A Phase 4-era provider with no verify capability."""

    name = "fake-plan-only"

    def verify(self, request, image_jpeg):  # pragma: no cover
        raise ProviderUnavailable("does not support verification")


@pytest.fixture
def patch_chain(monkeypatch):
    state = {"gemini": None, "openrouter": None}

    def fake_build(name):
        provider = state.get(name)
        if provider is None:
            raise ProviderUnavailable(f"No fake provider registered for {name}")
        return provider

    monkeypatch.setattr("app.providers.selector._build_provider", fake_build)
    return state


@pytest.fixture(autouse=True)
def _default_chain(patch_chain):
    """Both endpoints in the chain need a usable provider so unrelated tests
    that exercise fallback paths stay deterministic."""
    patch_chain["gemini"] = FakeVerifyProvider()
    patch_chain["openrouter"] = FakeVerifyProvider()


def _post_verify(**overrides):
    data = {"step_number": "1", "expected_state": "The screw is visibly tight."}
    files = {"image": ("verify.jpg", _jpeg_bytes(), "image/jpeg")}
    data.update(overrides)
    return client.post("/api/v1/verify", data=data, files=files)


# ---------------------------------------------------------------------------
# Schema-level rules
# ---------------------------------------------------------------------------
class TestVerificationSchemaRules:
    def test_uncertain_always_carries_view_request(self):
        from app.schemas import VerificationResult

        result = VerificationResult(
            state=VerificationState.UNCERTAIN,
            confidence=0.2,
            explanation="Relevant part not visible in frame.",
        )
        assert result.needs_better_view is True
        assert result.better_view_instruction  # generic fallback filled in

    def test_pass_strips_better_view(self):
        from app.schemas import VerificationResult

        result = VerificationResult(
            state=VerificationState.PASS,
            confidence=0.9,
            explanation="Flush.",
            needs_better_view=True,
            better_view_instruction="nope",
        )
        assert result.needs_better_view is False
        assert result.better_view_instruction is None

    def test_fail_strips_better_view(self):
        from app.schemas import VerificationResult

        result = VerificationResult(
            state=VerificationState.FAIL,
            confidence=0.85,
            explanation="Gap still visible.",
            needs_better_view=True,
            better_view_instruction="nope",
        )
        assert result.needs_better_view is False
        assert result.better_view_instruction is None

    def test_explanation_required(self):
        from pydantic import ValidationError

        from app.schemas import VerificationResult

        with pytest.raises(ValidationError):
            VerificationResult(state=VerificationState.PASS, confidence=0.9, explanation="   ")

    def test_confidence_bounded(self):
        from pydantic import ValidationError

        from app.schemas import VerificationResult

        with pytest.raises(ValidationError):
            VerificationResult(
                state=VerificationState.PASS, confidence=1.7, explanation="too sure"
            )

    def test_request_requires_expected_state(self):
        from pydantic import ValidationError

        from app.schemas import VerificationRequest

        with pytest.raises(ValidationError):
            VerificationRequest(
                step_number=1,
                expected_state="   ",
                current_image="a" * 60,
            )

    def test_request_rejects_non_verify_mode(self):
        from pydantic import ValidationError

        from app.schemas import VerificationRequest

        with pytest.raises(ValidationError):
            VerificationRequest(
                step_number=1,
                expected_state="tight screw",
                current_image="a" * 60,
                mode=Mode.PHOTO,
            )


# ---------------------------------------------------------------------------
# Parser
# ---------------------------------------------------------------------------
class TestParseVerifyPayload:
    def test_complete_aliases_to_pass(self):
        result = parse_verify_payload(
            _verify_payload(state="COMPLETE", confidence=0.88), "test"
        )
        assert result.state is VerificationState.PASS

    def test_incomplete_aliases_to_fail(self):
        result = parse_verify_payload(
            _verify_payload(state="INCOMPLETE", confidence=0.8, explanation="still loose"),
            "test",
        )
        assert result.state is VerificationState.FAIL

    def test_uncertain_gets_forced_view(self):
        result = parse_verify_payload(
            _verify_payload(
                state="UNCERTAIN",
                confidence=0.1,
                explanation="cannot tell",
                needs_better_view=False,
                better_view_instruction=None,
            ),
            "test",
        )
        assert result.state is VerificationState.UNCERTAIN
        assert result.needs_better_view is True
        assert result.better_view_instruction

    def test_unknown_state_rejected(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_verify_payload(_verify_payload(state="MAYBE"), "test")

    def test_missing_explanation_rejected(self):
        with pytest.raises(ProviderInvalidResponse):
            parse_verify_payload(_verify_payload(explanation=""), "test")

    def test_out_of_range_confidence_clamped(self):
        result = parse_verify_payload(
            _verify_payload(confidence=5.0), "test"
        )
        assert result.confidence == 1.0

    def test_evidence_alias_accepted_for_explanation(self):
        result = parse_verify_payload(
            _verify_payload(explanation=None) | {"evidence": "head is flush"}, "test"
        )
        assert result.explanation == "head is flush"


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------
class TestVerifyEndpoint:
    def test_pass_roundtrip(self, patch_chain):
        provider = FakeVerifyProvider(
            _verify_payload(state="PASS", confidence=0.92, explanation="Head flush with bracket.")
        )
        patch_chain["gemini"] = provider
        resp = _post_verify()
        assert resp.status_code == 200
        body = resp.json()
        assert body["state"] == "PASS"
        assert body["explanation"] == "Head flush with bracket."
        assert body["needs_better_view"] is False
        assert body["provider_used"] == "fake-verify"
        assert body["step_number"] == 1
        # The model receives the step context, not a raw diagnosis:
        assert provider.last_request.expected_state == "The screw is visibly tight."
        assert provider.last_request.mode == Mode.VERIFY
        assert provider.last_image.startswith(b"\xff\xd8")  # real preprocessed JPEG

    def test_fail_roundtrip(self, patch_chain):
        patch_chain["gemini"] = FakeVerifyProvider(
            _verify_payload(state="FAIL", confidence=0.83, explanation="Screw still protrudes.")
        )
        resp = _post_verify()
        assert resp.status_code == 200
        body = resp.json()
        assert body["state"] == "FAIL"
        assert body["needs_better_view"] is False

    def test_uncertain_roundtrip_with_view(self, patch_chain):
        patch_chain["gemini"] = FakeVerifyProvider(
            _verify_payload(
                state="UNCERTAIN",
                confidence=0.15,
                explanation="Area worked on is out of frame.",
                needs_better_view=True,
                better_view_instruction="Move closer to the hinge.",
            )
        )
        resp = _post_verify()
        assert resp.status_code == 200
        body = resp.json()
        assert body["state"] == "UNCERTAIN"
        assert body["needs_better_view"] is True
        assert body["better_view_instruction"] == "Move closer to the hinge."

    def test_optional_step_context_forwarded(self, patch_chain):
        provider = FakeVerifyProvider()
        patch_chain["gemini"] = provider
        resp = _post_verify(
            step_action="Turn the screw clockwise until snug.",
            target_component="seat mounting screw",
        )
        assert resp.status_code == 200
        assert provider.last_request.step_action == "Turn the screw clockwise until snug."
        assert provider.last_request.target_component == "seat mounting screw"

    def test_missing_expected_state_is_400(self):
        resp = _post_verify(expected_state="   ")
        assert resp.status_code == 400

    def test_missing_step_number_is_422(self):
        resp = client.post(
            "/api/v1/verify",
            data={"expected_state": "tight"},
            files={"image": ("v.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert resp.status_code == 422

    def test_out_of_range_step_number_is_400(self):
        resp = _post_verify(step_number="99")
        assert resp.status_code == 400

    def test_unusable_image_is_400(self):
        resp = client.post(
            "/api/v1/verify",
            data={"step_number": "1", "expected_state": "tight"},
            files={"image": ("v.jpg", b"notanimage", "image/jpeg")},
        )
        assert resp.status_code == 400

    def test_provider_unavailable_maps_to_503(self, patch_chain):
        down = FakeVerifyProvider(error=ProviderUnavailable("quota"))
        patch_chain["gemini"] = down
        patch_chain["openrouter"] = down
        resp = _post_verify()
        assert resp.status_code == 503
        assert "temporarily unavailable" in resp.json()["detail"]

    def test_malformed_model_output_maps_to_502(self, patch_chain):
        bad = FakeVerifyProvider(error=ProviderInvalidResponse("unparseable"))
        patch_chain["gemini"] = bad
        patch_chain["openrouter"] = bad
        resp = _post_verify()
        assert resp.status_code == 502

    def test_fallback_used_when_primary_down(self, patch_chain):
        patch_chain["gemini"] = FakeVerifyProvider(error=ProviderUnavailable("down"))
        patch_chain["openrouter"] = FakeVerifyProvider(
            _verify_payload(state="PASS", confidence=0.9, explanation="ok")
        )
        resp = _post_verify()
        assert resp.status_code == 200
        assert resp.json()["provider_used"] == "fake-verify"

    def test_verification_is_isolated_from_safety_gate(self, patch_chain):
        """Verification must not re-run the diagnosis safety gate: it judges a
        step, not a hazard (spec §17.7 separation)."""
        provider = FakeVerifyProvider(
            _verify_payload(state="PASS", confidence=0.9, explanation="ok")
        )
        patch_chain["gemini"] = provider
        resp = _post_verify(
            expected_state="Exposed wiring fully enclosed in the junction box.",
        )
        assert resp.status_code == 200
        assert resp.json()["state"] == "PASS"
        assert provider.verify_calls == 1
