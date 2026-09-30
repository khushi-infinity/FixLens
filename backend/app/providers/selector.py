"""Provider selection + bounded fallback (spec §4).

Primary provider from AI_PROVIDER, optional fallback from
AI_FALLBACK_PROVIDER. Selection is configuration-driven, no call site names
a provider. Fallback triggers only on ProviderUnavailable (timeout, 5xx,
quota, network); invalid model payloads are NOT retried on the fallback
because a second model seeing the same image usually produces the same
schema problem, surfaced as a controlled error instead.
"""
import json
import logging
import time
from typing import Any, Optional

from ..config import get_settings
from ..schemas import (
    AssemblyPlan,
    DiagnosisResult,
    Mode,
    RepairPlan,
    VerificationRequest,
    VerificationResult,
)
from .base import (
    AIProvider,
    ProviderError,
    ProviderInvalidResponse,
    ProviderUnavailable,
)
from .gemini import GeminiProvider
from .openrouter import OpenRouterProvider

logger = logging.getLogger("fixlens.providers")


def _build_provider(name: str) -> AIProvider:
    settings = get_settings()
    if name == "gemini":
        return GeminiProvider(
            api_key=settings.gemini_api_key,
            models=settings.gemini_models,
        )
    if name == "openrouter":
        return OpenRouterProvider(
            api_key=settings.openrouter_api_key,
            model=settings.openrouter_model,
            models=settings.openrouter_models,
        )
    raise ProviderUnavailable(f"Unknown provider configured: {name}")


def diagnose_with_fallback(
    image_jpeg: bytes,
    mode: Mode,
    user_context: Optional[str] = None,
) -> tuple[DiagnosisResult, str, int]:
    """Runs diagnosis on the primary provider, falling back once.

    Returns (diagnosis, provider_name, duration_ms). Raises the last
    ProviderError if every configured provider fails.
    """
    settings = get_settings()
    chain = [settings.ai_provider]
    if settings.ai_fallback_provider and settings.ai_fallback_provider != settings.ai_provider:
        chain.append(settings.ai_fallback_provider)

    overall_start = time.monotonic()
    last_error: Optional[ProviderError] = None

    for index, provider_name in enumerate(chain):
        is_last = index == len(chain) - 1
        try:
            provider = _build_provider(provider_name)
        except ProviderError as exc:
            logger.warning("provider=%s not buildable: %s", provider_name, exc)
            last_error = exc
            continue

        start = time.monotonic()
        try:
            logger.info("provider=%s attempt=1 action=diagnose", provider.name)
            diagnosis = provider.diagnose(image_jpeg, mode, user_context)
            duration_ms = int((time.monotonic() - overall_start) * 1000)
            logger.info(
                "provider=%s status=validated duration_ms=%d total_duration_ms=%d",
                provider.name,
                int((time.monotonic() - start) * 1000),
                duration_ms,
            )
            return diagnosis, provider.name, duration_ms
        except ProviderUnavailable as exc:
            logger.warning("provider=%s status=unavailable error=%s", provider_name, exc)
            last_error = exc
            if is_last:
                break
            logger.info("action=fallback to=%s", chain[index + 1])
            continue
        except ProviderInvalidResponse as exc:
            logger.warning("provider=%s status=invalid_response error=%s", provider_name, exc)
            last_error = exc
            break

    assert last_error is not None
    raise last_error


def _capabilities(provider: AIProvider) -> tuple:
    """Capabilities each provider must expose for Phase 4 planning."""
    plan = getattr(provider, "plan", None)
    assembly = getattr(provider, "plan_assembly", None)
    if not callable(plan) or not callable(assembly):
        raise ProviderUnavailable(
            f"Provider {provider.name} does not support repair planning"
        )
    return plan, assembly


def _verify_capability(provider: AIProvider):
    verify = getattr(provider, "verify", None)
    if not callable(verify):
        raise ProviderUnavailable(
            f"Provider {provider.name} does not support verification"
        )
    return verify


