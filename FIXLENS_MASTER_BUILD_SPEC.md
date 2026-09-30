# FixLens Master Build Specification

This is the single source of truth for the FixLens project. It is intended to be read by the AI coding agent and by the human builder. The project is a student entry for the RevenueCat Shipaton 2026 Next Gen Award. Next Gen uses a demo video and public open-source repository rather than requiring a store listing. The official rules require the demo video to be under two minutes and to show the project running on its target device.

## 1. Product

FixLens is a mobile AI technician. A user can either capture a photo or point the phone's live camera at a physical object. FixLens identifies the object and visible issue, assesses safety, provides repair or assembly guidance, visually targets the relevant component, and verifies the user's action with a follow-up camera scan.

Core loop:

`OBSERVE -> UNDERSTAND -> SAFETY DECISION -> INSTRUCT -> USER ACTS -> VERIFY -> NEXT STEP`

The app must not become a generic chatbot. The camera is the primary interface.

## 2. V1 demo scenarios

1. Stuck office chair
2. Loose screw or simple mechanical repair
3. Simple furniture assembly
4. Unsafe electrical wiring safety stop

## 3. Mobile architecture

```text
Physical Android Phone
  CameraX
  Jetpack Compose UI
  RevenueCat SDK
        |
        | USB + ADB reverse
        v
Development Mac
  FastAPI backend :8000
  Gemini provider
  OpenRouter fallback
  Safety policy
  Repair state machine
```

The final demo is a real Android app on a real phone. Do not replace it with a browser clone.

## 4. AI runtime strategy

### Primary

Use a currently enabled Gemini multimodal Flash model through the Google Gemini API. Current official Google documentation lists Gemini 3.8 Flash as a multimodal model and the pricing page lists free input/output tokens for its free tier, subject to model access and quota. Gemini 2.5 Flash and 2.5 Flash-Lite are also documented multimodal models with structured-output capability. The agent must check which model is actually enabled for the specific API key before implementation.

Google documents image input directly and supports image understanding, object detection and segmentation workflows.

### Fallback

Use OpenRouter with one specifically configured free vision-capable model. Do not use a random free-model router for the core demo. Normalize and validate every response through the same Pydantic schema.

### Emergency fallback

Deterministic Demo Mode for office chair, assembly, loose screw, and electrical safety stop. Demo Mode uses the real camera UI but does not pretend that a canned result came from live AI.

### Critical provider rule

Do not put Gemini/OpenRouter secret keys in Android. Keys belong on the FastAPI backend.

## 5. Secrets and configuration

The coding agent needs the variable names, not the secret values. The human developer creates `backend/.env`.

```dotenv
GEMINI_API_KEY=PASTE_KEY_HERE
OPENROUTER_API_KEY=PASTE_KEY_HERE
```

Also create `.env.example` with placeholders. Add `backend/.env` to `.gitignore`.

Keep backend URL, provider, model IDs, timeouts, and demo mode configurable. Never hardcode them into Kotlin source.

RevenueCat is different from AI keys: the SDK uses a public/app-specific API key. Configure it from the RevenueCat dashboard and keep the code structured so the key is a local/configured value, not scattered across source files.

Because Google states that free-tier Gemini content may be used to improve its products, the demo must use non-sensitive images and objects rather than private personal data.

## 6. Photo mode

```text
User -> Scan a Photo -> Capture -> Backend -> Vision model -> Diagnosis
```

The response must include object, visible issue, likely causes, confidence, safety level, and whether another view is needed.

## 7. Live camera mode

Use CameraX for the real phone camera. Do not stream every frame to the multimodal model. Capture representative frames or user-triggered frames and send only what is needed.

The UI should support simple visual targeting with an overlay, not a full AR framework.

## 8. Safety system

Safety is a deterministic policy layer between model output and user instructions.

```text
Model diagnosis
      v
Safety policy
  LOW -> guide
  MEDIUM -> limited guidance + warnings
  HIGH -> safety stop
```

High-risk examples: exposed mains electrical wiring, suspected gas leaks, severely damaged lithium batteries, structural instability, high-voltage systems, dangerous machinery.

When high-risk is detected, discard actionable repair instructions and tell the user to contact the appropriate qualified professional.

When evidence is insufficient, ask for one specific camera view rather than guessing.

## 9. AI prompt pack

### Global system prompt

```text
You are FixLens, a cautious visual repair and assembly assistant.
Your job is to analyze only the physical evidence present in the supplied image(s), identify the object and visible issue, assess safety conservatively, and produce structured information for the FixLens app.
Never invent a component, measurement, diagnosis, or repair step that cannot be supported by the visual evidence.
If you cannot determine the object or issue reliably, request one specific additional view.
Do not provide actionable instructions for high-risk electrical, gas, high-voltage, severe battery, structural, or dangerous-machinery scenarios.
Return only the requested structured result.
```

### Diagnosis prompt

