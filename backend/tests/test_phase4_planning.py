"""Phase 4 tests: repair planning + assembly (schemas, parsers, safety gate,
endpoints). No real API calls, providers are injected fakes like in
test_diagnose_endpoint.py.

Key invariants under test:
  - Safety happens BEFORE generation: HIGH/better-view/no-issue diagnoses
    never reach the model (fake providers assert they were not called).
  - Blocked responses can never carry steps (schema-enforced).
  - Plans are strict structured objects; malformed model output is rejected.
  - Assembly plans without a supportable order carry NO steps + one view request.
"""
import io
import json

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app
from app.providers.base import (
    ProviderInvalidResponse,
    ProviderUnavailable,
    parse_assembly_payload,
    parse_plan_payload,
)
from app.safety import apply_assembly_safety_policy
from app.schemas import (
    AssemblyPlan,
    AssemblyStatus,
    ComponentItem,
    DiagnosisResult,
    EvidenceKind,
    LikelyCause,
    Mode,
    PlanStatus,
    ProfessionalType,
    RepairPlan,
    RepairStep,
    SafetyDecision,
    SafetyLevel,
    VisualEvidenceItem,
)

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


def _diagnosis(**overrides) -> DiagnosisResult:
    """A valid LOW/GUIDE diagnosis with a real issue, by default."""
    fields = dict(
        object_name="office chair",
        object_category="furniture",
        components=[
            ComponentItem(name="seat", kind=EvidenceKind.OBSERVED, status="loose"),
            ComponentItem(name="central mechanism", kind=EvidenceKind.OBSERVED, status=None),
        ],
        issue_summary="The seat is loose and wobbles on its mounting plate.",
        likely_causes=[LikelyCause(text="Loose mounting screws", confidence=0.7)],
        confidence=0.8,
        confidence_band=__import__("app.schemas", fromlist=["ConfidenceBand"]).ConfidenceBand.HIGH,
        safety_level=SafetyLevel.LOW,
        safety_reason="No sharp edges, electrical, or structural hazards visible.",
        observations=[VisualEvidenceItem(text="Gap between seat and mounting plate", kind=EvidenceKind.OBSERVED)],
        needs_better_view=False,
        better_view_instruction=None,
        professional_type=ProfessionalType.NONE,
        mode=Mode.PHOTO,
        provider_used="gemini",
    )
    fields.update(overrides)
    return DiagnosisResult(**fields)


def _plan_payload(object_name="office chair") -> dict:
    return {
        "object_name": object_name,
        "issue_summary": "The seat is loose on its mounting plate.",
        "steps": [
            {
                "number": 1,
                "title": "Tighten the mounting screws",
                "action": "Turn each mounting screw clockwise.",
                "instruction": "Sit on the seat to hold it square, then tighten each screw a half turn at a time, working in a star pattern.",
                "target_component": "seat mounting screws under the seat plate",
                "tool_known": True,
                "tool": "Phillips screwdriver",
                "tool_note": None,
                "warning": None,
                "expected_state": "Each screw is visibly snug and the seat no longer rocks.",
                "confirmation_required": True,
            },
            {
                "number": 2,
                "title": "Check the seat for movement",
                "action": "Push the seat side to side firmly.",
                "instruction": "Lean on the seat edges and try to rock it. If it still moves, re-tighten any screw that turned.",
                "target_component": "seat plate",
                "tool_known": False,
                "tool": None,
                "tool_note": "Use the appropriate screwdriver for this screw.",
                "warning": None,
                "expected_state": "The seat stays fixed when pushed.",
                "confirmation_required": True,
            },
        ],
        "notes": None,
    }


def _assembly_payload(order_confident=True) -> dict:
    return {
        "object_name": "metal shelving unit",
        "parts": [
            {"name": "vertical frame post", "kind": "OBSERVED", "status": "four visible"},
            {"name": "M6 hex bolts", "kind": "OBSERVED", "status": "eight visible"},
        ],
        "order_confident": order_confident,
        "steps": [
            {
                "number": 1,
                "title": "Stand the two frame posts upright",
                "action": "Place the posts parallel, 60 cm apart.",
                "instruction": "Set the posts on the floor with the bolt holes facing inward.",
                "target_component": "vertical frame posts",
                "tool_known": False,
                "tool": None,
                "tool_note": None,
                "warning": None,
                "expected_state": "Two posts standing parallel with holes aligned inward.",
                "confirmation_required": True,
            }
        ]
        if order_confident
        else [],
        "requested_view": "Show the flat side of the largest frame piece with its bolt holes."
        if not order_confident
        else None,
        "safety_level": "LOW",
        "safety_reason": "Loose parts only; nothing hot, energized, or structural in view.",
    }


