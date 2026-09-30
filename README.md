# FixLens

FixLens is a mobile AI technician. Point your phone's camera at something
broken, stuck, or difficult to assemble: FixLens identifies the object and the
visible issue, assesses safety, guides the repair step by step, visually
targets the component, and verifies your work with a follow-up scan.

> **Status: Phase 8 (final polish) — COMPLETE · app v0.8.0-phase8 · backend v0.6.0**
>
> **Visual identity:** an illustrated workshop-journal look — warm paper background, charcoal ink, terracotta and sage watercolor washes, serif editorial headings, and sketchy hand-drawn target overlays on the camera view.
> The full camera-first loop works end to end: a photo captured in the app
> travels to the FastAPI backend, through the Gemini/OpenRouter vision
> providers, and back as a validated, safety-gated diagnosis (**I SEE** →
> **POSSIBLE ISSUE** → **WHAT I FOUND** → **CONFIDENCE** → **SAFETY**).
> **Start Fix** turns it into a structured, step-by-step guided repair with
> **Show Me** visual targeting and **camera verification of every step**
> (PASS / INCOMPLETE / UNCERTAIN — never a success claim without visual
> evidence). HIGH risk is blocked before any generation; MEDIUM requires an
> explicit safety acknowledgement. **RevenueCat monetization** gates scans
> (3 free/month), guided repair, and assembly behind the `fixlens_pro`
> entitlement, subscriptions, or one-time repair packs — real purchase state
> only, with Restore. **Demo Mode** offers three deterministic scripted
> journeys (stuck chair, assembly, wiring safety stop) on the real camera,
> permanently badged "DEMO MODE — scripted result, not live AI", for a
> network-risk-free product demo. Camera shutters, progress, and state
> transitions are polished and animated; slow free-tier AI is communicated
> honestly with retry affordances everywhere.

## The core loop (spec)

```
OBSERVE -> UNDERSTAND -> SAFETY DECISION -> INSTRUCT -> USER ACTS -> VERIFY -> NEXT STEP
```

The camera is the primary interface — FixLens is deliberately not a chatbot.
Phases 2–4 implement OBSERVE → UNDERSTAND → SAFETY → INSTRUCT → USER ACTS →
NEXT STEP. VERIFY is Phase 5: the repair engine already exposes the seam
(`RepairStepState`, `VerificationResult`) and the step flow stops at an
honest "All guided steps completed." — it never claims the repair was verified.

## Architecture

```
Android (Kotlin, Jetpack Compose, Material 3, CameraX)
   │  capture / review / analyze (user-triggered only)
   │  POST /api/v1/diagnose   (multipart JPEG)
   │  POST /api/v1/plan       (validated diagnosis JSON → repair plan)
   │  POST /api/v1/assembly   (parts photo → assembly plan)
   │  POST /api/v1/verify     (fresh capture + step expected state →
   │                           PASS / FAIL / UNCERTAIN verification)
   ▼
FastAPI backend (Python)
   ├─ image validation + quality gate (dark/blank/too-small rejected)
   │   + resize/compress (Pillow)
   ├─ provider selector: Gemini ──unavailable──▶ OpenRouter (configurable)
   ├─ strict Pydantic validation (malformed model output can never pass)
   ├─ deterministic safety policy — runs BEFORE plan generation:
   │    HIGH → BLOCKED_HIGH_RISK, no instructions ever generated;
   │    MEDIUM → plan + requires_acknowledgement; better-view/no-issue blocked
   └─ structured logging (no keys, no images)
   ▼
Android renders the diagnosis layout (I SEE / POSSIBLE ISSUE / WHAT I FOUND /
CONFIDENCE / SAFETY · better-view request · safety stop), then on Start Fix
the guided repair screen: STEP x OF y · title · action · DO THIS · TOOL ·
CAREFUL · WHAT YOU SHOULD SEE AFTER · [I've Done This] [Show Me] [Why?]
[I can't do this] — driven by a pure-Kotlin repair state machine
(IDLE → PLAN_READY → … → REPAIR_COMPLETE) with no AI coupling.
```

- **Provider abstraction** (`backend/app/providers/`): `AIProvider` interface
  with `GeminiProvider` and `OpenRouterProvider`. Selection is configuration
  only (`AI_PROVIDER`, `AI_FALLBACK_PROVIDER`); model IDs are configurable
  candidate lists (first model that answers wins).
