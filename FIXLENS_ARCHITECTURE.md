# FixLens, Architecture Plan (Phase 1 snapshot)

> **Historical document.** This was the Phase 1 architecture plan; it is kept
> for the record only. The current source of truth is
> `FIXLENS_MASTER_BUILD_SPEC.md` plus the per-phase records in
> `FIXLENS_PROGRESS.md`, layer sequencing there superseded some numbering
> here (e.g. verification shipped in Phase 5, RevenueCat in Phase 6, Demo
> Mode in Phase 7). Do not treat phase numbers in this file as current.

---

## 1. Repository inspection, current state

| Area | State |
|---|---|
| Android app | **Not present.** No `android/` directory, no Gradle project. |
| FastAPI backend | **Not present.** No `backend/` directory. |
| Spec (`FIXLENS_MASTER_BUILD_SPEC.md`) | Present, complete, product, loop, scenarios, safety, schemas, monetization, phases. |
| `FIXLENS_PROGRESS.md` | Present, matches spec §15 template; Phase 1 is the active task. |
| Secrets | Root `.env` exists with `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `REVENUECAT_PUBLIC_API_KEY` **all configured** (names only checked; values never read). No `.gitignore` existed before this phase. |
| Version control | **Not a git repository yet.** |
| Toolchain | Python 3.9.6, Java 22, adb 1.0.41 available. Android Studio presence not verifiable from CLI; required per spec §14. |
| Tests | None, nothing to test yet. |

**Risk identified and closed this phase:** the root `.env` with live keys sat in a repo with no ignore rules. `.gitignore` now excludes `.env` files (keeping `!.env.example` tracked) *before* any `git init`/first commit can ever include secrets.

**Open question for the human builder:** the three keys currently live in a root `.env`. Per spec §5, AI keys belong in `backend/.env` and the RevenueCat public key is configured Android-side. When Phase 2/3 start, keys should move to the locations the spec defines; this phase did not move them.

---

## 2. Target repository layout (created across Phases 2-6)

```text
fixlens/
├── FIXLENS_MASTER_BUILD_SPEC.md      # exists, source of truth
├── FIXLENS_ARCHITECTURE.md           # this file
├── FIXLENS_PROGRESS.md               # exists, continuity file
├── .gitignore                        # added in Phase 1
├── android/                          # Phase 2
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/libs.versions.toml     # version catalog
│   └── app/
│       └── src/main/java/com/fixlens/app/
│           ├── MainActivity.kt           # single-activity Compose host
│           ├── navigation/               # nav routes: Scan, Result, Guide, Paywall
│           ├── camera/                   # CameraX preview, photo capture, live frames
│           ├── ui/
│           │   ├── screens/              # ScanScreen, ResultScreen, GuideScreen, ...
│           │   ├── overlay/              # simple normalized-box targeting overlay
│           │   └── theme/
│           ├── data/
│           │   ├── ApiClient.kt          # Retrofit/OkHttp -> FastAPI
│           │   ├── BackendConfig.kt      # URL/provider/model from BuildConfig/settings, never hardcoded
│           │   └── DemoMode.kt           # gate + local demo responses (Phase 10)
│           └── billing/                  # RevenueCat wrapper (Phase 9)
└── backend/                          # Phase 3 onward
    ├── requirements.txt
    ├── .env.example                  # placeholders; real .env never committed
    └── app/
        ├── main.py                   # FastAPI app, /health first
        ├── schemas.py                # Pydantic models: Mode, SafetyLevel, ActionType, ...
        ├── safety.py                 # deterministic safety policy layer
        ├── statemachine.py           # SCAN -> ... -> VERIFY -> NEXT_STEP/COMPLETE
        ├── demo_mode.py              # deterministic scenario data (Phase 10)
        └── providers/
            ├── base.py               # provider abstraction (spec §4 "behind an abstraction")
            ├── gemini.py             # primary
            ├── openrouter.py         # fallback
            └── selector.py           # Gemini -> OpenRouter -> Demo Mode
```

Naming, package choices, and file boundaries may shift slightly during implementation; the layer boundaries may not.

---

## 3. Core loop, traceable mapping (spec §1, §11 → code)

The spec's loop is the product. Every future layer exists to serve one stage of it:

```text
OBSERVE      CameraX capture (photo or live frame)                 → Phase 2 (camera), 6/7 (flow)
UNDERSTAND   /v1/diagnose via Gemini → Pydantic Diagnosis          → Phase 3/4 (backend), 6 (UI)
SAFETY DECISION  deterministic policy gates the diagnosis          → Phase 8
INSTRUCT     repair plan steps + visual-target overlay box         → Phase 6/7
USER ACTS    user performs the physical step                       → (off-screen)
VERIFY       follow-up camera scan → PASS/FAIL/UNCERTAIN           → Phase 7
NEXT STEP    state machine advances or requests a better view      → Phase 6
```

Hard rules carried into every phase:

- **Camera-first.** No free-text chat surface anywhere in the app (spec §1, §18).
- **Safety is deterministic.** The policy layer sits between model output and user instructions and can *discard* model instructions (spec §8).
- **Normalized schemas only.** Android never sees raw provider responses; everything passes through Pydantic validation (spec §10).
- **Secrets stay server-side.** Only the RevenueCat *public* SDK key is Android-side (spec §4, §5).
- **Insufficient evidence → exactly one specific better-view request** (spec §8).

---

## 4. Request/response flow (development mode)

```text
Android (CameraX frame/photo, JPEG)
   │  POST http://127.0.0.1:8000  (via adb reverse tcp:8000 tcp:8000; LAN fallback configurable, never hardcoded)
   ▼