# ---------------------------------------------------------------------------
# Fake providers (plan + assembly capable)
# ---------------------------------------------------------------------------
class FakePlanProvider:
    name = "fake-plan"

    def __init__(self, plan_payload=None, assembly_payload=None):
        self.plan_payload = plan_payload or _plan_payload()
        self.assembly_payload = assembly_payload or _assembly_payload()
        self.plan_calls = 0
        self.assembly_calls = 0

    def plan(self, diagnosis_payload_json, user_context=None):
        self.plan_calls += 1
        return parse_plan_payload(self.plan_payload, self.name)

    def plan_assembly(self, user_context=None, image_jpeg=None):
        self.assembly_calls += 1
        return parse_assembly_payload(self.assembly_payload, self.name)

    def diagnose(self, image_jpeg, mode, user_context=None):  # pragma: no cover
        raise ProviderUnavailable("not used in these tests")


class FakeUnavailablePlanProvider(FakePlanProvider):
    name = "fake-plan-down"

    def plan(self, diagnosis_payload_json, user_context=None):
        self.plan_calls += 1
        raise ProviderUnavailable("simulated outage")

    def plan_assembly(self, user_context=None, image_jpeg=None):
        self.assembly_calls += 1
        raise ProviderUnavailable("simulated outage")


class FakeMalformedPlanProvider(FakePlanProvider):
    name = "fake-plan-malformed"

    def plan(self, diagnosis_payload_json, user_context=None):
        self.plan_calls += 1
        raise ProviderInvalidResponse("plan output unparseable")


@pytest.fixture
def patch_chain(monkeypatch):
    state = {"gemini": None, "openrouter": None}

    def fake_build(name):
        return state[name]

    monkeypatch.setattr("app.providers.selector._build_provider", fake_build)
    return state


# ---------------------------------------------------------------------------
# Schema invariants
# ---------------------------------------------------------------------------
class TestRepairStepSchema:
    def test_valid_step_round_trips(self):
        step = RepairStep(
            number=1,
            title="Remove the retaining screw",
            action="Turn the Phillips screw counterclockwise.",
            instruction="Grip the screwdriver firmly and turn counterclockwise.",
            target_component="retaining screw on the central mechanism",
            tool_known=True,
            tool="Phillips screwdriver",
            expected_state="Screw visibly loosened.",
        )
        assert step.number == 1
        assert step.confirmation_required is True

    def test_tool_known_requires_tool(self):
        with pytest.raises(Exception, match="tool is required"):
            RepairStep(
                number=1, title="t", action="a", instruction="i",
                target_component="c", tool_known=True, tool=None,
                expected_state="s",
            )

    def test_tool_unknown_forbids_tool(self):
        with pytest.raises(Exception, match="tool must be null"):
            RepairStep(
                number=1, title="t", action="a", instruction="i",
                target_component="c", tool_known=False, tool="wrench",
                expected_state="s",
            )

    def test_blank_optional_fields_become_none(self):
        step = RepairStep(
            number=1, title="t", action="a", instruction="i",
            target_component="c", tool_known=False, tool=None,
            expected_state="s", warning="   ", tool_note="  ",
        )
        assert step.warning is None
        assert step.tool_note is None