def plan_with_fallback(
    diagnosis: DiagnosisResult,
    user_context: Optional[str] = None,
) -> tuple[RepairPlan, str, int]:
    """Generates a repair plan from a validated diagnosis, with the same
    bounded fallback as diagnosis. Raises ProviderUnavailable if no provider
    supports planning; ProviderInvalidResponse propagates (not retried).
    """
    settings = get_settings()
    chain = [settings.ai_provider]
    if settings.ai_fallback_provider and settings.ai_fallback_provider != settings.ai_provider:
        chain.append(settings.ai_fallback_provider)

    # The diagnosis is passed to the model as compact JSON, it is the
    # validated, safety-gated schema object, never the raw photo.
    diagnosis_json = json.dumps(
        diagnosis.model_dump(mode="json", exclude_none=True),
        ensure_ascii=False,
    )

    overall_start = time.monotonic()
    last_error: Optional[ProviderError] = None

    for index, provider_name in enumerate(chain):
        is_last = index == len(chain) - 1
        try:
            provider = _build_provider(provider_name)
        except ProviderError as exc:
            logger.warning("provider=%s not buildable: %s", provider_name, exc)
            last_error = exc
            continue

        plan_fn, _ = _capabilities(provider)
        start = time.monotonic()
        try:
            logger.info("provider=%s attempt=1 action=plan", provider.name)
            plan = plan_fn(diagnosis_json, user_context)
            duration_ms = int((time.monotonic() - overall_start) * 1000)
            logger.info(
                "provider=%s status=plan_validated steps=%d duration_ms=%d total_duration_ms=%d",
                provider.name,
                len(plan.steps),
                int((time.monotonic() - start) * 1000),
                duration_ms,
            )
            return plan, provider.name, duration_ms
        except ProviderUnavailable as exc:
            logger.warning("provider=%s status=unavailable action=plan error=%s", provider_name, exc)
            last_error = exc
            if is_last:
                break
            logger.info("action=fallback to=%s", chain[index + 1])
            continue
        except ProviderInvalidResponse as exc:
            logger.warning("provider=%s status=invalid_response action=plan error=%s", provider_name, exc)
            last_error = exc
            break

    assert last_error is not None
    raise last_error


def assembly_with_fallback(
    image_jpeg: bytes,
    user_context: Optional[str] = None,
) -> tuple[AssemblyPlan, str, int]:
    """Generates an assembly plan from a photo of disassembled parts, with
    the same bounded fallback as diagnosis."""
    settings = get_settings()
    chain = [settings.ai_provider]
    if settings.ai_fallback_provider and settings.ai_fallback_provider != settings.ai_provider:
        chain.append(settings.ai_fallback_provider)

    overall_start = time.monotonic()
    last_error: Optional[ProviderError] = None

    for index, provider_name in enumerate(chain):
        is_last = index == len(chain) - 1
        try:
            provider = _build_provider(provider_name)
        except ProviderError as exc:
            logger.warning("provider=%s not buildable: %s", provider_name, exc)
            last_error = exc
            continue

        _, assembly_fn = _capabilities(provider)
        start = time.monotonic()
        try:
            logger.info("provider=%s attempt=1 action=plan_assembly", provider.name)
            assembly = assembly_fn(user_context=user_context, image_jpeg=image_jpeg)
            duration_ms = int((time.monotonic() - overall_start) * 1000)
            logger.info(
                "provider=%s status=assembly_validated parts=%d order_confident=%s duration_ms=%d total_duration_ms=%d",
                provider.name,
                len(assembly.parts),
                assembly.order_confident,
                int((time.monotonic() - start) * 1000),
                duration_ms,
            )
            return assembly, provider.name, duration_ms
        except ProviderUnavailable as exc:
            logger.warning("provider=%s status=unavailable action=plan_assembly error=%s", provider_name, exc)
            last_error = exc
            if is_last:
                break
            logger.info("action=fallback to=%s", chain[index + 1])
            continue
        except ProviderInvalidResponse as exc:
            logger.warning("provider=%s status=invalid_response action=plan_assembly error=%s", provider_name, exc)
            last_error = exc
            break

    assert last_error is not None
    raise last_error


def verify_with_fallback(
    request: VerificationRequest,
    image_jpeg: bytes,
) -> tuple[VerificationResult, str, int]:
    """Verifies one step against a fresh capture, with the same bounded
    fallback as diagnosis: ProviderUnavailable retries the next configured
    provider once; ProviderInvalidResponse does not (a second model judging
    the same image usually repeats the same judgment problem)."""
    settings = get_settings()
    chain = [settings.ai_provider]
    if settings.ai_fallback_provider and settings.ai_fallback_provider != settings.ai_provider:
        chain.append(settings.ai_fallback_provider)

    overall_start = time.monotonic()
    last_error: Optional[ProviderError] = None

    for index, provider_name in enumerate(chain):
        is_last = index == len(chain) - 1
        try:
            provider = _build_provider(provider_name)
        except ProviderError as exc:
            logger.warning("provider=%s not buildable: %s", provider_name, exc)
            last_error = exc
            continue

        verify_fn = _verify_capability(provider)
        start = time.monotonic()
        try:
            logger.info("provider=%s attempt=1 action=verify step=%d", provider.name, request.step_number)
            result = verify_fn(request, image_jpeg)
            duration_ms = int((time.monotonic() - overall_start) * 1000)
            logger.info(
                "provider=%s status=verify_validated state=%s duration_ms=%d total_duration_ms=%d",
                provider.name,
                result.state.value,
                int((time.monotonic() - start) * 1000),
                duration_ms,
            )
            return result, provider.name, duration_ms
        except ProviderUnavailable as exc:
            logger.warning("provider=%s status=unavailable action=verify error=%s", provider_name, exc)
            last_error = exc
            if is_last:
                break
            logger.info("action=fallback to=%s", chain[index + 1])
            continue
        except ProviderInvalidResponse as exc:
            logger.warning("provider=%s status=invalid_response action=verify error=%s", provider_name, exc)
            last_error = exc
            break

    assert last_error is not None
    raise last_error