FastAPI :8000
   ├─ Pydantic request validation (mode, image, state)
   ├─ Provider selector: Gemini ──fail──▶ OpenRouter ──fail──▶ Demo Mode
   ├─ Response normalized through Pydantic schemas (spec §10)
   ├─ Safety policy: LOW → guide · MEDIUM → limited guide + warnings · HIGH → safety stop
   └─ Repair state machine: SCAN → ANALYZE → SAFETY_CHECK → DIAGNOSIS
        → GUIDE / LIMITED_GUIDE / ASK_FOR_VIEW / SAFETY_STOP → USER_ACTION → VERIFY → NEXT_STEP | COMPLETE
   ▼
Android renders: diagnosis card · step guidance · targeting overlay (0-1000 normalized box) ·
verification result · safety-stop screen
```

**Demo Mode discipline (spec §4, §13):** it uses the real camera UI, is deterministic per scenario, and is clearly labeled as pre-authored, never presented as live AI.

---

## 5. Backend contracts (locked in Phase 3)

One endpoint family, all responses Pydantic-validated:

- `GET /health`, liveness for the runbook's first device check
- `POST /v1/diagnose`, image(s) + mode → object, issue, likely causes, confidence, safety level, `needs_better_view` (+ `requested_view`)
- `POST /v1/plan`, verified diagnosis → minimal safe repair steps
- `POST /v1/target`, current image + step → normalized bounding box `[ymin, xmin, ymax, xmax]` on 0-1000
- `POST /v1/verify`, current image + expected step state → `PASS | FAIL | UNCERTAIN` + evidence

Enums match spec §10 exactly: `Mode`, `SafetyLevel`, `ActionType`, `VerificationState`.

---

## 6. Configuration & secrets plan (spec §4, §5)

| Concern | Where it lives | Rule |
|---|---|---|
| AI provider keys | `backend/.env` only (gitignored) | Never in Kotlin, never committed |
| `.env.example` files | tracked placeholders | Present from Phase 3 |
| Backend URL | Android `BackendConfig`, settings/config value | `adb reverse` default `127.0.0.1:8000`; LAN fallback user-configurable; never hardcoded (§14) |
| Provider + model IDs, timeouts, demo mode flag | backend config, env-driven | Never hardcoded anywhere |
| RevenueCat public SDK key | Android local/config value | Dashboard-configured; Test Store in dev (§12) |

**Model enablement check happens in Phase 4 before integration:** verify which multimodal Gemini Flash model the configured key can actually use (spec §4 requires checking the key, not assuming a model name).

---

## 7. Phase sequencing (spec §17, restated with entry/exit criteria, no scope added)

| # | Phase | Entry criteria | Exit criteria |
|---|---|---|---|
| 2 | Android foundation + CameraX + navigation | This plan reviewed | Compose app builds, installs on phone, camera preview + permission flow works, nav skeleton routes exist |
| 3 | FastAPI + schemas + mock endpoint | Phase 2 device run done | `/health` + mock `/v1/diagnose` return spec-valid schemas; phone reaches backend via adb reverse |
| 4 | Gemini provider | Phase 3 done, key enablement checked | Real diagnosis passes Pydantic validation |
| 5 | OpenRouter fallback | Phase 4 done | Fallback activates on Gemini failure, same schema |
| 6 | Diagnosis UI + repair state machine | Phase 5 done | Photo → diagnosis → guided steps end-to-end on device |
| 7 | Visual targeting + verification | Phase 6 done | Overlay box + verify flow work on device |
| 8 | Deterministic safety policy + Safety Stop UI | Phase 7 done | Electrical scenario produces Safety Stop, instructions discarded |
| 9 | RevenueCat + subscriptions + repair credits | Phase 8 done | Test Store purchase flips entitlement state |
| 10 | Deterministic Demo Mode | Phase 9 done | All four scenarios reproducible offline |
| 11 | Hardening, tests, README, LICENSE, fresh-clone verification | Phase 10 done | Checklist §20 fully green |

Phases stay strictly sequential; nothing outside the current phase is built early.

---

## 8. Verification strategy

- **Every phase:** build + tests relevant to the change; `FIXLENS_PROGRESS.md` updated immediately after.
- **Mobile-touching phases:** install and run on the physical phone (spec §14 runbook: Android Studio → `adb devices` → `adb reverse` → Run/installDebug), recorded as PASS/FAIL with evidence.
- **Backend phases:** endpoint smoke tests + Pydantic validation tests before any UI consumes them.
- **Final gate (Phase 11):** full §20 checklist against a fresh clone.

---

## 9. Immediate next action (Phase 2)

Create the Android project: Kotlin + Jetpack Compose + CameraX, single-activity navigation skeleton (Scan → Result → Guide), camera permission flow, and verify it installs and shows the live preview on the physical phone. Backend connection is deliberately **not** part of Phase 2.