class TestPlanResponseSchema:
    def test_blocked_status_cannot_carry_plan(self):
        from app.schemas import SafetyNotice

        notice = SafetyNotice(
            decision=SafetyDecision.SAFETY_STOP,
            user_message="stop",
            professional_type=ProfessionalType.OTHER,
        )
        plan = RepairPlan(object_name="o", issue_summary="i", steps=[_step(1)])
        with pytest.raises(Exception, match="plan must be null"):
            __import__("app.schemas", fromlist=["PlanResponse"]).PlanResponse(
                status=PlanStatus.BLOCKED_HIGH_RISK, plan=plan, safety=notice,
            )

    def test_plan_ready_requires_plan(self):
        from app.schemas import PlanResponse, SafetyNotice

        notice = SafetyNotice(
            decision=SafetyDecision.GUIDE,
            user_message="ok",
            professional_type=ProfessionalType.NONE,
        )
        with pytest.raises(Exception, match="plan is required"):
            PlanResponse(status=PlanStatus.PLAN_READY, plan=None, safety=notice)


def _step(n: int) -> RepairStep:
    return RepairStep(
        number=n, title=f"step {n}", action="do it", instruction="carefully",
        target_component="target", tool_known=False, expected_state="done",
    )


class TestAssemblyPlanSchema:
    def test_unconfident_order_forbids_steps(self):
        with pytest.raises(Exception, match="steps must be empty"):
            AssemblyPlan(
                object_name="shelf",
                parts=[ComponentItem(name="frame")],
                steps=[_step(1)],
                order_confident=False,
                requested_view="Show the bolt holes.",
                safety_level=SafetyLevel.LOW,
                safety_reason="no hazards",
            )

    def test_unconfident_order_requires_view(self):
        with pytest.raises(Exception, match="requested_view is required"):
            AssemblyPlan(
                object_name="shelf",
                parts=[ComponentItem(name="frame")],
                steps=[],
                order_confident=False,
                requested_view=None,
                safety_level=SafetyLevel.LOW,
                safety_reason="no hazards",
            )

    def test_confident_order_requires_steps(self):
        with pytest.raises(Exception, match="steps are required"):
            AssemblyPlan(
                object_name="shelf",
                parts=[ComponentItem(name="frame")],
                steps=[],
                order_confident=True,
                requested_view=None,
                safety_level=SafetyLevel.LOW,
                safety_reason="no hazards",
            )


# ---------------------------------------------------------------------------
# Parsers
# ---------------------------------------------------------------------------
class TestPlanParser:
    def test_valid_payload_parses_with_positional_numbers(self):
        """Model numbering is untrusted: numbers are positional by construction."""
        payload = _plan_payload()
        payload["steps"][1]["number"] = 99  # must be ignored
        plan = parse_plan_payload(payload, "fake")
        assert isinstance(plan, RepairPlan)
        assert [s.number for s in plan.steps] == [1, 2]
        assert plan.steps[0].tool == "Phillips screwdriver"

    def test_tool_named_without_flag_is_promoted(self):
        payload = _plan_payload()
        payload["steps"][1]["tool_known"] = False
        payload["steps"][1]["tool"] = "wrench"
        plan = parse_plan_payload(payload, "fake")
        assert plan.steps[1].tool_known is True
        assert plan.steps[1].tool == "wrench"

    def test_empty_steps_rejected(self):
        payload = _plan_payload()
        payload["steps"] = []
        with pytest.raises(ProviderInvalidResponse, match="no steps"):
            parse_plan_payload(payload, "fake")

    def test_malformed_step_rejected(self):
        payload = _plan_payload()
        payload["steps"][0] = "just a string"
        with pytest.raises(ProviderInvalidResponse):
            parse_plan_payload(payload, "fake")

    def test_generic_step_aliases_accepted(self):
        payload = _plan_payload()
        del payload["steps"][0]["instruction"]
        del payload["steps"][0]["expected_state"]
        payload["steps"][0]["detailed_instruction"] = "Do the motion."
        payload["steps"][0]["expected_visual_state"] = "It visibly moved."
        plan = parse_plan_payload(payload, "fake")
        assert plan.steps[0].instruction == "Do the motion."
        assert plan.steps[0].expected_state == "It visibly moved."


