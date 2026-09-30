"""FixLens FastAPI backend, Phase 6 (diagnosis + planning + verification).

Endpoints:
  GET  /health             liveness probe
  POST /api/v1/diagnose    image -> vision model -> validated DiagnosisResult
                           + deterministic safety decision
  POST /api/v1/plan        validated diagnosis -> structured repair plan
                           (safety gate runs BEFORE generation)
  POST /api/v1/assembly    parts photo -> structured assembly plan
                           (safety gate runs BEFORE steps are returned)
  POST /api/v1/verify      fresh capture + step expected state ->
                           PASS / FAIL / UNCERTAIN visual verification

Pipeline: validate input -> provider (fallback) -> Pydantic validation ->
safety policy -> normalized response. Uploaded images are processed in
memory only and never stored.
"""
import logging
import time
from typing import Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from .config import get_settings
from .imaging import ImageValidationError, prepare_for_model, validate_upload
from .providers.base import (
    ProviderError,
    ProviderInvalidResponse,
    ProviderUnavailable,
)
from .providers.selector import (
    assembly_with_fallback,
    diagnose_with_fallback,
    plan_with_fallback,
    verify_with_fallback,
)
from .safety import apply_assembly_safety_policy, apply_safety_policy
from .schemas import (
    AssemblyResponse,
    AssemblyStatus,
    DiagnoseResponse,
    Mode,
    PlanRequest,
    PlanResponse,
    PlanStatus,
    SafetyDecision,
    SafetyLevel,
    is_no_visible_issue_text,
    VerificationRequest,
    VerifyResponse,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("fixlens.api")

app = FastAPI(
    title="FixLens API",
    version="0.6.0",
    description="Backend for the FixLens camera-first repair assistant (Phase 6).",
)

# Minimal CORS for local development tooling; not used by the Android app.
try:
    from fastapi.middleware.cors import CORSMiddleware

    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )
except ImportError:  # pragma: no cover
    pass


@app.get("/health")
def health() -> dict:
    """Liveness probe used by the Android app and the development runbook."""
    return {"status": "ok", "version": app.version}


@app.post("/api/v1/diagnose", response_model=DiagnoseResponse)
async def diagnose(
    image: UploadFile = File(...),
    mode: str = Form("PHOTO"),
    context: Optional[str] = Form(None),
) -> DiagnoseResponse:
    """Diagnoses one photo. Explicit user-triggered analysis only, the
    backend never receives camera frames it did not ask for (spec §15)."""
    request_start = time.monotonic()
    logger.info("request=diagnose received content_type=%s", image.content_type)

    # --- Mode validation ---
    mode_upper = (mode or "PHOTO").strip().upper()
    if mode_upper not in (Mode.PHOTO.value, Mode.LIVE.value):
        raise HTTPException(status_code=400, detail="mode must be PHOTO or LIVE")

    # --- Image intake ---
    data = await image.read()
    try:
        validate_upload(data)
        logger.info("request=diagnose image_valid=true bytes=%d", len(data))
        jpeg_bytes, width, height = prepare_for_model(data)
        logger.info(
            "request=diagnose preprocessed width=%d height=%d bytes=%d",
            width,
            height,
            len(jpeg_bytes),
        )
    except ImageValidationError as exc:
        logger.warning("request=diagnose image_valid=false reason=%s", exc.user_message)
        raise HTTPException(status_code=400, detail=exc.user_message) from exc

    # --- AI provider(s) ---
    try:
        diagnosis, provider_name, duration_ms = diagnose_with_fallback(
            jpeg_bytes,
            Mode(mode_upper),
            context,
        )
    except ProviderUnavailable as exc:
        logger.error("request=diagnose status=provider_unavailable error=%s", exc)
        raise HTTPException(
            status_code=503,
            detail=(
                "The AI service is temporarily unavailable or rate-limited. "
                "Please try again in a moment."
            ),
        ) from exc
    except ProviderInvalidResponse as exc:
        logger.error("request=diagnose status=invalid_model_response error=%s", exc)
        raise HTTPException(
            status_code=502,
            detail=(
                "We couldn't analyze this image reliably right now. "
                "Try again or capture the object from a different angle."
            ),
        ) from exc
    except ProviderError as exc:  # unexpected provider-layer failure
        logger.error("request=diagnose status=provider_error error=%s", exc)
        raise HTTPException(status_code=502, detail="AI analysis failed unexpectedly.") from exc

    # --- Deterministic safety gate (spec §8) ---
    safety, final_level = apply_safety_policy(diagnosis)
    diagnosis.safety_level = final_level
    logger.info(
        "request=diagnose status=ok provider=%s safety=%s model_safety=%s duration_ms=%d",
        provider_name,
        safety.decision.value,
        final_level.value,
        int((time.monotonic() - request_start) * 1000),
    )

    return DiagnoseResponse(
        diagnosis=diagnosis,
        safety=safety,
        provider_used=provider_name,
        duration_ms=duration_ms,
    )


