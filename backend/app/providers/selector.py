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

# Transient upstream failures (Gemini 503 "high demand", OpenRouter 429
# rate-limit) clear within seconds. Each provider gets a bounded number of
# attempts with a linear backoff before the fallback chain moves on, so a
# demand spike degrades to "slower" instead of "failed".
PROVIDER_ATTEMPTS = 3
PROVIDER_BACKOFF_SECONDS = 1.5


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


def _provider_chain() -> list:
    """Primary provider + optional fallback, in order."""
    settings = get_settings()
    chain = [settings.ai_provider]
    if settings.ai_fallback_provider and settings.ai_fallback_provider != settings.ai_provider:
        chain.append(settings.ai_fallback_provider)
    return chain


def _run_with_fallback(action, chain, prepare, call, describe=lambda _result: ""):
    """Shared bounded-retry fallback runner for every AI action.

    For each provider in the chain: build it, prepare the capability
    (exceptions propagate, matching the old per-endpoint behavior), then call
    it up to PROVIDER_ATTEMPTS times. ProviderUnavailable (transient upstream
    failures) is retried with linear backoff, then falls to the next
    provider. ProviderInvalidResponse is never retried: a second model
    judging the same payload usually repeats the same judgment problem.

    Returns (result, provider_name, duration_ms). Raises the last
    ProviderError when every configured provider is exhausted.
    """
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

        # Capability extraction (e.g. planning support). Raises propagate:
        # a provider that cannot plan is a configuration problem, not a
        # transient one.
        action_fn = prepare(provider)

        for attempt in range(1, PROVIDER_ATTEMPTS + 1):
            start = time.monotonic()
            try:
                logger.info(
                    "provider=%s attempt=%d action=%s", provider.name, attempt, action
                )
                result = call(action_fn)
                duration_ms = int((time.monotonic() - overall_start) * 1000)
                logger.info(
                    "provider=%s status=validated action=%s %s duration_ms=%d total_duration_ms=%d",
                    provider.name,
                    action,
                    describe(result),
                    int((time.monotonic() - start) * 1000),
                    duration_ms,
                )
                return result, provider.name, duration_ms
            except ProviderUnavailable as exc:
                logger.warning(
                    "provider=%s attempt=%d status=unavailable action=%s error=%s",
                    provider.name,
                    attempt,
                    action,
                    exc,
                )
                last_error = exc
                if attempt < PROVIDER_ATTEMPTS:
                    delay = PROVIDER_BACKOFF_SECONDS * attempt
                    logger.info(
                        "provider=%s action=%s retrying in %.1fs",
                        provider.name,
                        action,
                        delay,
                    )
                    time.sleep(delay)
                    continue
                break  # attempts exhausted for this provider
            except ProviderInvalidResponse as exc:
                logger.warning(
                    "provider=%s status=invalid_response action=%s error=%s",
                    provider_name,
                    action,
                    exc,
                )
                last_error = exc
                break  # do not retry an invalid payload against the same model

        if is_last:
            break
        logger.info("action=fallback to=%s", chain[index + 1])

    assert last_error is not None
    raise last_error


def diagnose_with_fallback(
    image_jpeg: bytes,
    mode: Mode,
    user_context: Optional[str] = None,
) -> tuple[DiagnosisResult, str, int]:
    """Runs diagnosis with bounded per-provider retries and fallback.

    Returns (diagnosis, provider_name, duration_ms). Raises the last
    ProviderError if every configured provider is exhausted.
    """
    return _run_with_fallback(
        action="diagnose",
        chain=_provider_chain(),
        prepare=lambda provider: provider,
        call=lambda provider: provider.diagnose(image_jpeg, mode, user_context),
        describe=lambda _result: "",
    )


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
    bounded fallback + retry as diagnosis. Raises ProviderUnavailable if no
    provider supports planning; ProviderInvalidResponse propagates (not
    retried).
    """
    # The diagnosis is passed to the model as compact JSON, it is the
    # validated, safety-gated schema object, never the raw photo.
    diagnosis_json = json.dumps(
        diagnosis.model_dump(mode="json", exclude_none=True),
        ensure_ascii=False,
    )

    def prepare(provider):
        plan_fn, _ = _capabilities(provider)
        return plan_fn

    return _run_with_fallback(
        action="plan",
        chain=_provider_chain(),
        prepare=prepare,
        call=lambda plan_fn: plan_fn(diagnosis_json, user_context),
        describe=lambda plan: f"steps={len(plan.steps)}",
    )


def assembly_with_fallback(
    image_jpeg: bytes,
    user_context: Optional[str] = None,
) -> tuple[AssemblyPlan, str, int]:
    """Generates an assembly plan from a photo of disassembled parts, with
    the same bounded fallback + retry as diagnosis."""

    def prepare(provider):
        _, assembly_fn = _capabilities(provider)
        return assembly_fn

    return _run_with_fallback(
        action="plan_assembly",
        chain=_provider_chain(),
        prepare=prepare,
        call=lambda assembly_fn: assembly_fn(
            user_context=user_context, image_jpeg=image_jpeg
        ),
        describe=lambda assembly: (
            f"parts={len(assembly.parts)} order_confident={assembly.order_confident}"
        ),
    )


def verify_with_fallback(
    request: VerificationRequest,
    image_jpeg: bytes,
) -> tuple[VerificationResult, str, int]:
    """Verifies one step against a fresh capture, with the same bounded
    fallback + retry as diagnosis."""

    def prepare(provider):
        return _verify_capability(provider)

    return _run_with_fallback(
        action="verify",
        chain=_provider_chain(),
        prepare=prepare,
        call=lambda verify_fn: verify_fn(request, image_jpeg),
        describe=lambda result: f"state={result.state.value}",
    )