- **Safety policy** (`backend/app/safety.py`): deterministic keyword gate that
  can escalate the model's assessment; HIGH risk always produces a
  SAFETY_STOP and discards actionable instructions.
- **Secrets stay server-side.** AI keys live only in `backend/.env`
  (git-ignored). The Android app never sees a provider key.

## Repository layout

```
/android    Android app (Kotlin + Jetpack Compose + Material 3 + CameraX)
/backend    FastAPI service: /health, /api/v1/diagnose, /api/v1/plan,
            /api/v1/assembly, /api/v1/verify
/docs       DEVICE_SETUP.md — physical-device runbook
/assets     Branding/demo materials
FIXLENS_MASTER_BUILD_SPEC.md   Single source of truth
FIXLENS_PROGRESS.md            Phase-by-phase build log
```

## Device configuration

All external identifiers live in ONE developer-provided device config file
(never compiled into the app). Written per device/AVD:

```bash
adb shell "run-as com.fixlens.app sh -c 'cat > files/fixlens.properties'" <<'EOF'
backend.url=http://127.0.0.1:8000
revenuecat.api_key=testn_YOUR_TEST_STORE_KEY_FROM_DASHBOARD
EOF
```

`revenuecat.api_key` = the app-specific **Test Store API key** from the
RevenueCat dashboard (Project Settings → API keys). Without it, the app runs
in free mode: the 3-scans/month allowance still counts, but purchases are
disabled and the paywall says so honestly. Optional overrides:
`revenuecat.entitlement` (default `fixlens_pro`), `revenuecat.product_monthly`
(default `fixlens_monthly`), `revenuecat.product_annual` (default
`fixlens_annual`), `revenuecat.product_pack5` (default
`fixlens_repair_pack_5`), `revenuecat.product_pack10` (default
`fixlens_repair_pack_10`). Restart the app after changing the file.

**Never ship a Test Store key**: the RevenueCat SDK crashes release builds
on purpose when it finds one — swap to your platform store key before any
production build.

## Quick start

1. **Backend**

   ```bash
   cd backend
   python3 -m venv .venv
   source .venv/bin/activate
   pip install -r requirements.txt
   cp .env.example .env        # then fill in GEMINI_API_KEY / OPENROUTER_API_KEY
   uvicorn app.main:app --host 127.0.0.1 --port 8000
   curl http://127.0.0.1:8000/health   # {"status":"ok","version":"0.6.0"}
   ```

2. **Android** — follow `docs/DEVICE_SETUP.md` (developer options → USB
   debugging → `adb reverse tcp:8000 tcp:8000` → device config → installDebug).
   Android Studio: open `android/` and press Run.

3. **Test the AI pipeline** — in the app: *Scan a Photo* → capture anything →
   *Use image* → the diagnosis screen appears with the real analysis. If the
   photo is too dark, blank, or blurry, FixLens asks for a better image
   before spending any AI quota; if the evidence is insufficient, the result
   shows **I NEED A BETTER VIEW** with a specific camera instruction.

4. **Test guided repair (Phase 4)** — on a GUIDE/LOW-risk diagnosis, tap
   *Start Fix*: the plan is generated once, then the step screen appears with
   *I've Done This*, *Show Me* (live camera + pulsing target ring + the
   instruction), *Why?*, and *I can't do this* (skip with an explicit
   dialog). Completing every step shows "All guided steps completed." —
   Phase 4 does not verify the repair. MEDIUM-risk diagnoses show a
   BEFORE YOU START acknowledgement gate first. HIGH-risk diagnoses show the
   safety stop and never offer Start Fix.

5. **Test assembly mode (Phase 4)** — *Assemble Something* → photograph
   disassembled parts → *Get assembly steps*: either an ordered plan (parts
   list + numbered steps) or an explicit "I can't determine the order yet"
   screen naming the one view that would settle the order — the order is
   never guessed.

## Environment variables (`backend/.env`)