```text
Analyze this image for FixLens. Identify the main physical object, the visible issue, likely causes supported by the image, and the safest next action.
Return: object, components_visible, issue, likely_causes, confidence, safety_level, professional_category_if_needed, needs_better_view, requested_view, notes.
Do not guess hidden internal failures.
```

### Repair-plan prompt

```text
Given the verified diagnosis below, create the smallest safe sequence of repair steps a normal user can perform.
Each step must have an action, target component, tool if needed, expected visual result, risk note, and whether camera verification should be requested.
Do not include a step that the safety policy marks as high risk.
```

### Visual-target prompt

```text
Identify the exact visible component the user should interact with for the current step. Return a bounding box using normalized coordinates [ymin, xmin, ymax, xmax] on a 0-1000 scale. If it is not clearly visible, request a better view.
```

### Verification prompt

```text
Compare the current image with the expected state for this repair step. Decide whether the step appears complete, incomplete, or cannot be verified from the image. Explain only the visual evidence needed for the decision.
```

### Better-view prompt

```text
The available image is insufficient for a safe decision. Ask for exactly one useful camera change, such as moving closer, showing the underside, rotating the object, or centering a specific component. Do not ask for several unrelated views.
```

### Safety prompt

```text
Classify the situation conservatively. If there is a plausible serious injury, fire, explosion, electrocution, structural collapse, or other major hazard, classify as HIGH. Do not downgrade HIGH based only on uncertainty. Return risk_level, reasons, safe_boundary, professional_type.
```

## 10. Structured schemas

Use Pydantic on the backend. Suggested enums:

```text
Mode = PHOTO | LIVE | VERIFY
SafetyLevel = LOW | MEDIUM | HIGH
ActionType = GUIDE | LIMITED_GUIDE | ASK_FOR_VIEW | SAFETY_STOP
VerificationState = PASS | FAIL | UNCERTAIN
```

Do not allow raw provider response objects to flow directly into the Android UI.

## 11. Repair state machine

```text
SCAN
  -> ANALYZE
  -> SAFETY_CHECK
  -> DIAGNOSIS
  -> GUIDE / LIMITED_GUIDE / ASK_FOR_VIEW / SAFETY_STOP
  -> USER_ACTION
  -> VERIFY
  -> NEXT_STEP or COMPLETE
```

## 12. RevenueCat monetization

### Plans

| Plan | Price | Access |
|---|---:|---|
| Free | $0 | 3 scans/month, basic diagnosis and basic guidance |
| Pro Monthly | $7.99/month | Unlimited scans, live guided repair, verification, advanced troubleshooting, complex assembly |
| Pro Annual | $59.99/year | Same Pro features, annual value pricing |
| Repair Pack 5 | $3.99 one-time | 5 advanced repair credits |
| Repair Pack 10 | $6.99 one-time | 10 advanced repair credits |

Products:

```text
fixlens_monthly
fixlens_annual
fixlens_repair_pack_5
fixlens_repair_pack_10
```

Entitlement:

```text
fixlens_pro
```

### Monetization logic

Free users get 3 scans/month. Basic diagnosis remains usable. When the user tries to start a premium guided repair after the free allowance, show the RevenueCat paywall. A non-subscription user can use a Repair Pack for advanced repairs.

RevenueCat should manage products, entitlements, offerings/paywall presentation, purchases, restore purchases, loading, failures, and cancellation states. The current RevenueCat documentation states that its Test Store can be used without connecting to Apple or Google production stores, and test purchases do not charge real money.

## 13. Demo Mode

Demo Mode must be deterministic and reliable. It uses the real camera UI, but the backend result is pre-authored for the four demo scenarios. It exists to protect the final two-minute video from network or model flakiness.

Scenarios:

1. Stuck office chair: diagnosis -> highlighted component -> repair step -> verification pass
2. Assembly: recognize visible pieces -> generate order -> target connection -> verification
3. Loose screw: target screw -> tighten -> verification
4. Electrical wires: identify unsafe condition -> Safety Stop -> electrician recommendation

## 14. Mobile development runbook for a beginner

### Install and open

Use Android Studio. Open `/android`. Connect a physical Android phone with USB debugging enabled. Android Studio can build and deploy the debug app directly to the device.

### Backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### Connect phone to backend

With the phone connected by USB:

```bash
adb devices
adb reverse tcp:8000 tcp:8000
```

Then the app can use `http://127.0.0.1:8000` in this development mode. Android documents `adb reverse` as a way for a device or emulator to access a host-local development server.

### Build/install

```bash
cd android
./gradlew installDebug
```

Or press Run in Android Studio after selecting the connected phone.

### Alternative LAN mode

If USB reverse is unavailable, run FastAPI on `0.0.0.0`, find the Mac's LAN IP, and configure the app with `http://LAN_IP:8000`. Never hardcode this address.

## 15. Progress control file

Create `FIXLENS_PROGRESS.md` at repository root. The agent must read it before every session and update it after every meaningful task.

Required contents:

```text
Last updated
Current phase
Overall status
Objective
Completed
In Progress
Remaining
Blocked
Tests Run
Device Verification
Known Issues
Decisions
Next Action
```

