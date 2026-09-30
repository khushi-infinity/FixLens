"""FixLens backend configuration.

Everything is environment-driven (backend/.env or process env), no provider
URLs, IPs, model IDs, or secrets are hardcoded at call sites. Phase 2 adds
AI provider selection; none of these values are required for GET /health.
"""
import os
from functools import lru_cache
from typing import List, Optional

try:  # python-dotenv is optional; process env wins if both exist
    from dotenv import load_dotenv

    _DOTENV_AVAILABLE = True
except ImportError:  # pragma: no cover
    _DOTENV_AVAILABLE = False


def _load_env_file() -> None:
    if not _DOTENV_AVAILABLE:
        return
    from pathlib import Path

    env_path = Path(__file__).resolve().parent.parent / ".env"
    if env_path.exists():
        load_dotenv(env_path, override=False)


_load_env_file()


def _split_csv(raw: Optional[str], default: List[str]) -> List[str]:
    if not raw:
        return list(default)
    items = [item.strip() for item in raw.split(",") if item.strip()]
    return items or list(default)


@lru_cache
def get_settings() -> "Settings":
    return Settings()


class Settings:
    def __init__(self) -> None:
        # --- Secrets (server-side only, never sent to Android) ---
        self.gemini_api_key: Optional[str] = os.environ.get("GEMINI_API_KEY")
        self.openrouter_api_key: Optional[str] = os.environ.get("OPENROUTER_API_KEY")

        # --- Provider selection (spec: AI_PROVIDER / AI_FALLBACK_PROVIDER) ---
        self.ai_provider: str = (os.environ.get("AI_PROVIDER") or "gemini").strip().lower()
        fallback = (os.environ.get("AI_FALLBACK_PROVIDER") or "").strip().lower()
        self.ai_fallback_provider: Optional[str] = fallback or None

        # --- Model configuration ---
        # Candidate list: first model that answers wins. gemini-flash-latest
        # is the verified working alias for this key (gemini-3.8/3.5-flash
        # returned 503 overload, gemini-2.5* returned 404 during bring-up).
        self.gemini_models: List[str] = _split_csv(
            os.environ.get("GEMINI_MODELS"),
            ["gemini-flash-latest", "gemini-3.8-flash", "gemini-3.5-flash"],
        )
        # One specific free vision-capable model (spec §4: no random router).
        # Candidates are tried in order, free-tier pools are per-model, so a
        # saturated candidate (429) falls through to the next.
        self.openrouter_model: str = (
            os.environ.get("OPENROUTER_MODEL") or "qwen/qwen3.8-27b:free"
        ).strip()
        self.openrouter_models: List[str] = _split_csv(
            os.environ.get("OPENROUTER_MODELS"),
            [self.openrouter_model, "google/gemma-4-31b-it:free", "google/gemma-4-26b-a4b-it:free"],
        )

        # --- Request limits ---
        self.diagnose_timeout_seconds: int = int(os.environ.get("DIAGNOSE_TIMEOUT_SECONDS", "75"))