@app.post("/api/v1/plan", response_model=PlanResponse)
def plan(request: PlanRequest) -> PlanResponse:
    """Turns a validated Phase 3 diagnosis into a structured repair plan.

    Safety happens BEFORE planning (spec §17.6): HIGH risk never reaches the
    model, no instructions are ever generated, only the professional
    referral. Better-view and no-issue diagnoses are also blocked deterministically.
    """
    request_start = time.monotonic()
    logger.info(
        "request=plan received object=%s safety_level=%s needs_better_view=%s",
        request.diagnosis.object_name,
        request.diagnosis.safety_level.value,
        request.diagnosis.needs_better_view,
    )

    # --- Deterministic gates BEFORE any generation ------------------------
    safety, final_level = apply_safety_policy(request.diagnosis)

    if safety.decision == SafetyDecision.SAFETY_STOP or final_level == SafetyLevel.HIGH:
        logger.info("request=plan status=blocked reason=safety_stop")
        return PlanResponse(
            status=PlanStatus.BLOCKED_HIGH_RISK,
            plan=None,
            safety=safety,
            requires_acknowledgement=False,
            provider_used=None,
            duration_ms=int((time.monotonic() - request_start) * 1000),
        )

    if safety.decision == SafetyDecision.ASK_FOR_VIEW or request.diagnosis.needs_better_view:
        logger.info("request=plan status=blocked reason=needs_better_view")
        return PlanResponse(
            status=PlanStatus.BLOCKED_BETTER_VIEW,
            plan=None,
            safety=safety,
            duration_ms=int((time.monotonic() - request_start) * 1000),
        )

    if is_no_visible_issue_text(request.diagnosis.issue_summary):
        logger.info("request=plan status=blocked reason=no_issue")
        return PlanResponse(
            status=PlanStatus.BLOCKED_NO_ISSUE,
            plan=None,
            safety=safety,
            duration_ms=int((time.monotonic() - request_start) * 1000),
        )

    # --- Repair-plan generation (first AI call of this endpoint) ----------
    try:
        repair_plan, provider_name, duration_ms = plan_with_fallback(
            request.diagnosis,
            request.context,
        )
    except ProviderUnavailable as exc:
        logger.error("request=plan status=provider_unavailable error=%s", exc)
        raise HTTPException(
            status_code=503,
            detail=(
                "The AI service is temporarily unavailable or rate-limited. "
                "Please try again in a moment."
            ),
        ) from exc
    except ProviderInvalidResponse as exc:
        logger.error("request=plan status=invalid_model_response error=%s", exc)
        raise HTTPException(
            status_code=502,
            detail=(
                "We couldn't prepare reliable repair steps right now. "
                "Please try again in a moment."
            ),
        ) from exc
    except ProviderError as exc:
        logger.error("request=plan status=provider_error error=%s", exc)
        raise HTTPException(status_code=502, detail="Repair planning failed unexpectedly.") from exc

    logger.info(
        "request=plan status=ok provider=%s steps=%d safety=%s duration_ms=%d",
        provider_name,
        len(repair_plan.steps),
        final_level.value,
        int((time.monotonic() - request_start) * 1000),
    )

    return PlanResponse(
        status=PlanStatus.PLAN_READY,
        plan=repair_plan,
        safety=safety,
        # MEDIUM risk: the app must show the safety warning and get an
        # explicit acknowledgement BEFORE revealing any step (spec §17.6).
        requires_acknowledgement=final_level == SafetyLevel.MEDIUM,
        provider_used=provider_name,
        duration_ms=max(duration_ms, int((time.monotonic() - request_start) * 1000)),
    )