A feature is not complete until the relevant build/tests/device checks pass. The progress file is the continuity mechanism between coding-agent sessions.

## 16. Agent operating protocol

1. Read this spec and `FIXLENS_PROGRESS.md`.
2. Inspect the repository.
3. Work on one phase only.
4. Build and test.
5. Run on the physical phone when the change touches mobile behavior.
6. Update `FIXLENS_PROGRESS.md`.
7. Report files changed, tests run, device status, blockers, and next action.
8. Do not expand scope before the core loop works.

## 17. Implementation phases

1. Repository inspection and architecture plan.
2. Android foundation + CameraX + navigation.
3. FastAPI + normalized schemas + mock endpoint.
4. Gemini provider integration.
5. OpenRouter provider fallback.
6. Diagnosis UI + repair state machine.
7. Visual targeting + verification.
8. Deterministic safety policy + Safety Stop UI.
9. RevenueCat + subscriptions + repair credits.
10. Deterministic Demo Mode.
11. Hardening, tests, README, LICENSE, fresh-clone verification.

## 18. Master prompt for the coding agent

```text
You are the lead engineer for FixLens. Read FIXLENS_MASTER_BUILD_SPEC.md and FIXLENS_PROGRESS.md completely before writing code.

This is a real Android project for the RevenueCat Shipaton Next Gen Award. The target demo is a physical Android phone, not a browser clone and not a fake mockup.

Build the camera-first loop:
OBSERVE -> UNDERSTAND -> SAFETY -> INSTRUCT -> USER ACTS -> VERIFY -> NEXT STEP

Use Kotlin + Jetpack Compose + CameraX on Android and FastAPI + Pydantic on the backend. Keep AI provider logic behind an abstraction. Primary provider: a currently enabled Gemini multimodal Flash model. Fallback: a specifically configured free OpenRouter vision model. Never put secret AI keys in the Android app.

Implement Photo Mode and Live Camera Mode. Use the real phone camera. Keep visual guidance simple and polished using overlays rather than full AR.

Implement deterministic safety gating. High-risk cases must stop and recommend a qualified professional. If evidence is insufficient, request one specific better camera view.

Implement RevenueCat with the following products:
fixlens_monthly
fixlens_annual
fixlens_repair_pack_5
fixlens_repair_pack_10

Entitlement: fixlens_pro

Free: 3 scans/month
Pro Monthly: $7.99/month
Pro Annual: $59.99/year
Repair Pack 5: $3.99
Repair Pack 10: $6.99

Use RevenueCat Test Store for development.

Implement Demo Mode for four deterministic scenarios so the two-minute demo remains reliable.

For every phase:
- read FIXLENS_PROGRESS.md
- inspect current code
- implement only the phase
- build
- run tests
- install/run on physical device where relevant
- update FIXLENS_PROGRESS.md
- report exact evidence
- identify the next action

Never hardcode secrets. Never hardcode a fake production result. Never claim completion without verification.
```

## 19. Final demo sequence

```text
0:00 broken chair
0:03 open FixLens
0:08 live camera recognition
0:15 diagnosis
0:25 visual target
0:35 repair action
0:45 verification
0:52 assembly example
1:20 unsafe electrical example -> Safety Stop
1:35 RevenueCat paywall / plans
1:45 Pro unlock
1:50 architecture / repository
1:57 FixLens logo
```

## 20. Final engineering checklist

- Android app runs on physical phone
- Photo Mode works
- Live Camera Mode works
- Camera permissions work
- Backend health endpoint works
- Gemini works with configured key
- OpenRouter fallback works or is explicitly marked unavailable
- Provider outputs validate through Pydantic
- Safety Stop works for electrical scenario
- Better-view request works
- Visual target overlay works
- Verification flow works
- RevenueCat Test Store purchase works
- Pro entitlement changes the app state
- Repair credit purchase works
- Demo Mode works
- No secrets committed
- No provider URLs or IPs hardcoded
- `FIXLENS_PROGRESS.md` is current
- Fresh clone setup is documented and tested
- Final two-minute demo is recorded on the real phone

## 21. Current official references

- RevenueCat Shipaton 2026 rules: https://revenuecat-shipaton-2026.devpost.com/
- RevenueCat Android SDK: https://www.revenuecat.com/docs/getting-started/installation/android
- RevenueCat configuration: https://www.revenuecat.com/docs/getting-started/configuring-sdk
- RevenueCat products/entitlements: https://www.revenuecat.com/docs/projects/configuring-products
- RevenueCat paywalls: https://www.revenuecat.com/docs/tools/paywalls
- Google Gemini models: https://ai.google.dev/gemini-api/docs/models
- Google Gemini pricing: https://ai.google.dev/gemini-api/docs/pricing
- Google Gemini image understanding: https://ai.google.dev/gemini-api/docs/image-understanding
- Android hardware device setup: https://developer.android.com/studio/run/device
- Android ADB reverse forwarding: https://developer.android.com/develop/ui/views/layout/webapps/access-local-server
