"""Gemini vision provider — primary (spec §4).

Uses the REST generateContent endpoint directly (no SDK dependency), sends the
image inline as base64 JPEG, requests JSON-structured output, and normalizes
through the shared parser. Model is configuration-driven with a candidate
list: the first model that answers wins, so a key with different model
enablement or a 503-overloaded model degrades gracefully.

Phase 4 adds repair-plan and assembly generation on the same transport, with
the same candidate-model fallback and strict payload validation.
"""
import base64
import json
import time
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

from ..config import get_settings
from ..prompts import (
    ASSEMBLY_JSON_CONTRACT,
    ASSEMBLY_PROMPT,
    DIAGNOSIS_JSON_CONTRACT,
    DIAGNOSIS_PROMPT,
    GLOBAL_SYSTEM_PROMPT,
    REPAIR_PLAN_JSON_CONTRACT,
    REPAIR_PLAN_PROMPT,
    VERIFICATION_PROMPT,
    VERIFY_JSON_CONTRACT,
)
from ..schemas import (
    AssemblyPlan,
    DiagnosisResult,
    Mode,
    RepairPlan,
    VerificationRequest,
    VerificationResult,
)
from .base import (
    ProviderInvalidResponse,
    ProviderUnavailable,
    extract_json_object,
    parse_assembly_payload,
    parse_diagnosis_payload,
    parse_plan_payload,
    parse_verify_payload,
)

GEMINI_TIMEOUT_SECONDS = 60