@app.post("/api/v1/assembly", response_model=AssemblyResponse)
async def assembly_endpoint(
    image: UploadFile = File(...),
    context: Optional[str] = Form(None),
) -> AssemblyResponse:
    """Turns a photo of disassembled parts into a structured assembly plan.

    The model identifies parts and proposes a sequence; if the visual evidence
    does not determine an order (order_confident=false), the response carries
    NO steps and exactly one requested_view. The deterministic safety gate
    runs on the returned plan BEFORE it leaves the backend: a HIGH-risk plan
    is discarded entirely.
    """
    request_start = time.monotonic()
    logger.info("request=assembly received content_type=%s", image.content_type)

    data = await image.read()
    try:
        validate_upload(data)
        logger.info("request=assembly image_valid=true bytes=%d", len(data))
        jpeg_bytes, width, height = prepare_for_model(data)
        logger.info(
            "request=assembly preprocessed width=%d height=%d bytes=%d",
            width,
            height,
            len(jpeg_bytes),
        )
    except ImageValidationError as exc:
        logger.warning("request=assembly image_valid=false reason=%s", exc.user_message)
        raise HTTPException(status_code=400, detail=exc.user_message) from exc

    try:
        assembly_plan, provider_name, duration_ms = assembly_with_fallback(
            jpeg_bytes,
            context,
        )
    except ProviderUnavailable as exc:
        logger.error("request=assembly status=provider_unavailable error=%s", exc)
        raise HTTPException(
            status_code=503,
            detail=(
                "The AI service is temporarily unavailable or rate-limited. "
                "Please try again in a moment."
            ),
        ) from exc
    except ProviderInvalidResponse as exc:
        logger.error("request=assembly status=invalid_model_response error=%s", exc)
        raise HTTPException(
            status_code=502,
            detail=(
                "We couldn't identify these parts reliably right now. "
                "Try again or capture them from a different angle."
            ),
        ) from exc
    except ProviderError as exc:
        logger.error("request=assembly status=provider_error error=%s", exc)
        raise HTTPException(status_code=502, detail="Assembly planning failed unexpectedly.") from exc

    # --- Deterministic safety gate BEFORE the plan is returned ------------
    safety, final_level = apply_assembly_safety_policy(assembly_plan)
    if safety.decision == SafetyDecision.SAFETY_STOP or final_level == SafetyLevel.HIGH:
        logger.info("request=assembly status=blocked reason=safety_stop")
        return AssemblyResponse(
            status=AssemblyStatus.BLOCKED_HIGH_RISK,
            assembly_plan=None,  # discarded, no path can render its steps
            safety=safety,
            provider_used=provider_name,
            duration_ms=int((time.monotonic() - request_start) * 1000),
        )

    needs_view = not assembly_plan.order_confident
    if needs_view:
        logger.info("request=assembly status=needs_better_view")
    else:
        logger.info(
            "request=assembly status=ok provider=%s parts=%d steps=%d safety=%s duration_ms=%d",
            provider_name,
            len(assembly_plan.parts),
            len(assembly_plan.steps),
            final_level.value,
            int((time.monotonic() - request_start) * 1000),
        )

    return AssemblyResponse(
        status=AssemblyStatus.NEEDS_BETTER_VIEW if needs_view else AssemblyStatus.READY,
        assembly_plan=assembly_plan,
        safety=safety,
        provider_used=provider_name,
        duration_ms=max(duration_ms, int((time.monotonic() - request_start) * 1000)),
    )