| Variable | Purpose |
|---|---|
| `GEMINI_API_KEY` | Google Gemini API key (primary provider) |
| `OPENROUTER_API_KEY` | OpenRouter key (fallback provider) |
| `AI_PROVIDER` | `gemini` (default) or `openrouter` |
| `AI_FALLBACK_PROVIDER` | Provider used when the primary is unavailable |
| `GEMINI_MODELS` | Comma-separated candidate models (first that answers wins) |
| `OPENROUTER_MODEL(S)` | Specific free vision model(s) — never a random router |
| `DIAGNOSE_TIMEOUT_SECONDS` | Request timeout (default 75) |

Never commit `.env`. `.gitignore` already excludes it.

## Tests

```bash
cd backend && .venv/bin/python -m pytest tests/ -v   # 83 tests (offline)
cd android && ./gradlew :app:testDebugUnitTest       # 35 tests (JVM + MockWebServer)
cd android && ./gradlew :app:lintDebug               # lint
```

Backend endpoint tests inject fake providers — no API quota is consumed by
the test suite. Live AI verification is performed explicitly (see
`FIXLENS_PROGRESS.md`).

## Current status

- ✅ Phase 1: Android foundation (navigation, Photo/Live camera, permissions,
  My Repairs), FastAPI `/health`, adb-reverse device connectivity
- ✅ Phase 2: AI diagnosis pipeline — provider abstraction, Gemini primary,
  OpenRouter fallback, strict validation, deterministic safety gate
- ✅ Phase 3: perception + diagnosis — components with OBSERVED/INFERRED
  distinction, controlled confidence bands, image quality gate, user-context
  handling (symptom ≠ evidence), spec §10 diagnosis layout, safety-stop and
  better-view variants; verified live with 6 real-image scenarios and
  end-to-end on the emulator
- ✅ Phase 4: guided repair + assembly — structured repair plans
  (`POST /api/v1/plan`, generated once per session), safety-before-generation
  gate (HIGH blocked, MEDIUM acknowledgement), pure-Kotlin repair state
  machine, step-by-step guidance screen, Show Me pulsing-target overlay,
  honest tool identification, difficult-step skip flow, assembly mode with
  order-uncertainty handling; verified live (real planner calls, all blocked
  paths) and end-to-end on the emulator including step-through to completion
- ✅ Phase 5: camera-based verification — `POST /api/v1/verify` compares a
  fresh capture against a step's expected state (PASS/FAIL/UNCERTAIN +
  evidence; UNCERTAIN always carries exactly one better-view instruction;
  PASS never guessed without visual evidence). One user-triggered frame per
  verification, never a stream. Result mapped onto the repair engine's
  verification seam: only PASS advances a step, FAIL/UNCERTAIN keep it open;
  the user may skip verification for a step (their own confirmation then
  completes it — never worded as verified); verified live (real Gemini:
  PASS/FAIL/UNCERTAIN + quality-gate rejection) and end-to-end on the
  emulator (verify → FAIL evidence → return to step, engine state intact)
- ✅ Phase 6: RevenueCat monetization — real RevenueCat SDK with Test Store
  support, `fixlens_pro` entitlement checked against live purchase state
  (never faked or cached), paywall with Pro monthly/annual from real
  offerings + one-time Repair Pack 5/10 + restore purchases, free plan of
  3 scans/month with credits granted exactly once per pack transaction.
  Gating is woven into the flow: camera UI always usable, allowance charged
  only after the backend accepts an image, guided repair/assembly gate
  before generation and resume after unlock, honest "not configured" state
  on devices without a key. API key is a developer-provided device config
  value (`revenuecat.api_key`), never compiled in
- ⏳ Next (Phase 5): visual verification of completed steps (the engine's
  READY_FOR_VERIFICATION phase is the plug-in seam), then monetization
  (RevenueCat) and Demo Mode

## Known limitations

- Free-tier AI quotas (Gemini daily cap; OpenRouter per-model free pools) can
  rate-limit analysis at busy times — the app shows an honest error and the
  request can be retried.
- The model can misjudge unusual photos; the deterministic safety layer can
  escalate but cannot make the model see things the image does not show.
- The safety corpus matches hazard phrases, not meaning: a NEGATED sentence
  like "no exposed wiring" still escalates (deliberately conservative —
  false blocks are safer than missed hazards).
- Show Me targets are approximate: the ring marks where to look in frame;
  there is no per-frame component tracking (and no per-frame AI calls).
- Phase 4 does not verify completed steps — completion says so explicitly.
- Physical-phone verification of the full flow is still pending (all device
  verification so far ran on the API 35 emulator).
