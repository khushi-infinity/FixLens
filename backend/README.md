# FixLens Backend

FastAPI service for the FixLens camera-first repair assistant.

## Endpoints

- `GET /health`, liveness probe consumed by the Android app
- `POST /api/v1/diagnose`, image (+ optional user context) → vision model →
  validated, safety-gated diagnosis
- `POST /api/v1/plan`, validated diagnosis → deterministic safety gate →
  repair plan (HIGH risk is blocked BEFORE any model call)
- `POST /api/v1/assembly`, parts photo → assembly plan or an explicit
  better-view request (order is never guessed)
- `POST /api/v1/verify`, capture + step context → PASS / FAIL / UNCERTAIN
  with visual evidence (verification is isolated from diagnosis)

Pipeline for every endpoint: image validation → resize/compress → provider
selector (Gemini primary, OpenRouter fallback, configuration-driven) →
strict Pydantic validation → deterministic safety policy where applicable.
Uploaded images are processed in memory and never persisted.

## Configuration

Copy `.env.example` to `.env` and fill in the keys. `.env` holds real secrets
and is git-ignored; it must never be committed. API keys always stay
server-side, they are never embedded in the Android app.

Key variables: `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `AI_PROVIDER`,
`AI_FALLBACK_PROVIDER`, `GEMINI_MODELS`, `OPENROUTER_MODEL(S)`,
`DIAGNOSE_TIMEOUT_SECONDS` (see `.env.example` for details).

## Run

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
# health: {"status":"ok","version":"0.6.0"}
```

## Tests

```bash
python -m pytest tests/ -q
```

109 offline tests: schema invariants, JSON recovery, deterministic safety
policy and gates-before-generation, image intake, provider parsers, and
endpoint behavior with injected fake providers (no API quota consumed).