@app.post("/api/v1/verify", response_model=VerifyResponse)
async def verify_endpoint(
    image: UploadFile = File(...),
    step_number: int = Form(...),
    expected_state: str = Form(...),
    step_action: Optional[str] = Form(None),
    target_component: Optional[str] = Form(None),
    user_confirms_done: bool = Form(True),
) -> VerifyResponse:
    """Verifies one repair step against a fresh capture.

    Separation of concerns (spec §17.7): verification is NOT a re-diagnosis,
    the model only judges the supplied expected state against the current
    image. The call is explicitly user-triggered (one frame per request, the
    client never streams). UNCERTAIN is the honest outcome whenever the
    capture does not clearly settle the comparison, and it always carries
    exactly one better-view instruction; PASS is never guessed without
    visual evidence.
    """
    request_start = time.monotonic()
    logger.info(
        "request=verify received step=%d content_type=%s",
        step_number,
        image.content_type,
    )

    # --- Request validation ------------------------------------------------
    expected_state = (expected_state or "").strip()
    if not expected_state:
        raise HTTPException(status_code=400, detail="expected_state is required")
    if not 1 <= step_number <= 50:
        raise HTTPException(status_code=400, detail="step_number must be between 1 and 50")
    step_action = (step_action or "").strip() or None
    target_component = (target_component or "").strip() or None

    # --- Image intake: same quality gate as diagnosis ----------------------
    data = await image.read()
    try:
        validate_upload(data)
        logger.info("request=verify image_valid=true bytes=%d", len(data))
        jpeg_bytes, width, height = prepare_for_model(data)
        logger.info(
            "request=verify preprocessed width=%d height=%d bytes=%d",
            width,
            height,
            len(jpeg_bytes),
        )
    except ImageValidationError as exc:
        logger.warning("request=verify image_valid=false reason=%s", exc.user_message)
        raise HTTPException(status_code=400, detail=exc.user_message) from exc

    verify_request = VerificationRequest(
        step_number=step_number,
        expected_state=expected_state,
        step_action=step_action,
        target_component=target_component,
        user_confirms_done=user_confirms_done,
        current_image="<redacted-in-backend>",
        mode=Mode.VERIFY,
    )

    # --- AI provider(s) -----------------------------------------------------
    try:
        result, provider_name, duration_ms = verify_with_fallback(
            verify_request,
            jpeg_bytes,
        )
    except ProviderUnavailable as exc:
        logger.error("request=verify status=provider_unavailable error=%s", exc)
        raise HTTPException(
            status_code=503,
            detail=(
                "The AI service is temporarily unavailable or rate-limited. "
                "Please try again in a moment."
            ),
        ) from exc
    except ProviderInvalidResponse as exc:
        logger.error("request=verify status=invalid_model_response error=%s", exc)
        raise HTTPException(
            status_code=502,
            detail=(
                "We couldn't verify this step reliably right now. "
                "Try again with a clearer view of the area you worked on."
            ),
        ) from exc
    except ProviderError as exc:  # unexpected provider-layer failure
        logger.error("request=verify status=provider_error error=%s", exc)
        raise HTTPException(status_code=502, detail="Verification failed unexpectedly.") from exc

    logger.info(
        "request=verify status=ok provider=%s state=%s needs_view=%s duration_ms=%d",
        provider_name,
        result.state.value,
        result.needs_better_view,
        int((time.monotonic() - request_start) * 1000),
    )

    return VerifyResponse(
        step_number=verify_request.step_number,
        state=result.state,
        confidence=result.confidence,
        explanation=result.explanation,
        needs_better_view=result.needs_better_view,
        better_view_instruction=result.better_view_instruction,
        provider_used=provider_name,
        duration_ms=max(duration_ms, int((time.monotonic() - request_start) * 1000)),
    )
