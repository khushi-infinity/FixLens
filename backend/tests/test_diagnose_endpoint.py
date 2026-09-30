"""Endpoint tests with injected fake providers (no real API calls).

Covers: image validation (empty/unsupported/oversized), provider success,
unavailable->fallback, malformed provider payload -> 502, mode validation.
"""
import io
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.providers.base import ProviderInvalidResponse, ProviderUnavailable
from app.schemas import Mode, SafetyDecision, SafetyLevel

client = TestClient(app)


def _jpeg_bytes(color=(120, 90, 60), size=(640, 480), fmt="JPEG", noise=True) -> bytes:
    """Creates a synthetic photo. Noise is added by default so the image
    passes the Phase 3 quality gate (uniform flat frames are rejected as
    lens-blocked/unusable before reaching any provider)."""
    img = Image.new("RGB", size, color)
    if noise:
        px = img.load()
        for y in range(0, size[1], 3):
            for x in range(0, size[0], 3):
                v = (x * 7 + y * 13 + color[0]) % 256
                px[x, y] = (v, v, v)
    buf = io.BytesIO()
    img.save(buf, format=fmt)
    return buf.getvalue()


def _payload(provider_name: str):
    return {
        "object_name": "test object",
        "object_category": "mechanical",
        "issue_summary": "A visible scratch across the surface.",
        "likely_causes": [{"text": "Impact", "confidence": 0.5}],
        "confidence": 0.7,
        "safety_level": "LOW",
        "safety_reason": "No hazards observed.",
        "visual_evidence": [{"text": "Scratch near the edge", "kind": "OBSERVED"}],
        "needs_better_view": False,
        "better_view_instruction": None,
        "professional_type_if_needed": "NONE",
    }


class FakeOKProvider:
    name = "fake-ok"

    def __init__(self, payload=None):
        self.payload = payload or _payload(self.name)

    def diagnose(self, image_jpeg, mode, user_context=None):
        from app.providers.base import parse_diagnosis_payload

        result = parse_diagnosis_payload(self.payload, mode, self.name)
        result.provider_used = self.name
        return result


class FakeUnavailableProvider:
    name = "fake-down"

    def diagnose(self, image_jpeg, mode, user_context=None):
        raise ProviderUnavailable("simulated outage")


class FakeMalformedProvider:
    name = "fake-malformed"

    def diagnose(self, image_jpeg, mode, user_context=None):
        raise ProviderInvalidResponse("model returned unparseable text")


# The selector resolves providers via _build_provider; tests patch it
# directly to inject fakes — no real API call ever happens here.
@pytest.fixture
def patch_chain(monkeypatch):
    state = {"gemini": None, "openrouter": None}

    def fake_build(name):
        return state[name]

    monkeypatch.setattr("app.providers.selector._build_provider", fake_build)
    return state


class TestImageValidation:
    def test_empty_upload_rejected(self):
        r = client.post(
            "/api/v1/diagnose", files={"image": ("x.jpg", b"", "image/jpeg")}
        )
        assert r.status_code == 400
        assert "empty" in r.json()["detail"].lower()

    def test_unsupported_format_rejected(self):
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.txt", b"not an image at all", "text/plain")},
        )
        assert r.status_code == 400
        assert "unsupported" in r.json()["detail"].lower()

    def test_oversized_upload_rejected(self):
        big = b"\xff\xd8\xff" + b"\x00" * (10 * 1024 * 1024 + 1)
        r = client.post(
            "/api/v1/diagnose", files={"image": ("big.jpg", big, "image/jpeg")}
        )
        assert r.status_code == 400
        assert "too large" in r.json()["detail"].lower()

    def test_flat_unusable_image_rejected(self):
        """Phase 3 quality gate: a lens-blocked/uniform frame is rejected
        before any provider call."""
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("flat.jpg", _jpeg_bytes(noise=False), "image/jpeg")},
        )
        assert r.status_code == 400
        assert "no visible detail" in r.json()["detail"].lower()

    def test_invalid_mode_rejected(self, patch_chain):
        patch_chain["gemini"] = FakeOKProvider()
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
            data={"mode": "TELEPORT"},
        )
        assert r.status_code == 400


class TestDiagnoseSuccess:
    def test_success_returns_normalized_diagnosis(self, patch_chain):
        patch_chain["gemini"] = FakeOKProvider()
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
            data={"mode": "PHOTO"},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["provider_used"] == "fake-ok"
        assert body["diagnosis"]["object_name"] == "test object"
        assert body["safety"]["decision"] == "GUIDE"
        assert body["diagnosis"]["mode"] == "PHOTO"

    def test_high_risk_becomes_safety_stop(self, patch_chain):
        high = _payload("fake-ok")
        high.update(
            {
                "issue_summary": "Exposed mains wiring hanging from the wall.",
                "safety_level": "HIGH",
                "professional_type_if_needed": "ELECTRICIAN",
            }
        )
        patch_chain["gemini"] = FakeOKProvider(payload=high)
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["safety"]["decision"] == "SAFETY_STOP"
        assert "electrician" in body["safety"]["user_message"].lower()

    def test_png_accepted_and_preprocessed(self, patch_chain):
        patch_chain["gemini"] = FakeOKProvider()
        big_png = _jpeg_bytes(size=(4000, 3000), fmt="PNG")
        assert len(big_png) < 10 * 1024 * 1024
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.png", big_png, "image/png")},
        )
        assert r.status_code == 200


class TestFallback:
    def test_unavailable_primary_falls_back(self, patch_chain):
        patch_chain["gemini"] = FakeUnavailableProvider()
        patch_chain["openrouter"] = FakeOKProvider()
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 200
        assert r.json()["provider_used"] == "fake-ok"

    def test_all_unavailable_maps_to_503(self, patch_chain):
        patch_chain["gemini"] = FakeUnavailableProvider()
        patch_chain["openrouter"] = FakeUnavailableProvider()
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 503
        assert "temporarily unavailable" in r.json()["detail"].lower()

    def test_malformed_maps_to_502(self, patch_chain):
        patch_chain["gemini"] = FakeMalformedProvider()
        patch_chain["openrouter"] = FakeMalformedProvider()
        r = client.post(
            "/api/v1/diagnose",
            files={"image": ("x.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 502
        assert "couldn't analyze" in r.json()["detail"].lower()