class TestAssemblyParser:
    def test_confident_order_parses(self):
        assembly = parse_assembly_payload(_assembly_payload(order_confident=True), "fake")
        assert assembly.order_confident is True
        assert len(assembly.steps) == 1
        assert [s.number for s in assembly.steps] == [1]

    def test_unconfident_order_keeps_view(self):
        assembly = parse_assembly_payload(_assembly_payload(order_confident=False), "fake")
        assert assembly.order_confident is False
        assert assembly.steps == []
        assert "bolt holes" in assembly.requested_view

    def test_confident_flag_without_steps_is_downgraded(self):
        payload = _assembly_payload(order_confident=True)
        payload["steps"] = []
        assembly = parse_assembly_payload(payload, "fake")
        assert assembly.order_confident is False  # evidence did not support the flag
        assert assembly.requested_view  # fallback view request supplied

    def test_no_parts_rejected(self):
        payload = _assembly_payload()
        payload["parts"] = []
        with pytest.raises(ProviderInvalidResponse, match="no parts"):
            parse_assembly_payload(payload, "fake")


# ---------------------------------------------------------------------------
# Deterministic gates
# ---------------------------------------------------------------------------
class TestAssemblySafetyGate:
    def test_low_assembly_passes(self):
        assembly = parse_assembly_payload(_assembly_payload(), "fake")
        notice, level = apply_assembly_safety_policy(assembly)
        assert notice.decision == SafetyDecision.GUIDE
        assert level == SafetyLevel.LOW

    def test_high_risk_corpus_escalates_and_would_block(self):
        payload = _assembly_payload()
        payload["safety_level"] = "LOW"
        payload["parts"].append({"name": "exposed wiring harness", "kind": "OBSERVED"})
        assembly = parse_assembly_payload(payload, "fake")
        notice, level = apply_assembly_safety_policy(assembly)
        assert level == SafetyLevel.HIGH  # escalation-only policy held
        assert notice.decision == SafetyDecision.SAFETY_STOP

    def test_medium_assembly_limited_guide(self):
        payload = _assembly_payload()
        payload["safety_level"] = "MEDIUM"
        payload["safety_reason"] = "sharp edge on the cut frame rail"
        assembly = parse_assembly_payload(payload, "fake")
        notice, level = apply_assembly_safety_policy(assembly)
        assert notice.decision == SafetyDecision.LIMITED_GUIDE


# ---------------------------------------------------------------------------
# POST /api/v1/plan
# ---------------------------------------------------------------------------
def _post_plan(diagnosis: DiagnosisResult, context=None):
    return client.post(
        "/api/v1/plan",
        json={"diagnosis": json.loads(diagnosis.model_dump_json()), "context": context},
    )


