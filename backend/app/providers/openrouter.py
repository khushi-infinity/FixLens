"""OpenRouter vision provider, configurable fallback (spec §4).

Speaks the OpenAI-compatible chat/completions protocol with one specifically
configured free vision-capable model (never a random router choice). Conforms
to the same AIProvider interface; the rest of the backend cannot tell which
provider produced a result.

Phase 4 adds repair-plan and assembly generation on the same transport.
"""
import base64
import json
import time
import urllib.error
import urllib.request
from typing import Any, Callable, Dict, List, Optional

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

OPENROUTER_TIMEOUT_SECONDS = 90


class OpenRouterProvider:
    def __init__(self, api_key: Optional[str], model: str, models: Optional[List[str]] = None):
        self.api_key = (api_key or "").strip()
        # Candidate list: first model that answers wins. Free-tier pools are
        # per-model, so a saturated candidate (429) falls through to the next.
        self.models = [m.strip() for m in (models or [model]) if m and m.strip()]
        if not self.api_key:
            raise ProviderUnavailable("OPENROUTER_API_KEY is not configured")
        if not self.models:
            raise ProviderUnavailable("OPENROUTER_MODEL is not configured")

    @property
    def name(self) -> str:
        return "openrouter"

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

        image_url = {
            "type": "image_url",
            "image_url": {
                "url": f"data:image/jpeg;base64,{base64.b64encode(image_jpeg).decode('ascii')}"
            },
        }

        def parse(parsed: Dict[str, Any]) -> DiagnosisResult:
            result = parse_diagnosis_payload(parsed, mode, self.name)
            result.provider_used = self.name
            return result

        return self._generate(
            prompt=prompt,
            image_url=image_url,
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
            return parse_plan_payload(parsed, self.name)

        return self._generate(prompt=prompt, image_url=None, parse=parse)

    def plan_assembly(self, user_context: Optional[str] = None, image_jpeg: Optional[bytes] = None) -> AssemblyPlan:
        """Generates an assembly plan from a photo of disassembled parts."""
        prompt = (
            f"{GLOBAL_SYSTEM_PROMPT}\n\n{ASSEMBLY_PROMPT}\n\n{ASSEMBLY_JSON_CONTRACT}"
        )
        if user_context:
            prompt += f"\n\nUser context: {user_context}"
        if image_jpeg is None:
            raise ProviderUnavailable("Assembly planning requires an image")

        image_url = {
            "type": "image_url",
            "image_url": {
                "url": f"data:image/jpeg;base64,{base64.b64encode(image_jpeg).decode('ascii')}"
            },
        }

        def parse(parsed: Dict[str, Any]) -> AssemblyPlan:
            return parse_assembly_payload(parsed, self.name)

        return self._generate(prompt=prompt, image_url=image_url, parse=parse)

    def verify(self, request: VerificationRequest, image_jpeg: bytes) -> VerificationResult:
        """Compares a fresh capture against a step's expected state. Separate
        from diagnosis: this is expected-vs-observed judgment only, never a
        re-diagnosis."""
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

        image_url = {
            "type": "image_url",
            "image_url": {
                "url": f"data:image/jpeg;base64,{base64.b64encode(image_jpeg).decode('ascii')}"
            },
        }

        def parse(parsed: Dict[str, Any]) -> VerificationResult:
            return parse_verify_payload(parsed, self.name)

        return self._generate(prompt=prompt, image_url=image_url, parse=parse)

    # ------------------------------------------------------------------
    # Shared transport
    # ------------------------------------------------------------------
    def _generate(
        self,
        prompt: str,
        image_url: Optional[Dict[str, Any]],
        parse: Callable[[Dict[str, Any]], Any],
    ):
        """One prompt (+ optional image part) through the candidate model
        list. First model that yields a validated payload wins."""
        last_error: Optional[Exception] = None
        for model in self.models:
            content: List[Dict[str, Any]] = [{"type": "text", "text": prompt}]
            if image_url is not None:
                content.append(image_url)
            body = {
                "model": model,
                "messages": [{"role": "user", "content": content}],
                "max_tokens": 2048,
                "temperature": 0.1,
            }

            request = urllib.request.Request(
                "https://openrouter.ai/api/v1/chat/completions",
                data=json.dumps(body).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {self.api_key}",
                },
                method="POST",
            )
            try:
                with urllib.request.urlopen(
                    request, timeout=OPENROUTER_TIMEOUT_SECONDS
                ) as response:
                    payload = json.load(response)
            except urllib.error.HTTPError as exc:
                detail = ""
                try:
                    detail = exc.read().decode("utf-8", "replace")[:200]
                except Exception:
                    pass
                last_error = ProviderUnavailable(
                    f"OpenRouter HTTP {exc.code}: {detail or exc.reason}"
                )
                continue  # try the next candidate model
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                last_error = ProviderUnavailable(f"OpenRouter network failure: {exc}")
                continue

            try:
                text = payload["choices"][0]["message"]["content"]
            except (KeyError, IndexError, TypeError) as exc:
                last_error = ProviderInvalidResponse(
                    f"OpenRouter response missing message content ({exc})"
                )
                continue

            try:
                parsed = extract_json_object(text)
                return parse(parsed)
            except ProviderInvalidResponse as exc:
                last_error = exc
                continue

        assert last_error is not None
        raise last_error