class GeminiProvider:
    def __init__(self, api_key: Optional[str], models: Optional[List[str]] = None):
        self.api_key = (api_key or "").strip()
        self.models = models or ["gemini-flash-latest"]
        if not self.api_key:
            raise ProviderUnavailable("GEMINI_API_KEY is not configured")

    @property
    def name(self) -> str:
        return "gemini"

    # ------------------------------------------------------------------
    # Public capabilities
    # ------------------------------------------------------------------
    def diagnose(
        self,
        image_jpeg: bytes,
        mode: Mode,
        user_context: Optional[str] = None,
    ) -> DiagnosisResult:
        prompt = (
            f"{GLOBAL_SYSTEM_PROMPT}\n\n{DIAGNOSIS_PROMPT}\n\n{DIAGNOSIS_JSON_CONTRACT}"
        )
        if user_context:
            prompt += f"\n\nUser context: {user_context}"

        def parse(parsed: Dict[str, Any]) -> DiagnosisResult:
            result = parse_diagnosis_payload(parsed, mode, self.name)
            result.provider_used = self.name
            return result

        return self._generate(
            prompt=prompt,
            image_jpeg=image_jpeg,
            parse=parse,
        )

    def plan(self, diagnosis_payload_json: str, user_context: Optional[str] = None) -> RepairPlan:
        """Generates a structured repair plan from a serialized validated
        diagnosis (already safety-gated by the caller). Text-only request."""
        prompt = (
            f"{GLOBAL_SYSTEM_PROMPT}\n\n{REPAIR_PLAN_PROMPT}\n\n"
            f"{REPAIR_PLAN_JSON_CONTRACT}\n\nValidated diagnosis JSON:\n{diagnosis_payload_json}"
        )
        if user_context:
            prompt += f"\n\nUser context: {user_context}"

        def parse(parsed: Dict[str, Any]) -> RepairPlan:
            plan = parse_plan_payload(parsed, self.name)
            return plan

        return self._generate(prompt=prompt, image_jpeg=None, parse=parse)

    def plan_assembly(self, user_context: Optional[str] = None, image_jpeg: Optional[bytes] = None) -> AssemblyPlan:
        """Generates an assembly plan from a photo of disassembled parts."""
        prompt = (
            f"{GLOBAL_SYSTEM_PROMPT}\n\n{ASSEMBLY_PROMPT}\n\n{ASSEMBLY_JSON_CONTRACT}"
        )
        if user_context:
            prompt += f"\n\nUser context: {user_context}"
        if image_jpeg is None:
            raise ProviderUnavailable("Assembly planning requires an image")

        def parse(parsed: Dict[str, Any]) -> AssemblyPlan:
            return parse_assembly_payload(parsed, self.name)

        return self._generate(prompt=prompt, image_jpeg=image_jpeg, parse=parse)

    def verify(self, request: VerificationRequest, image_jpeg: bytes) -> VerificationResult:
        """Compares a fresh capture against a step's expected state. Separate
        from diagnosis: the model never re-derives an object/issue diagnosis
        here, it only judges expected-vs-observed for THIS step."""
        context_lines = [
            f"Step {request.step_number} of the repair plan.",
            f"Expected state after this step: {request.expected_state}",
        ]
        if request.step_action:
            context_lines.append(f"What the user was asked to do: {request.step_action}")
        if request.target_component:
            context_lines.append(f"Component this step acts on: {request.target_component}")
        context_lines.append(
            "The user reports having performed the step."
            if request.user_confirms_done
            else "The user has not confirmed performing the step."
        )
        prompt = (
            f"{GLOBAL_SYSTEM_PROMPT}\n\n{VERIFICATION_PROMPT}\n\n{VERIFY_JSON_CONTRACT}\n\n"
            + "\n".join(context_lines)
        )

        def parse(parsed: Dict[str, Any]) -> VerificationResult:
            return parse_verify_payload(parsed, self.name)

        return self._generate(prompt=prompt, image_jpeg=image_jpeg, parse=parse)

    # ------------------------------------------------------------------
    # Shared transport
    # ------------------------------------------------------------------
    def _generate(
        self,
        prompt: str,
        image_jpeg: Optional[bytes],
        parse,
    ):
        """One prompt (+ optional inline image) through the candidate model
        list. First model that yields a validated payload wins; transport
        failures and invalid payloads try the next candidate."""
        parts: List[Dict[str, Any]] = [{"text": prompt}]
        if image_jpeg is not None:
            parts.append(
                {
                    "inline_data": {
                        "mime_type": "image/jpeg",
                        "data": base64.b64encode(image_jpeg).decode("ascii"),
                    }
                }
            )
        body = {
            "contents": [{"parts": parts}],
            "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.1,
                "maxOutputTokens": 2048,
            },
        }

        last_error: Optional[Exception] = None
        for model in self.models:
            url = (
                "https://generativelanguage.googleapis.com/v1beta/models/"
                f"{model}:generateContent"
            )
            request = urllib.request.Request(
                url,
                data=json.dumps(body).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "x-goog-api-key": self.api_key,
                },
                method="POST",
            )
            start = time.monotonic()
            try:
                with urllib.request.urlopen(request, timeout=GEMINI_TIMEOUT_SECONDS) as response:
                    payload = json.load(response)
            except urllib.error.HTTPError as exc:
                detail = ""
                try:
                    detail = exc.read().decode("utf-8", "replace")[:200]
                except Exception:
                    pass
                # 4xx with a real Gemini error body is an invalid-usage problem,
                # not a transport failure — surface it and try the next model.
                last_error = ProviderUnavailable(
                    f"Gemini HTTP {exc.code}: {detail or exc.reason}"
                )
                continue
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                last_error = ProviderUnavailable(f"Gemini network failure: {exc}")
                continue

            duration_ms = int((time.monotonic() - start) * 1000)

            try:
                text = payload["candidates"][0]["content"]["parts"][0]["text"]
            except (KeyError, IndexError, TypeError) as exc:
                last_error = ProviderInvalidResponse(
                    f"Gemini response missing candidate text ({exc})"
                )
                continue

            try:
                parsed = extract_json_object(text)
            except ProviderInvalidResponse as exc:
                last_error = exc
                continue

            try:
                return parse(parsed)
            except ProviderInvalidResponse as exc:
                last_error = exc
                continue

        assert last_error is not None
        raise last_error