class TestPlanEndpoint:
    def test_low_diagnosis_plans(self, patch_chain):
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis())
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "PLAN_READY"
        assert body["plan"]["object_name"] == "office chair"
        assert len(body["plan"]["steps"]) == 2
        assert body["plan"]["steps"][0]["tool"] == "Phillips screwdriver"
        assert body["requires_acknowledgement"] is False
        assert provider.plan_calls == 1

    def test_medium_requires_acknowledgement(self, patch_chain):
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis(safety_level=SafetyLevel.MEDIUM,
                                  safety_reason="pinch point between seat plate and post"))
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "PLAN_READY"
        assert body["requires_acknowledgement"] is True
        assert body["safety"]["decision"] == "LIMITED_GUIDE"

    def test_high_risk_never_reaches_the_model(self, patch_chain):
        """The core safety override: HIGH blocks BEFORE generation."""
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis(
            safety_level=SafetyLevel.HIGH,
            safety_reason="exposed mains wiring behind the chair cable",
            professional_type=ProfessionalType.ELECTRICIAN,
        ))
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "BLOCKED_HIGH_RISK"
        assert body["plan"] is None
        assert "electrician" in body["safety"]["user_message"].lower()
        assert provider.plan_calls == 0  # gate ran before any generation

    def test_model_says_low_but_corpus_is_high_still_blocked(self, patch_chain):
        """Deterministic escalation must hold even when the model under-called."""
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis(
            safety_level=SafetyLevel.LOW,
            safety_reason="seems fine",
            observations=[VisualEvidenceItem(text="bare wire sticking out of the base", kind=EvidenceKind.OBSERVED)],
        ))
        assert r.status_code == 200
        assert r.json()["status"] == "BLOCKED_HIGH_RISK"
        assert provider.plan_calls == 0

    def test_better_view_blocked_before_generation(self, patch_chain):
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis(
            needs_better_view=True,
            better_view_instruction="Move the camera underneath the seat and show the mounting plate.",
        ))
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "BLOCKED_BETTER_VIEW"
        assert body["plan"] is None
        assert provider.plan_calls == 0

    def test_no_issue_never_gets_a_plan(self, patch_chain):
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = _post_plan(_diagnosis(
            issue_summary="no visible issue",
            likely_causes=[],
            confidence_band=__import__("app.schemas", fromlist=["ConfidenceBand"]).ConfidenceBand.HIGH,
        ))
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "BLOCKED_NO_ISSUE"
        assert body["plan"] is None
        assert provider.plan_calls == 0

    def test_provider_unavailable_maps_to_503(self, patch_chain):
        patch_chain["gemini"] = FakeUnavailablePlanProvider()
        patch_chain["openrouter"] = FakeUnavailablePlanProvider()
        r = _post_plan(_diagnosis())
        assert r.status_code == 503
        assert "temporarily unavailable" in r.json()["detail"].lower()

    def test_malformed_plan_maps_to_502(self, patch_chain):
        patch_chain["gemini"] = FakeMalformedPlanProvider()
        patch_chain["openrouter"] = FakeMalformedPlanProvider()
        r = _post_plan(_diagnosis())
        assert r.status_code == 502
        assert "couldn't" in r.json()["detail"].lower()

    def test_falls_back_when_primary_unavailable(self, patch_chain):
        patch_chain["gemini"] = FakeUnavailablePlanProvider()
        patch_chain["openrouter"] = FakePlanProvider()
        r = _post_plan(_diagnosis())
        assert r.status_code == 200
        assert r.json()["provider_used"] == "fake-plan"

    def test_invalid_body_rejected(self, patch_chain):
        r = client.post("/api/v1/plan", json={"diagnosis": {"object_name": "x"}})
        assert r.status_code == 422


# ---------------------------------------------------------------------------
# POST /api/v1/assembly
# ---------------------------------------------------------------------------
class TestAssemblyEndpoint:
    def test_ready_plan_with_steps(self, patch_chain):
        provider = FakePlanProvider()
        patch_chain["gemini"] = provider
        r = client.post(
            "/api/v1/assembly",
            files={"image": ("parts.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "READY"
        assert body["assembly_plan"]["object_name"] == "metal shelving unit"
        assert len(body["assembly_plan"]["steps"]) == 1
        assert provider.assembly_calls == 1

    def test_unconfident_order_returns_view_request_not_steps(self, patch_chain):
        provider = FakePlanProvider(assembly_payload=_assembly_payload(order_confident=False))
        patch_chain["gemini"] = provider
        r = client.post(
            "/api/v1/assembly",
            files={"image": ("parts.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "NEEDS_BETTER_VIEW"
        assert body["assembly_plan"]["steps"] == []
        assert body["assembly_plan"]["requested_view"]

    def test_high_risk_parts_discard_plan(self, patch_chain):
        provider = FakePlanProvider(assembly_payload=_assembly_payload())
        # Model under-called risk; corpus escalation must still discard.
        provider.assembly_payload["safety_level"] = "LOW"
        provider.assembly_payload["parts"].append({"name": "exposed wire bundle", "kind": "OBSERVED"})
        patch_chain["gemini"] = provider
        r = client.post(
            "/api/v1/assembly",
            files={"image": ("parts.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "BLOCKED_HIGH_RISK"
        assert body["assembly_plan"] is None  # discarded before leaving backend

    def test_invalid_image_rejected(self, patch_chain):
        patch_chain["gemini"] = FakePlanProvider()
        r = client.post(
            "/api/v1/assembly",
            files={"image": ("parts.txt", b"not an image", "text/plain")},
        )
        assert r.status_code == 400

    def test_provider_unavailable_maps_to_503(self, patch_chain):
        patch_chain["gemini"] = FakeUnavailablePlanProvider()
        patch_chain["openrouter"] = FakeUnavailablePlanProvider()
        r = client.post(
            "/api/v1/assembly",
            files={"image": ("parts.jpg", _jpeg_bytes(), "image/jpeg")},
        )
        assert r.status_code == 503
