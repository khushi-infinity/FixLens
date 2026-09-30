# FixLens Progress

Last updated: 2026-09-30
Current Phase: Phase 9 — FINAL AUDIT + SUBMISSION — COMPLETE
Overall status: ON_TRACK

## VISUAL IDENTITY (post-audit watercolor restyle)
**Complete — verified by build, tests, and emulator E2E + screenshots.**
- Palette: warm paper (`#F5F1E7`) / cream surfaces, charcoal ink text, terracotta primary, sage & lavender-blue washes, muted rule lines — the dark-teal theme is fully retired (no legacy color constants remain).
- Typography: serif display headings + humanist sans body (system families; offline-safe, scales with accessibility settings).
- Hand-drawn details: `Workshop.kt` paper-grain background modifier, sketchy ink target overlay on camera views, notebook-style numbered section cards, irregular card corner radii; workshop watercolor illustration on Home.
- All user-visible control strings preserved (`Scan a photo`, `Live camera`, `Try a guided demo`, `I've Done This`, `Start Fix`, …); shutters unchanged (`Capture (demo|verification)? photo`).
- Verified: `testDebugUnitTest` + `assembleDebug` + `lintDebug` all pass (zero warnings); fresh-install emulator smoke walks home → demo picker → demo capture → result → guided step with all checks OK.

## FINAL AUDIT (Phase 9 — this session)
**Overall: PASS — READY for submission, conditional on three developer actions (see Submission Blockers).**

### 1. Build — PASS
- Backend: starts clean, `/health` → `{"status":"ok","version":"0.6.0"}`; 109/109 pytest PASS.
- Android: `:app:testDebugUnitTest` 72/72 PASS, `:app:assembleDebug` PASS, `:app:lintDebug` PASS.
- Fixed during audit: the ONLY build warning (AGP informational "compileSdk 35 newer than AGP 8.5.2") suppressed explicitly via `android.suppressUnsupportedCompileSdk=35`; build now emits **zero warnings**. Audit APK installed on the emulator and re-verified by E2E.

### 2. Core product — PASS (emulator E2E on the audit build)
- Camera → capture → **real Gemini diagnosis** (television, High confidence, components with •/○ kinds) → **SAFETY level rendered** ("High Risk" observed live; pre-fold and post-scroll) → paywall/gated flows → demo-driven guided loop → verification → completion (same production screens/engine) — all exercised live this audit.
- Guided repair + verification + completion exercised end-to-end via the deterministic demo journey (identical screens/engine to Real Mode; Real-Mode legs were fully live-verified in Phases 4–6, logic unchanged since).

### 3. Safety — PASS (live, zero leakage)
- HIGH wiring diagnosis → `POST /api/v1/plan` → **BLOCKED_HIGH_RISK, plan=null, ZERO model calls** (server log: `action=plan` count 0) — nothing generated, nothing leaks.
- chair → BLOCKED_BETTER_VIEW; furniture/no-issue → BLOCKED_NO_ISSUE — both plan=null.
- In-app better-view: Try Again → fresh intentional capture (verified across audit E2E runs).
- Demo J3 wiring: STOP + licensed-electrician referral + **no fix action exists** (verified negative) + honest banner.

### 4. AI — PASS
- Real provider calls throughout the audit (multiple diagnose 200s); provider/fallback layer unchanged since Phase 5 (OpenRouter fallback config-driven, marked unavailable when unset).
- No hardcoded AI responses in product code: demo content is data, permanently badged "DEMO MODE — scripted result, not live AI", provider marker "demo", and never touches network/billing; test fakes exist only under `src/test`.
- No hardcoded diagnosis/confidence/safety anywhere (confidence is a controlled band; safety is the deterministic policy layer).

### 5. RevenueCat — PASS (to the account-free limit)
- Paywall renders real state (audit E2E: FIXLENS PRO + feature list + honest unconfigured card + Restore responds).
- Free-limit gating organically reached again this audit: exhausted allowance → paywall at Start Fix (spec §12); fresh state scans freely.
- Products/entitlement exactly per spec §12, config-default IDs matching the dashboard contract.
- **Not exercised live: the native Test Store purchase/restore sheet itself** (needs a RevenueCat account + `testn_` key — developer action; purchase/restore code paths are implemented per SDK docs and unit-tested against a fake gateway; recorded honestly, never simulated).

### 6. Demo Mode — PASS (all three scenarios, audit build)
- J1 stuck chair: picker → real camera → capture → scripted diagnosis → Start Fix → 3 steps × scripted-PASS verification → "All guided steps completed." with banners and honest wording (no "verified" claim).
- J2 furniture assembly: steps + banner.
- J3 unsafe wiring: STOP + referral + no fix action + banner.
- Screenshots `/tmp/audit9_*.png`, logs `/tmp/audit9*.log`.

### 7. Configuration — PASS
- Secrets scan: no keys in any source file; only obviously-fake `testn_fake…` in unit tests; real `backend/.env` / root `.env` present but **gitignored** (patterns verified) — and this checkout is not a git repo, so nothing can be committed from it as-is.
- `android/local.properties`, `build/`, `.pytest_cache/`, `captures/` all gitignored.
- No hardcoded IPs in app code: `127.0.0.1:8000` appears only in setup docs and unit tests as the documented development default; the app reads its backend URL from device config.
- Fixed during audit: root `.env.example` was misleading (pointed Android config at an env var); rewritten to document the real flow (backend/.env for AI keys, device `fixlens.properties` for backend.url + revenuecat.api_key).

### 8. GitHub readiness — PASS (after doc fixes)
- README accurate (Phase 8 status header verified), spec + progress current, LICENSE (MIT) present, both `.env.example` files present and placeholder-only.
- Fixed during audit: `backend/README.md` was Phase-2-stale ("30 tests", missing plan/assembly/verify) — refreshed to the final endpoint set, 109 tests, run commands; `FIXLENS_ARCHITECTURE.md` marked as a historical Phase-1 snapshot so it cannot be mistaken for current truth.
- No build artifacts tracked (gitignore verified item-by-item).

### 9. Physical device — NOT TESTED
- No phone connected this session (`adb devices`: emulator-5554 only), consistent with every phase. The exact demo flow runs on the emulator; it is expected to transfer 1:1 (no network/clock dependence in Demo Mode), but the Shipaton video must be recorded on the physical phone.

### 10. Two-minute demo readiness — PASS
- Deterministic path covers: scan object → diagnosis → safety decision → guided repair → verification → completion → RevenueCat paywall/Pro (paywall reachable organically or via "FixLens Pro" entry) → distinctive FixLens experience (spec §10 layout, Show Me targeting, honest banners).
- Live-AI path works but is quota/latency variable (free tier observed 46–150+ s this phase); Demo Mode is the reliable recording path.

## Submission Blockers (Phase 9 — all developer actions, no code work remains)
1. **RevenueCat account + Test Store API key** (`revenuecat.api_key=testn_…` in device config; dashboard catalog must match the config IDs) to exercise the native purchase/restore sheet and record that beat of the video.
2. **Physical Android phone** connected for the final demo recording (spec §3/§20; never connected in any session).
3. **Push the local checkout to the empty canonical GitHub repo** (deliberately not done by the agent without instruction; `.gitignore` covers both `.env` files, `local.properties`, and build output — verify with `git status` before committing).

## Final Recommended Actions
1. `git init`, add the canonical remote, review `git status` for no ignored-file leaks, commit, push.
2. Create the RevenueCat project, Test Store key, and the 4 products + `fixlens_pro` entitlement; write the key via the DEVICE_SETUP runbook.
3. Connect the physical phone, follow docs/DEVICE_SETUP.md, verify the demo journey once, record the two-minute video (spec §19 shot list) using Demo Mode + paywall beats.
4. Optional: record one live-AI diagnosis beat during an unsaturated quota window.

## Files Changed (Phase 9 — audit fixes only, no features)
- `android/gradle.properties` (+suppressUnsupportedCompileSdk=35 — zero-warning build)
- `.env.example` (rewritten to document the real configuration flow)
- `backend/README.md` (Phase-2-stale → final endpoints/tests/run commands)
- `FIXLENS_ARCHITECTURE.md` (marked as historical Phase-1 snapshot)
- `FIXLENS_PROGRESS.md` (this record)

## Files Added (Phase 8)
- `android/.../imaging/UploadPrep.kt` (client-side upload downscale to the backend's model resolution — 1280 px longest edge; identical AI results, far faster wire; never fails preparation)

## Files Modified (Phase 8)
- `android/.../network/ApiClient.kt` (RELIABILITY: new `ApiError.Timeout` distinct from Unreachable — OkHttp SocketTimeoutException no longer lies as "could not reach"; AI window raised 90 s → 150 s after an 86 s diagnose was observed; timeout wording asks for a retry)
- `android/.../ui/screens/ScreenState.kt` (polish: fade/slide entrance animation on shared loading/error/empty states)
- `android/.../ui/screens/DiagnosisResultScreen.kt` (polish: rise-and-fade result entrance, safety-icon scale-in, honest "up to two minutes" loading copy)
- `android/.../ui/screens/PhotoCaptureScreen.kt` (polish: shutter reads as a camera shutter + `Capture photo` content-description; honest analyzing copy; upload downscaled via UploadPrep; timeout wording)
- `android/.../ui/screens/VerifyStepScreen.kt` (same shutter/semantics polish; upload downscaled; timeout wording)
- `android/.../ui/screens/AssemblyModeScreen.kt` (upload downscaled; timeout wording)
- `android/.../ui/screens/GuidedRepairScreen.kt` (polish: animated step-progress bar; HONESTY FIX: DEMO MODE banner now on guided steps AND completion — previously only picker/camera/result/verify had it; timeout wording; stale Phase-5 comment updated)
- `android/.../ui/screens/DemoCameraScreen.kt` (shutter visual + `Capture demo photo` content-description)
- `android/.../ui/BackendStatusChip.kt` (maps the new Timeout error honestly)
- `android/app/build.gradle.kts` (versionName 0.1.0-phase1 → 0.8.0-phase8, versionCode 8; removed unused deps)
- `android/gradle/libs.versions.toml` (removed unused androidx-junit/espresso/tooling-preview entries)
- `android/app/src/main/java/com/fixlens/app/FixLensApp.kt` (removed debug startup log — the only println-grade log in the app)
- `README.md`, `FIXLENS_PROGRESS.md`

## Polish Completed (Phase 8)
- **Animations**: shared loading/error/empty states fade+slide in; diagnosis result rises in with a safety-icon scale-pop; guided step-progress bar animates on advance; Show Me's pulsing target overlay (pre-existing) retained. No motion exceeds ~350 ms; camera preview untouched.
- **Loading/error/success states**: every state renders through the shared animated components; new honest copy — "Free-tier AI can be slow — this can take up to two minutes." during analysis; timeout is now its own state ("The AI is taking longer than usual right now… try again") instead of a false connectivity claim; "Checking backend…" replaced with an honesty line about what analysis actually is.
- **Camera experience**: shutters on all three camera screens now render as a proper shutter (outer ring + inner circle), carry contentDescriptions, and keep the Phase 5/6 robustness (bind-ready gating, one auto-retry, targeted unbind); permission and camera-error paths unchanged and still graceful.
- **Accessibility**: all three shutters are semantic buttons; audit found no unlabeled IconButtons; touch targets unchanged (74 dp shutters, 52–56 dp buttons).
- **Performance**: uploads now downscale client-side to exactly what the model sees (backend resizes to 1280 px anyway) — multi-MB camera originals become ~100 KB uploads with identical AI results; no polling loops exist anywhere (single /health per entry); capture already uses CAPTURE_MODE_MINIMIZE_LATENCY; per-call 90→150 s AI timeout prevents mid-generation aborts; Dispatchers.IO protected; no UI freezes found (all network off-Main).
- **Cleanup**: removed `kotlinx-coroutines-debug` (hardcoded, unused), `ui-tooling-preview` (no @Preview anywhere), all androidTest-only deps (no androidTest sources exist) and their version catalog entries; removed the app-start debug log; dead-code scan found no unused screens/symbols (`MyRepairsScreen`, `CaptureStore` all live); secrets scan clean (config files only, .gitignore covers .env; not a git repo locally so nothing can leak via commit).
- **Version markers**: versionName/Code finally reflect reality (0.8.0-phase8 / 8).

## Tests Run (Phase 8)
- `backend: .venv/bin/python -m pytest tests/ -q` — PASS (109/109, unchanged; no backend changes this phase)
- `android: :app:testDebugUnitTest` — PASS (72/72); `:app:assembleDebug` PASS; `:app:lintDebug` PASS (all on the slimmed dependency graph)
- **E2E (emulator, final 0.8.0 build)**:
  - **Live main flow**: Camera → capture → real Gemini diagnosis rendered with safety level (DIAG_OK + SAFETY: OK) → better-view stochastic variant handled via Try Again → full diagnosis variant with Start Fix reached → **free-scan allowance exhausted organically by the session's scans → RevenueCat paywall appeared at Start Fix (correct spec §12 gating; device datastore showed scans_this_month=3)** — evidence /tmp/e2e8_paywall.png
  - **Reliability proof under degraded free tier**: a >150 s Gemini stall produced the NEW honest timeout wording (not the old false "could not reach"); retry affordances worked every time
  - **Demo journey (deterministic, full polish surface)**: picker → real camera → capture → scripted diagnosis → Start Fix → 3 steps with scripted-PASS verification → "All guided steps completed." **with the DEMO MODE banner now visible on steps and completion** (the E2E caught its absence and the fix is verified) — /tmp/e2e8demo.log
- Backend request log confirmed the fast path: image 200 KB → preprocessed 83 KB at 960×1280 (client downscale + backend resize in concert)

## Physical Device (Phase 8)
NOT TESTED (physical) — no phone connected this session (`adb devices`: emulator-5554 only), consistent with every prior phase. All polish verified on Medium_Phone_API_35.

## Known Issues (Phase 8)
- Free-tier Gemini latency was extreme during this session (86 s diagnose observed; some calls exceeded even 150 s): the app now communicates this honestly and recovers, but demo recording should prefer Demo Mode or an unsaturated window.
- The live guided-repair + verification legs were not re-run to completion this session (free tier saturated; both stalls ended in honest retry states). Both legs are logic-unchanged since Phase 5's full live E2E and the polished UI surfaces were exercised via the demo journey; noted honestly rather than claimed.
- The allowance is device-local (by design); `pm clear` resets it — used deliberately in E2E to test both exhausted and fresh states.
- Physical-device verification still pending (user must connect a phone).

## Blockers (Phase 8)
- None software-side.

## Architecture Decisions (Phase 8)
- Polish stayed surgical: animations added inside existing composables, no screen rewrites; Real-Mode logic untouched (72/72 tests pass unchanged).
- UploadPrep mirrors the backend constant (1280 px) instead of inventing a new one — one source of truth for model resolution, with the backend's own resize kept as the authority.
- Timeout became a distinct error type rather than a wording patch: connectivity failures and slow-AI failures have different causes and now different, honest messages everywhere (chip, capture, plan, verify, assembly).
- The demo-banner gap was treated as an honesty bug (not cosmetic): scripted steps and completion previously rendered identically to live AI results — fixed and E2E-verified.

## Not Yet Implemented (Phase 9)
- Fresh-clone verification (clean checkout → backend up → app on device) — spec build-order item 11
- Final two-minute demo video on a physical phone (Demo Mode ready; physical device still required)
- Session persistence across restarts; visual-target bounding boxes (spec §17.7)

## Next Recommended Task (as of Phase 8)
Phase 9 — release hardening: fresh-clone verification per spec §17.11/§17 (clean checkout → README instructions → backend → app), then the two-minute demo video on a physical phone using Demo Mode (now with honest banners on every scripted screen).

## Files Added (Phase 7)
- `android/.../demo/DemoScenarios.kt` (three pre-authored journeys as pure provider-agnostic content: chair guided repair, furniture assembly, wiring safety stop)
- `android/.../demo/DemoModels.kt` (DemoDiagnosis/DemoStep/DemoJourney + DemoMappers into the exact production wire DTOs and session plan)
- `android/.../ui/screens/DemoPickerScreen.kt` (scenario picker over the live camera + DEMO MODE honesty banner)
- `android/.../ui/screens/DemoCameraScreen.kt` (real CameraX capture inside Demo Mode; shutter always ready; nothing uploaded)
- `android/.../ui/screens/DemoFlowRouter.kt` (DemoContext + staged router: CAPTURE → RESULT → GUIDED / safety-stop end)
- `android/app/src/test/java/com/fixlens/app/DemoScenariosTest.kt` (8 tests: content validity, production-shape mapping, engine completion, safety-stop invariants, determinism)

## Files Modified (Phase 7)
- `android/.../ui/screens/GuidedRepairScreen.kt` (demo short-circuit BEFORE network/billing: plan mapped from scripted content; demo never metered)
- `android/.../ui/screens/VerifyStepScreen.kt` (demo verification returns the scripted PASS with NO backend call and NO upload; banner on capture)
- `android/.../ui/screens/AssemblyModeScreen.kt` (demo assembly result resolved deterministically at capture; no AI/billing)
- `android/.../ui/screens/DiagnosisResultScreen.kt` (demo banner on the result screen; demo param threaded)
- `android/.../ui/screens/ShowMeCameraScreen.kt` (+BackHandler: system BACK now closes the overlay to the step)
- `android/.../ui/screens/VerifyStepScreen.kt` (+BackHandler during capture stage: BACK returns to the step, not out of the flow)
- `android/.../ui/screens/HomeScreen.kt` (+"Try the FixLens demo" entry)
- `android/.../MainActivity.kt` (+DEMO_PICKER / DEMO routes with activeDemo session state)
- `android/.../network/ApiClient.kt` (RELIABILITY: per-call 90s AI timeout for diagnose/plan/assembly/verify instead of the shared 20s config timeout; shared client untouched for /health)
- `README.md`, `FIXLENS_PROGRESS.md`

## Demo Mode (Phase 7)
PASS — all three journeys verified E2E on the emulator (screenshots /tmp/e2e7*.png, logs /tmp/e2e7b.log /tmp/e2e7c.log):
- **J1 Stuck office chair (full guided loop)**: picker → real camera (DEMO MODE banner) → capture → scripted diagnosis rendered in the production §10 layout (office chair, seized gas lift, •/○ components) → Start Fix → Step 1 of 3 with DO THIS/TOOL/CAREFUL/WHAT YOU SHOULD SEE AFTER → Show Me overlay → I've Done This → verify capture → Verify This Step → scripted PASS with WHAT FIXLENS SAW evidence → Next Step → ×3 → "All guided steps completed." with the honest no-verified-claim wording
- **J2 Furniture assembly**: picker → camera → capture → scripted shelving diagnosis → Start Fix → Step 1 of 3 ordered assembly steps with parts and expected states
- **J3 Unsafe electrical wiring (safety stop)**: picker → camera → capture → STOP card + electrician referral rendered; **no fix action offered** for HIGH risk (verified negative)
- **Determinism**: unit-tested (two full runs produce identical DTOs); engine completion driven by the SAME RepairEngine the live flow uses
- **Honesty**: persistent "DEMO MODE — scripted result, not live AI" banner on picker, camera, diagnosis, verification, and completion screens; provider marker "demo" carried on every scripted payload; nothing captured is analyzed, uploaded, or stored; zero billing interactions in demo (never metered, never gated)
- **Real Mode unchanged**: Real Mode path is bit-for-bit the same code with demo=null; demo hooks are parameter defaults (no behavior change when absent), and the live E2E suite from Phases 5/6 still exercises the real pipeline

## Reliability (Phase 7)
PASS:
- **AI timeout fallback**: per-call 90s timeout on the four AI endpoints (previously the shared 20s config timeout could kill a slow model call mid-flight and surface it as a confusing transport error); /health and config-intended timeouts unchanged
- **Crash prevention**: BACK from Show Me / verification capture no longer pops the whole guided-repair route (BackHandler added; found by the demo E2E — previously the demo flow could eject the user to Home mid-repair)
- **State recovery**: every demo/retry path re-enters through existing retry affordances (Try again / Start Fix re-tap / fresh capture); no state carries across demo sessions (activeDemo cleared on exit)
- Loading states were already honest from prior phases ("Preparing your repair plan…", "Comparing with the expected result…", "One photo, one check")

## Tests Run (Phase 7)
- `backend: .venv/bin/python -m pytest tests/ -q` — PASS (109/109, unchanged; no backend changes this phase)
- `android: :app:testDebugUnitTest` — PASS (72/72 = 64 prior + 8 new demo tests)
- `android: :app:assembleDebug` — PASS; `:app:lintDebug` — PASS
- **E2E (emulator)**: three demo journeys + Home entry + picker, all green (details above); two script iterations were needed to align with the staged demo flow and correct shutter coordinates — final run green
- E2E caught and fixed a real navigation bug (BACK popping the route — see Known Issues)

## Physical Device (Phase 7)
NOT TESTED (physical) — no physical phone was connected this session (`adb devices` shows only emulator-5554, as in every prior phase). The demo flow was verified end-to-end on the Medium_Phone_API_35 emulator instead. The scripted demo is deterministic by construction (no network, no AI, no clock dependence), so the emulator result is expected to transfer 1:1 to a physical device; the camera permission and CameraX paths exercised are the same ones verified on the emulator since Phase 2. Physical runbook unchanged (docs/DEVICE_SETUP.md).

## Known Issues (Phase 7)
- **BACK popped the guided flow (FOUND AND FIXED)**: system BACK from the Show Me overlay or verification capture exited the entire guided-repair route instead of returning to the step (missing BackHandler; caught by the demo E2E when the scripted finish loop broke). Fixed in both camera overlays; E2E green.
- Demo verification always returns scripted PASS: by design for the demo video, but it means Demo Mode does not demonstrate the FAIL/UNCERTAIN wordings (those are production-code paths verified in Phases 5 live tests). A scripted FAIL beat could be added if wanted.
- The assembly demo journey ends at the assembly-plan steps (no separate verification stage in the current assembly UX); the chair journey demonstrates the full verification loop.
- The demo picker requires camera permission (picker shows the live camera as its background); without permission the picker shows an explanatory allow-camera state instead of the scenario list.
- Physical-device verification still pending (user must connect a phone) — unchanged across phases.

## Blockers (Phase 7)
- None software-side. Physical-phone verification awaits a connected device.

## Architecture Decisions (Phase 7)
- Demo content is data, not code paths: one DemoScenarios object + mappers into the exact DTOs the live pipeline emits, so every scripted run renders through the unmodified production screens (result §10 layout, guidance, Show Me, verification, safety stop) — zero bespoke demo screens beyond the picker/camera.
- Demo short-circuits BEFORE network and BEFORE billing: no backend call, no allowance consumption, no credit spend, no entitlement read — Demo Mode cannot touch RevenueCat state by construction.
- Honesty is structural: persistent banner on every demo screen, provider marker "demo" on every scripted payload, picker copy stating nothing is analyzed — mirrors the spec's "does not pretend Demo Mode is live AI".
- The staged router (CAPTURE → RESULT → GUIDED) keeps the camera in the loop per spec §13 while staying deterministic: the capture is real, the result is not.
- Reliability work favored surgical changes: per-call timeouts instead of a new HTTP stack, BackHandlers instead of navigation rework — Real Mode behavior is preserved by default parameters.

## Not Yet Implemented (Phase 8+)
- Session persistence across app restarts (RepairSessionRecord type exists)
- Visual-target bounding boxes (spec §17.7 targeting; Show Me is approximate by design)
- Hardening pass (spec §17.11): fresh-clone verification, LICENSE, final README polish
- Final two-minute demo video (now unblocked: Demo Mode is ready for the real phone)

## Next Recommended Task (as of Phase 7)
Phase 8 per the build plan: hardening + release readiness (spec §17.11) — fresh-clone verification (clean checkout → backend up → app on device), LICENSE, README final polish, and recording the two-minute demo video on a physical phone using Demo Mode (chair → assembly → wiring safety stop → paywall), which now runs deterministically without network risk.

## Files Added (Phase 6)
- `android/.../billing/BillingConfig.kt` (developer-provided key + spec §12 product IDs, parsed from fixlens.properties)
- `android/.../billing/BillingGate.kt` (PURE spec §12 rules: 3 scans/month, credit/Pro decisions, month key, pack→credits)
- `android/.../billing/PurchasesGateway.kt` (gateway interface + BillingResult/BillingSnapshot/PaywallProduct/TransactionRef)
- `android/.../billing/RevenueCatGateway.kt` (the ONLY SDK-speaking class: configure/offerings/purchase/restore/listener)
- `android/.../billing/BillingRepository.kt` (entitlement sync + allowance + exactly-once credits; pluggable BillingStorage)
- `android/.../billing/BillingConfigStore.kt` (reads revenuecat.* from the device config file)
- `android/.../ui/screens/PaywallScreen.kt` (paywall: annual/monthly from real offerings, packs, restore, honest unconfigured state)
- `android/app/src/test/java/com/fixlens/app/BillingTest.kt` (17 tests: gate rules + repository vs fake gateway)

## Files Modified (Phase 6)
- `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts` (+com.revenuecat.purchases:purchases 10.15.1)
- `android/.../di/AppContainer.kt` (+billing collaborators; listener attach)
- `android/.../ui/screens/PhotoCaptureScreen.kt` (MonetizedCaptureFlow: allowance evaluated at spend; scans counted only after backend acceptance; credits spent on beyond-allowance scans; scans-remaining badge)
- `android/.../ui/screens/GuidedRepairScreen.kt` (premium gate BEFORE plan generation; resumes via retryToken after unlock; credit spent only when the plan arrives)
- `android/.../ui/screens/AssemblyModeScreen.kt` (premium gate BEFORE assembly generation; credit spent after gate allows)
- `android/.../ui/screens/HomeScreen.kt` (+FixLens Pro entry; plan badge: N free scans left / FIXLENS PRO)
- `android/.../MainActivity.kt` (+PAYWALL route; billing state to Home badge)
- `README.md` (+Device configuration section incl. revenuecat.api_key), `docs/DEVICE_SETUP.md` (+RevenueCat Test Store runbook), `FIXLENS_PROGRESS.md`

## Monetization (Phase 6)
PASS — architecture verified; purchase flows verified against the REAL SDK on the emulator to the extent possible without a RevenueCat account (no Test Store API key exists in this environment):
- **Products/entitlement** exactly per spec §12: fixlens_monthly ($7.99/mo), fixlens_annual ($59.99/yr), fixlens_repair_pack_5 ($3.99), fixlens_repair_pack_10 ($6.99), entitlement `fixlens_pro`. Product IDs are config defaults, overridable via the device config file.
- **Real purchase state only**: entitlements come from `CustomerInfo.entitlements.active` via the SDK; nothing is cached to disk, simulated, or inferred from purchases. Repurchases/net-new identities behave exactly as RevenueCat reports.
- **Entitlement checking**: BillingRepository syncs on start and via the SDK's UpdatedCustomerInfoListener (deferred attach — see crash fix below); Home badge reflects live state ("N free scans left" / "FIXLENS PRO").
- **3 free scans/month**: calendar-month counter (device-local courtesy gate); evaluated BEFORE analysis with a paywall at exhaustion; counted only after the backend accepted the image; credits cover scans beyond the allowance.
- **Repair-credit balance**: credits granted EXACTLY ONCE per one-time purchase, keyed by RevenueCat transaction id (restores/re-syncs cannot double-grant — unit-tested: same txn twice → 5, not 10 credits).
- **Paywall UI**: annual + monthly from the real offering, one-time packs, Restore Purchases, per-outcome purchase states (busy/cancelled/error with friendly messages), honest "not configured" card when no key is present (no fake products, ever).
- **Purchase flows** (subscription purchase, pack purchase, restore): code paths fully implemented through awaitPurchase/awaitRestore with PurchasesTransactionException handling (user-cancel distinguished from errors); the native/Test-Store sheet itself could not be exercised without a Test Store API key — recorded as BLOCKED-ON-ACCOUNT, not simulated.
- **Gating integrated naturally**: camera UI is never blocked; free users get the full basic diagnosis; the paywall appears exactly when spec says (scan beyond 3rd, premium action beyond allowance).

## Tests Run (Phase 6)
- `backend: .venv/bin/python -m pytest tests/ -q` — PASS (109/109, unchanged; no backend changes beyond the version string)
- `android: :app:testDebugUnitTest` — PASS (64/64 = 47 prior + 17 new billing tests)
- `android: :app:assembleDebug` — PASS; `:app:lintDebug` — PASS
- **E2E (emulator, /tmp/e2e6.log + /tmp/e2e6b.log)**: app launches with the RevenueCat SDK wired → Home shows the FixLens Pro entry → paywall opens with FIXLENS PRO + feature list → honest "Purchases are not configured on this device" state (NO fake products rendered) → dismiss returns to Home → full scan flow still works end-to-end on the Phase 6 build (real Gemini diagnosis rendered)
- E2E caught and fixed a real startup crash (see Known Issues)

## Physical Device (Phase 6)
NOT TESTED (physical) — no physical phone connected this session (`adb devices`: emulator-5554 only). Additionally, Test Store purchases require a RevenueCat account + Test Store API key, which the developer must provide (see docs/DEVICE_SETUP.md). All on-device verification ran on the Medium_Phone_API_35 emulator.

## Known Issues (Phase 6)
- **Startup crash on unconfigured devices (FOUND AND FIXED)**: attaching UpdatedCustomerInfoListener in AppContainer.<init> touched Purchases.sharedInstance before any configure() → UninitializedPropertyAccessException at app creation on devices without a key. Fixed: listener attach defers until the SDK is configured (Purchases.isConfigured guard + pendingListener). E2E re-run clean.
- **Test Store purchases NOT exercised live**: no RevenueCat account/API key exists in this environment. Purchase/restore code paths are implemented per the documented SDK API and unit-tested against a fake gateway, but the Test Store sheet itself (simulate-success modal) awaits a developer-provided key — recorded honestly as BLOCKED-ON-ACCOUNT, not simulated.
- Product/entitlement names on the RevenueCat dashboard must match the config defaults exactly (or the config file must override them); a mismatched catalog renders the paywall with missing entries — never wrong ones.
- The scan allowance is a client-side courtesy counter (device-local): reinstalling resets it, by design (RevenueCat remains the only purchase-truth source).
- Test Store subscriptions auto-renew on compressed schedules (1-month ≈ 5 min) — entitlement expiry can be observed live in minutes.
- Background servers die between agent tool calls in this environment (self-contained scripts used); irrelevant to real usage.
- Physical-device verification still pending (user must connect a phone).

## Blockers (Phase 6)
- Test Store purchase/restore flows require a RevenueCat account and Test Store API key (developer action; runbook added to docs/DEVICE_SETUP.md). Not a code blocker — the app runs correctly with or without the key.
- Physical-phone verification awaits a connected device.

## Architecture Decisions (Phase 6)
- Same boundary discipline as the AI provider layer: only RevenueCatGateway imports the SDK; every monetization rule is unit-testable against a fake (17 new tests, no device needed).
- BillingGate is pure logic (no Android/RevenueCat imports) — the spec §12 rules live in one place; the repository applies them to live state.
- Entitlement = SDK truth, never persisted; allowance/credits = local counters persisted via a BillingStorage interface (DataStore default, in-memory in tests).
- Credits are transaction-id-keyed for exactly-once grants: restore on a fresh install grants missing credits once, never twice.
- The gate decides BEFORE the action and charges AFTER success (scan counted only when the backend accepted the image; credit spent only when the plan/assembly actually generates).
- Premium gating follows spec §12 precisely: paywall for premium guided repair AFTER the free allowance — within the allowance, guided repair is basic guidance (this rule was corrected mid-phase after re-reading the spec; the first implementation was stricter than the spec).
- Unconfigured-key devices are first-class: free mode, honest paywall, zero fake data; the app must never crash for missing monetization config.

## Not Yet Implemented (Phase 7+)
- Deterministic Demo Mode (spec §17.10) — now the top recommendation before the demo video
- Session persistence across app restarts (RepairSessionRecord type exists)
- Visual-target bounding boxes (spec §17.7), fresh-clone verification, final demo video

## Next Recommended Task (as of Phase 6)
Phase 7 per the build plan: Deterministic Demo Mode (spec §17.10) — four pre-authored scenarios (chair, assembly, screw, electrical safety stop) that use the real camera UI, keep the loop honest, and protect the two-minute Shipaton video from the free-tier 429/503s observed in every session. (RevenueCat Test Store verification should be folded in by the developer when a RevenueCat account is created — runbook is in docs/DEVICE_SETUP.md.)

## Files Added (Phase 5)
- `backend/tests/test_phase5_verification.py` (26 tests: schema view rules, parser aliases, endpoint mapping)
- `android/app/src/main/java/com/fixlens/app/ui/screens/VerifyStepScreen.kt` (verification capture/review/result screen)
- `android/app/src/test/java/com/fixlens/app/VerificationTest.kt` (12 tests: wire contract + engine verification seam)

## Files Modified (Phase 5)
- `backend/app/schemas.py` (VerificationRequest/VerificationResult/VerifyResponse hardened: only UNCERTAIN may request a better view and it must always carry one; PASS/FAIL views stripped; explanation required; confidence bounded)
- `backend/app/prompts.py` (+VERIFY_JSON_CONTRACT: PASS only on clear visual evidence, UNCERTAIN is the honest fallback, aliases COMPLETE/INCOMPLETE accepted)
- `backend/app/providers/base.py` (+parse_verify_payload: state aliasing, never trusts raw model wording)
- `backend/app/providers/gemini.py`, `openrouter.py` (+verify() via the shared _generate transport)
- `backend/app/providers/selector.py` (+verify_with_fallback, +_verify_capability)
- `backend/app/main.py` (+POST /api/v1/verify — same quality gate as diagnosis, provider fallback, 400/502/503 mapping; version 0.5.0)
- `android/.../repair/RepairEngine.kt` (VerificationResult now Verified/FailedMismatch/Inconclusive/NotVerified; MarkAttempted legal from READY_FOR_VERIFICATION so the user can withdraw a confirmation; step completion still gated on Advance)
- `android/.../network/DiagnosisModels.kt` (+VerifyResponseDto, VerificationStates)
- `android/.../network/ApiClient.kt` (+verify(): multipart capture + step context)
- `android/.../ui/screens/GuidedRepairScreen.kt` (READY_FOR_VERIFICATION now opens the verification flow instead of auto-advancing; PASS→Advance, FAIL/UNCERTAIN→back to step; per-step skip-verification flag; honest completion wording updated)
- `android/.../camera/PreviewSession.kt` (FIX: targeted unbind of the session's own use cases instead of unbindAll() — a global unbind during screen transitions killed the next screen's fresh camera binding)
- `README.md`, `FIXLENS_PROGRESS.md`

## Verification API (Phase 5)
PASS — verified live and by unit tests:
- `POST /api/v1/verify`: multipart capture + step_number + expected_state (+ optional step_action/target_component/user_confirms_done) → `VerifyResponse{state, confidence, explanation, needs_better_view, better_view_instruction}`
- Same image quality gate as diagnosis runs BEFORE any model call (flat/dark/too-small frames rejected with 400 — verified live: near-uniform image → "This image has no visible detail...")
- Verification is isolated from diagnosis and from the safety gate: no re-diagnosis, no plan generation, no hazard re-classification (unit-tested)
- Provider fallback identical to the other endpoints (unavailable → 503 with retry UI text; malformed model output → 502; only UNCERTAIN may request a better view, schema-enforced)

## Verification Outcomes (Phase 5, live Gemini)
PASS — all three states observed from the real model through the real endpoint:
- **PASS**: hex-bolt step against the parts photo with a matching expected state → `state=PASS, confidence=1.0, explanation="The hex bolt is clearly visible and seated against the metal frame post."`
- **FAIL**: same photo against a contradicting expected state ("empty bolt hole with no bolt remaining") → `state=FAIL, confidence=1.0, explanation="The hex bolt is still clearly visible and seated... removal step has not been completed."` — the model refused to confirm a false claim
- **UNCERTAIN (5 scenarios)**: chair illustration, hardware-store wide shot, single-bolt close-up (insufficient framing), wrong-object capture (bicycle rack for a chair step), out-of-focus image — every one returned UNCERTAIN (never a guessed PASS) with exactly ONE specific better-view instruction naming a camera move AND the part to show
- Chair/screw/assembly step contexts all exercised; raw JSON in /tmp/p5_*.json, /tmp/p5b_*.json

## Android Verification Flow (Phase 5)
PASS (emulator E2E, /tmp/e2e5.log): guided plan (Step 1 of 2, real Gemini planner) → "I've Done This" → **verification capture screen** (SHOW THE RESULT panel with the step's expected state + shutter) → Back returns to the step, re-entry OK → capture → review ("Verify This Step") → real backend verify → **FAIL result screen**: exact spec wording, WHAT FIXLENS SAW evidence panel, expected-state reminder → "Keep Working On It" returned to Step 1 with the step correctly NOT completed (completedCount=0 behavior engine-tested; UI returned to step) → retry path available. PASS wording ("Step complete.") and UNCERTAIN wording ("I can't verify this clearly. Move the camera closer.") are rendered from the same result view (unit-tested strings; PASS/UNCERTAIN additionally exercised in live backend scenarios). One frame per explicit user action — no streaming anywhere.

## Step Progression (Phase 5)
PASS — the engine seam worked exactly as designed:
- READY_FOR_VERIFICATION no longer auto-advances; the UI dispatches Advance ONLY on PASS
- FAIL/UNCERTAIN/Back dispatch MarkAttempted → the step stays open (BACK to working state, engine-tested: Advance after FAIL is a no-op)
- "Skip verification for now" (UNCERTAIN path): user-declined verification for the CURRENT step; the next "I've Done This" completes on their own confirmation — never worded as verified
- No code path can complete a step without the PASS-gated Advance (engine-tested)

## Physical Device (Phase 5)
NOT TESTED (physical) — no physical phone connected this session (`adb devices` shows only emulator-5554). All device verification ran on the Medium_Phone_API_35 emulator as in Phases 2–4. Runbook unchanged (docs/DEVICE_SETUP.md).

## Tests Run (Phase 5)
- `backend: .venv/bin/python -m pytest tests/ -q` — PASS (109/109 = 83 prior + 26 new)
- `android: :app:testDebugUnitTest` — PASS (47/47 = 35 prior + 12 new)
- `android: :app:assembleDebug` — PASS; `:app:lintDebug` — PASS
- Live verify matrix (real Gemini through the real endpoint): 5 UNCERTAIN scenarios (chair illustration, screw wide-shot, assembly close-up, wrong object, out-of-focus) + PASS candidate + FAIL candidate + quality-gate 400 + missing expected_state 400 — raw JSON in /tmp/p5_*.json /tmp/p5b_*.json, log /tmp/p5_live.log /tmp/p5_live2.log
- **E2E (emulator, /tmp/e2e5.log)**: Scan a Photo → real diagnosis → Start Fix → Step 1 of 2 → I've Done This → VERIFY_CAPTURE OK → Back/re-entry OK → review → Verify This Step → VERIFY_STATE=FAIL (real model, honest refusal) → EVIDENCE_PANEL OK → EXPECTED_SHOWN OK → FAIL_RETURNS_TO_STEP OK (step not completed)
- E2E also caught and fixed a real camera bug the unit tests could not see (see Known Issues)

## Known Issues (Phase 5)
- **Camera binding race (FOUND AND FIXED this phase)**: `PreviewSession.stop()` used `unbindAll()`; when Show Me closed and the verification screen opened in the same transition, the global unbind killed the new screen's just-bound camera → "Not bound to a valid Camera [ImageCapture]" on shutter tap. Fixed with targeted `unbind(*boundUseCases)`; E2E re-run clean. The screen additionally retries a failed bind once and refuses the shutter until preview+capture are bound.
- Free-tier quotas: a Gemini 429/503 hit mid-E2E (handled by the app's honest error + retry; the E2E script waits out the per-minute window). Verification calls cost one image + a short prompt each — cheap, but not free.
- Model stochasticity: the same emulator scene can yield better-view at diagnosis time; the E2E script handles all variants. Verification itself was stable across 8 live calls.
- Verification judges the expected-state TEXT against the image: a step whose expected state is vague ("area looks correct") will verify worse than one with a concrete visual claim — plan prompts already require visual expected_state, which this phase now rewards.
- Repair sessions remain in-memory per screen entry (unchanged from Phase 4).
- LocalLifecycleOwner deprecation warning (cosmetic, pre-existing).
- Physical-device verification still pending (user must connect a phone).

## Blockers (Phase 5)
- None software-side. Physical-phone verification awaits a connected device.

## Architecture Decisions (Phase 5)
- Verification is a NEW capability on the provider abstraction (verify() mirroring plan()/plan_assembly()), not a diagnosis variant: the model judges expected-vs-observed for ONE step and is prompted not to re-diagnose or comment on unrelated issues.
- Honesty is schema law again: only UNCERTAIN may set needs_better_view and it must always carry exactly one instruction (fallback auto-filled); PASS/FAIL have view fields stripped; the parser aliases COMPLETE→PASS / INCOMPLETE→FAIL but never trusts unknown state words.
- The engine gained NO verification knowledge: PASS→Advance, FAIL/UNCERTAIN→MarkAttempted are UI dispatch decisions; the state machine remains AI-free and now also allows withdrawing a confirmation (MarkAttempted from READY_FOR_VERIFICATION) instead of trapping the user in the camera flow.
- Wordings are exact spec strings client-side ("Step complete." / "This step doesn't appear complete. The screw is still loose." / "I can't verify this clearly. Move the camera closer."); the model's evidence renders beneath, clearly separated ("WHAT FIXLENS SAW") — claim and evidence never conflated.
- The user can decline verification per step (skip flag): completion then rests on their own confirmation, which the UI words honestly ("completed", never "verified") — mirrors the Phase 4 rule that FixLens never claims success it did not see.
- One frame per explicit user action at every layer: the verify endpoint accepts a single image, the client never streams, and the loading state says so ("One photo, one check — nothing is analyzed continuously.").

## Not Yet Implemented (Phase 6+)
- RevenueCat: products, entitlement, paywall, repair credits (spec §17.9)
- Deterministic Demo Mode for the four demo scenarios (spec §17.10)
- Session persistence across app restarts (RepairSessionRecord type exists)
- Visual-target bounding boxes (spec §17.7 targeting: Show Me is approximate by design), fresh-clone verification, final demo video

## Next Recommended Task (as of Phase 5)
Phase 6 per the build plan: RevenueCat monetization (spec §17.9) — configure fixlens_monthly / fixlens_annual / fixlens_repair_pack_5 / fixlens_repair_pack_10 with the fixlens_pro entitlement via Test Store, gate verification + guided repair behind the free 3-scans allowance, and add the paywall. (Alternative if demo-video reliability is prioritized first: Demo Mode per spec §17.10 — it protects the recording from the free-tier 429/503s observed in every session.)

## Objective
Turn the FixLens specification into a reliable Android camera-first demo with a FastAPI backend, multimodal AI, deterministic safety gating, camera verification, and RevenueCat monetization.

## Implemented
### Phase 8 (this session)
- [x] **UI polish**: animated entrances on shared states, diagnosis result + safety icon motion, animated step progress, camera-shutter design on all three capture screens
- [x] **Honest states**: timeout is its own error with truthful wording everywhere; "up to two minutes" analysis copy; "Checking backend…" replaced
- [x] **Honesty fix (E2E-caught)**: DEMO MODE banner now on guided steps + completion (was missing there) — scripted results can never pass for live AI
- [x] **Performance**: client-side upload downscale to model resolution (~100 KB uploads); 150 s AI window after observing 86 s real latency; no polling; I/O off Main throughout
- [x] **Cleanup**: 4 unused dependencies + catalog entries removed, debug log removed, version markers bumped to 0.8.0-phase8, no secrets in code
- [x] **Accessibility**: shutters are semantic buttons; no unlabeled icon controls
- [x] **Tests**: 109 backend + 72 Android PASS; assemble + lint PASS; E2E: live diagnosis+safety OK, organic allowance exhaustion → paywall (spec §12), degraded-tier timeout honesty proven, demo journey end-to-end with banners

### Phase 7 (prior session)
- [x] **Demo Mode** (spec §13, trimmed to the demo trio): three deterministic journeys — stuck chair (full guided loop with verification), furniture assembly (ordered steps), unsafe wiring (safety stop + electrician referral)
- [x] **Real camera kept**: picker/capture run on the live CameraX preview; the scripted result follows the capture; nothing captured is uploaded, analyzed, or stored
- [x] **Production screens only**: scripted content maps into the exact wire DTOs/session shapes so result, guidance, Show Me, verification, completion, and safety-stop all render through unmodified production code
- [x] **Honesty**: persistent DEMO MODE banner on every demo screen; "demo" provider marker on scripted payloads; picker copy states the boundary; zero billing interaction in demo
- [x] **Safety-stop demo**: wiring journey renders STOP + electrician referral with no fix action offered (verified negative E2E)
- [x] **Reliability**: per-call 90s AI timeouts (diagnose/plan/assembly/verify) decoupled from the shared config timeout; BACK navigation fixed (Show Me / verify capture return to the step, not out of the flow — E2E-caught bug)
- [x] **Real Mode unchanged**: demo hooks are nullable parameter defaults; live pipeline code paths identical when demo is absent
- [x] **Tests**: 8 new demo tests (content validity, production-shape mapping, engine completion, determinism); suite 72/72; all three journeys E2E-verified on the emulator

### Phase 6 (prior session)
- [x] **RevenueCat SDK** (com.revenuecat.purchases 10.15.1) wired through a single gateway class; config-driven key (revenuecat.api_key in fixlens.properties), never compiled in
- [x] **Products + entitlement** per spec §12: fixlens_monthly/fixlens_annual/fixlens_repair_pack_5/fixlens_repair_pack_10, fixlens_pro
- [x] **Entitlement checking** against live CustomerInfo (start sync + UpdatedCustomerInfoListener; deferred attach); Home badge reflects real state
- [x] **Purchase flows**: subscriptions + one-time packs via awaitPurchase, restore via awaitRestore, user-cancel distinguished, friendly error mapping
- [x] **3 free scans/month**: calendar-month allowance, evaluated before analysis, counted only after backend acceptance, paywall at exhaustion
- [x] **Repair credits**: exactly-once grants keyed by RevenueCat transaction id; balance covers scans/premium actions beyond the allowance
- [x] **Paywall UI**: annual vs monthly pricing, packs, Restore Purchases, honest unconfigured state on key-less devices
- [x] **Natural gating**: camera never blocked; guided repair/assembly gate before generation and resume after unlock; premium-gate rule matched to spec §12 ("after the free allowance")
- [x] **Startup crash fixed** (unconfigured devices): SDK listener attach deferred until configure(); E2E-verified
- [x] **Tests**: 17 new Android billing tests (pure gate rules + repository vs fake gateway); suite 64/64

### Phase 5 (prior session)
- [x] **Verification endpoint** (`POST /api/v1/verify`): multipart capture + step_number + expected_state (+ optional step_action/target_component) → PASS/FAIL/UNCERTAIN with evidence; the same image quality gate as diagnosis runs BEFORE any model call; provider fallback identical to the other endpoints; verification isolated from diagnosis and from the safety gate
- [x] **Verification schema law** (`schemas.py`): only UNCERTAIN may request a better view and it must ALWAYS carry exactly one instruction (fallback auto-filled); PASS/FAIL have view fields stripped; explanation required; confidence bounded; mode pinned to VERIFY
- [x] **VERIFY_JSON_CONTRACT** (`prompts.py`): PASS only on clear visual evidence, UNCERTAIN is the honest fallback, judge only the image against the expected state, never use the user's claim as evidence; parser aliases COMPLETE→PASS / INCOMPLETE→FAIL but rejects unknown state words
- [x] **Provider verify()** on Gemini + OpenRouter via the shared _generate transport; verify_with_fallback in the selector with the same bounded fallback rules
- [x] **Android wire layer**: VerifyResponseDto + VerificationStates + ApiClient.verify() (one multipart frame per explicit user action)
- [x] **RepairEngine seam activated**: VerificationResult is now Verified/FailedMismatch/Inconclusive/NotVerified; READY_FOR_VERIFICATION no longer auto-advances — the UI dispatches Advance ONLY on PASS; FAIL/UNCERTAIN return the step to the working state; the user may decline verification per step (own confirmation completes it, never worded as verified); no code path completes a step without PASS-gated Advance
- [x] **VerifyStepScreen**: capture (SHOW THE RESULT panel naming the expected state) → review → single verify call → result view with exact spec wordings + WHAT FIXLENS SAW evidence panel + expected-state reminder; retry with a FRESH capture for UNCERTAIN; honest per-outcome buttons
- [x] **Camera bug fixed**: PreviewSession.stop() now unbinds only its own use cases (unbindAll() during screen transitions killed the next screen's fresh binding — caught by E2E)
- [x] **Tests**: 26 new backend, 12 new Android; live verify matrix (8 real calls covering PASS/FAIL/UNCERTAIN/quality-gate/400s) + full E2E on the emulator

### Phase 4 (prior sessions)
- [x] **Backend planning schemas** (`schemas.py`): `RepairStep` (number/title/action/instruction/target_component/tool_known/tool/tool_note/warning/expected_state/confirmation_required; schema law: tool_known⇄tool consistency, blanks→None), `RepairPlan`, `AssemblyPlan` (order_confident=false structurally requires steps=[] AND requested_view), `PlanStatus`/`PlanResponse` (blocked statuses can never carry a plan — model-validator enforced), `AssemblyStatus`/`AssemblyResponse` (HIGH blocks discard the plan), shared `is_no_visible_issue_text` helper
- [x] **Repair-plan + assembly JSON contracts** (`prompts.py`): strict shapes — smallest safe sequence (2–6 steps), target must exist in the diagnosis, tool_known only when the fastener clearly determines the tool (else honest tool_note fallback), step-specific warnings or null, visual expected_state, never manipulate high-risk hazards; assembly: order_confident only when evidence determines the sequence, else empty steps + exactly ONE requested_view, never guess order
- [x] **Plan parsers** (`providers/base.py`): `parse_plan_payload`/`parse_assembly_payload` — step numbers are POSITIONAL BY CONSTRUCTION (model numbering untrusted), alias fields accepted (detailed_instruction/expected_visual_state/target), tool named without flag is promoted, empty steps/no parts rejected, confident-flag-without-steps downgraded to view request
- [x] **Provider planning methods**: Gemini + OpenRouter `plan()` (text-only, consumes serialized validated diagnosis) and `plan_assembly()` (parts photo) via a shared `_generate` transport with candidate-model fallback; `plan_with_fallback`/`assembly_with_fallback` in selector with identical bounded-fallback rules as diagnosis; `apply_assembly_safety_policy` deterministic gate
- [x] **POST /api/v1/plan** — SAFETY HAPPENS BEFORE GENERATION: deterministic gate runs first; HIGH/SAFETY_STOP → `BLOCKED_HIGH_RISK` (the model is never called — no instructions ever generated), needs_better_view/ASK_FOR_VIEW → `BLOCKED_BETTER_VIEW`, no visible issue → `BLOCKED_NO_ISSUE`; PLAN_READY carries `requires_acknowledgement=true` exactly when final level is MEDIUM
- [x] **POST /api/v1/assembly** — parts photo → assembly plan → deterministic gate BEFORE returning; HIGH discards the plan entirely; order_confident=false → `NEEDS_BETTER_VIEW` with zero steps
- [x] **Android wire layer**: RepairStepDto/RepairPlanDto/PlanResponseDto/AssemblyPlanDto/AssemblyResponseDto + status constants; `ApiClient.plan()` (JSON body with full validated diagnosis) and `ApiClient.planAssembly()`
- [x] **RepairEngine** (`repair/RepairEngine.kt`) — pure Kotlin, no Android/AI/network imports: NOT_STARTED/PLAN_READY → STEP_ACTIVE → WAITING_FOR_USER → READY_FOR_VERIFICATION → STEP_COMPLETE → NEXT_STEP → REPAIR_COMPLETE; Pause/Resume/Cancel/SkipStep (difficult-step path marks BLOCKED_BY_USER, honestly counted); every intent legal-state guarded (invalid = no-op, never crash); **Phase 5 seam**: `RepairStepState` + `VerificationResult` (NotVerified now; Verified/FailedMismatch/Inconclusive later) and a distinct READY_FOR_VERIFICATION phase the UI auto-advances; MEDIUM acknowledgement enforced in the ENGINE (Start refused while unacknowledged); completion wording controlled: "All guided steps completed." — never "Repair confirmed"
- [x] **GuidedRepairScreen** — plan generated ONCE per session on explicit Start Fix (loading/error states always offer Retry); blocked-plan renderer (STOP / I NEED A BETTER VIEW / NOTHING TO FIX — never steps); MEDIUM BEFORE YOU START acknowledgement gate; step UI per spec: STEP x OF y, title, action, DO THIS, TOOL (named only when tool_known — else honest "use the appropriate screwdriver" fallback), CAREFUL warning, WHAT YOU SHOULD SEE AFTER, Why? explainer (target + expected state); buttons only: I've Done This / Show Me / Why? / I can't do this (skip dialog); exit dialog offers Pause/Cancel; completion view states FixLens does not verify the result yet
- [x] **ShowMeCameraScreen** — CameraX preview + pulsing amber target ring + instruction panel; explicit honest wording ("The circle shows roughly where to look"); no per-frame AI, opens only on explicit tap
- [x] **AssemblyModeScreen** — parts capture flow (own review, fresh-capture retry) → ASSEMBLY PLAN screen (parts list •/○, numbered steps with warnings/expected states) or "I CAN'T DETERMINE THE ORDER YET" + requested view + Try Again; STOP card for blocked HIGH
- [x] **Wiring**: Start Fix on the diagnosis result (GUIDE/LIMITED_GUIDE only — SAFETY_STOP variant unchanged, offers no fix path); GUIDED_REPAIR + ASSEMBLY routes; Home "Assemble Something" entry
- [x] **Tests**: 36 new backend (schema invariants incl. blocked-cannot-carry-plan, parsers, corpus escalation, gates-before-generation asserting the fake provider was NOT called, fallback/503/502 mapping) + 15 new Android (engine lifecycle/ack-gate/skip/pause/cancel/no-op guards; MockWebServer plan/assembly parsing, blocked statuses, ack flag, error detail mapping)

### Phase 3 (prior session)
- [x] Schema v3: `components` list (name + OBSERVED/INFERRED kind + visible status), `observations` (renamed from visual_evidence per §9 terminology), `confidence_band` (HIGH/MEDIUM/LOW, deterministic mapping from numeric score: ≥0.75/≥0.45/else)
- [x] Conditional likely-causes rule: empty causes allowed ONLY for "no visible issue" (model must not fabricate causes for healthy objects); diagnosed issue requires ≥1 cause — discovered via live testing (bicycle rack returned honest empty causes and previously 502'd)
- [x] Prompt upgrade: component rules (never invent, INFERRED only for certainly-present-but-hidden parts, status for abnormal condition), user-context rules (symptom is the user's report, NOT visual evidence; request the view that confirms/refutes claims), better-view must name camera move AND part to show
- [x] Image quality gate (spec §14): rejects pitch-black, blown-out, flat/lens-blocked (luma stddev), and too-small (<160 px short side) frames with specific friendly messages — before any provider call or quota spend
- [x] Android UI rebuild to spec §10 layout: FIXLENS → I SEE (object + components, "•" observed / "○" inferred) → POSSIBLE ISSUE (+likely causes) → WHAT I FOUND (observations with kinds) → CONFIDENCE (controlled band + honesty note, no fake percentages) → SAFETY; SAFETY STOP variant (STOP card, no guided-fix action, "View What I Detected"/"Done"); I NEED A BETTER VIEW variant (instruction + Try Again → fresh capture); confidence/cause percentages removed from UI
- [x] User context plumbing verified live: chair photo + "The chair won't rotate." → model correctly treated the illustration image as non-photographic, did NOT confirm the user's claim as fact, and returned ASK_FOR_VIEW asking for a real photo of the underside
- [x] Better-view "Try Again" returns to the live camera for a fresh intentional capture (stored capture deliberately not reused, spec §13)

### Phase 2 (prior session)
- [x] Provider abstraction (Gemini primary / OpenRouter fallback, config-driven, candidate model lists), strict Pydantic validation, deterministic safety policy, image intake, `/api/v1/diagnose`, Android integration, 30 backend + 20 Android tests

### Phase 1 (prior sessions)
- [x] Android foundation (Compose navigation, CameraX, permissions, My Repairs), FastAPI `/health`, adb-reverse connectivity

---

# Phase 4 record (prior session)

## Files Added (Phase 4)
- `backend/tests/test_phase4_planning.py` (36 tests: schemas, parsers, gates, endpoints)
- `android/app/src/main/java/com/fixlens/app/repair/RepairEngine.kt` (state machine + Phase 5 boundary types)
- `android/app/src/main/java/com/fixlens/app/ui/screens/GuidedRepairScreen.kt`
- `android/app/src/main/java/com/fixlens/app/ui/screens/ShowMeCameraScreen.kt`
- `android/app/src/main/java/com/fixlens/app/ui/screens/AssemblyModeScreen.kt`
- `android/app/src/test/java/com/fixlens/app/RepairEngineTest.kt` (9 tests)
- `android/app/src/test/java/com/fixlens/app/PlanResponseTest.kt` (6 tests)

## Files Modified (Phase 4)
- `backend/app/schemas.py` (+RepairStep/RepairPlan/AssemblyPlan/PlanRequest/PlanResponse/AssemblyResponse/status enums, is_no_visible_issue_text)
- `backend/app/prompts.py` (+REPAIR_PLAN_JSON_CONTRACT, +ASSEMBLY_JSON_CONTRACT; diagnosis text unchanged)
- `backend/app/providers/base.py` (+parse_plan_payload, parse_assembly_payload, _step_from_payload)
- `backend/app/providers/gemini.py`, `backend/app/providers/openrouter.py` (+plan/plan_assembly via shared _generate core)
- `backend/app/providers/selector.py` (+plan_with_fallback, assembly_with_fallback, _capabilities)
- `backend/app/safety.py` (+apply_assembly_safety_policy)
- `backend/app/main.py` (+POST /api/v1/plan, +POST /api/v1/assembly; version 0.4.0; gates BEFORE generation)
- `android/.../network/DiagnosisModels.kt` (+plan/assembly DTOs)
- `android/.../network/ApiClient.kt` (+plan(), +planAssembly())
- `android/.../ui/screens/DiagnosisResultScreen.kt` (+Start Fix for GUIDE/LIMITED_GUIDE; SAFETY_STOP variant unchanged)
- `android/.../ui/screens/PhotoCaptureScreen.kt` (+onStartFix plumbing)
- `android/.../ui/screens/HomeScreen.kt` (+Assemble Something entry)
- `android/.../MainActivity.kt` (+GUIDED_REPAIR/ASSEMBLY routes, pendingDiagnosis hoist)
- `README.md`, `FIXLENS_PROGRESS.md`

## Repair Planning (Phase 4)
PASS — verified live and E2E:
- **Live PLAN_READY**: valid television diagnosis (fixture modeled on the Phase 3 E2E result) → `POST /api/v1/plan` → real Gemini planner → 3 structured steps in 9.9 s: 1) Remove the loose cable cover (tool-unknown note: "No tools are required…", warning about metal tools) → 2) Reseat the video signal cable (warning: power off first) → 3) Power cycle (expected: "display shows a normal image instead of the checkerboard pattern") — every step carried target/action/warning/expected_state (raw JSON in /tmp/p4_plan_ready.json)
- **Live blocked matrix**: all 6 saved Phase 3 diagnoses → wiring→BLOCKED_HIGH_RISK, corpus-escalated bicycle rack→BLOCKED_HIGH_RISK, chair/ambiguous→BLOCKED_BETTER_VIEW, shelving/hardware→BLOCKED_NO_ISSUE — every blocked response returned plan=null (log /tmp/p4_live2.log)
- **Live honesty**: stuck-chair and missing-bolt context attempts → model honestly returned needs_better_view (illustration / visible empty mounting hole) → BLOCKED_BETTER_VIEW, no plan fabricated
- **E2E**: Start Fix → "Preparing your repair plan…" → Step 1 of 3 rendered from the real plan

## Assembly (Phase 4)
PASS — live `/api/v1/assembly` on disassembled-parts photo: metal upright/frame + hex socket bolt identified, order_confident=false, steps=[], requested_view="Show the full structure of the shelving unit to identify how the uprights and cross-member…" (order never guessed). E2E: Assemble Something → capture → Get assembly steps → order-uncertain screen with parts list and Try Again, no steps rendered (/tmp/e2e4c.log).

## Camera Guidance (Phase 4)
PASS (emulator) — Show Me overlay verified E2E: live preview + pulsing target ring + instruction panel + honest approximation wording; opens only on explicit tap; zero AI calls per frame. (Physical device NOT TESTED.)

## Safety Blocking (Phase 4)
PASS — HIGH never receives instructions through any tested path: model-level HIGH (wiring) blocked before generation with zero model calls (fake-provider tests assert call count 0); corpus escalation holds even when the model under-calls risk; blocked responses cannot carry a plan (schema-enforced, live-verified); SAFETY_STOP diagnosis screen offers no Start Fix; MEDIUM produces requires_acknowledgement=true and the engine refuses to start steps before acceptance (unit-tested).

## Physical Device (Phase 4)
NOT TESTED (physical) — no physical phone connected this session (`adb devices` shows only emulator-5554). All device verification ran on the Medium_Phone_API_35 emulator as in Phase 3. Runbook unchanged (docs/DEVICE_SETUP.md).

## Tests Run (Phase 4)
- `backend: .venv/bin/python -m pytest tests/ -q` — PASS (83/83 = 47 prior + 36 new)
- `android: :app:testDebugUnitTest` — PASS (35/35 = 20 prior + 15 new)
- `android: :app:assembleDebug` — PASS; `:app:lintDebug` — PASS
- Live planner scenarios (real Gemini through the real endpoints): T1 stuck chair+context → honest ASK_FOR_VIEW → BLOCKED_BETTER_VIEW; T2 furniture+missing-bolt → ASK_FOR_VIEW → BLOCKED_BETTER_VIEW; T3 television fixture diagnosis → PLAN_READY (3 steps); T4 assembly → NEEDS_BETTER_VIEW; T5 wiring → BLOCKED_HIGH_RISK; T6 blocked matrix (all 6 saved Phase 3 diagnoses) → plan=null everywhere; several 503 quota events handled honestly (retry)
- **E2E (emulator, /tmp/e2e4b.log + /tmp/e2e4c.log)**: home shows Assemble Something → Scan a Photo → Use image → diagnosis → Start Fix → Step 1 of 3 (title/action/DO THIS/TOOL — NOT SURE WHICH/CAREFUL/WHAT YOU SHOULD SEE AFTER/I've Done This/Show Me/Why?/I can't do this) → Show Me overlay OK → Back → I've Done This → Step 2 of 3 OK → I've Done This ×2 → "All guided steps completed." (COMPLETION: OK; forbidden "confirmed" wording absent) → assembly flow → NEEDS_VIEW screen with parts list

## Known Issues (Phase 4)
- Free-tier quotas: Gemini daily cap exhausted intermittently during live testing (several 503s, handled with honest error + retry); OpenRouter free pools frequently 429. Demo Mode remains essential for the final video.
- Safety corpus matches phrases, not meaning: a NEGATED hazard phrase ("no exposed wiring") still escalates to HIGH — observed live (fixture blocked as BLOCKED_HIGH_RISK); by-design conservative (false blocks safer than missed hazards).
- Model stochasticity: the same emulator scene can yield a full diagnosis one run and a better-view request the next; both downstream branches verified correct.
- Repair sessions are in-memory per screen entry: Pause/Resume works within the session, but sessions are not persisted across app restarts yet (`RepairSessionRecord` type exists for that follow-up).
- Background servers die between agent tool calls in this environment (self-contained scripts used); irrelevant to real usage.
- LocalLifecycleOwner deprecation warning (cosmetic, pre-existing).
- Physical-device verification still pending (user must connect a phone).

## Blockers (Phase 4)
- None software-side. Physical-phone verification awaits a connected device.

## Architecture Decisions (Phase 4)
- Safety happens BEFORE planning: the /api/v1/plan gate runs before any model call and blocked statuses structurally cannot carry steps (Pydantic model-validator) — there is no code path from BLOCKED_* to instructions.
- The plan is generated ONCE per session on explicit user action (Start Fix); no AI calls per frame, per step, or on camera movement (spec §17.6 §22).
- RepairEngine is pure Kotlin (no Android/AI imports); plans enter as provider-agnostic RepairSessionPlan; DTO→session mapping lives in the UI layer; the engine cannot call a model by construction.
- Step numbers are positional by construction — model numbering is untrusted and never rendered.
- Phase 5 seam: READY_FOR_VERIFICATION engine phase + RepairStepState/VerificationResult types; Phase 4 auto-advances that phase on user confirmation only, so Phase 5 can intercept it without touching the state machine.
- MEDIUM acknowledgement is enforced in the engine (Start is refused while unacknowledged), not merely hidden in UI.
- Tool honesty is schema law (tool_known⇄tool consistency) with a UI-level honest fallback; assembly order honesty is structural (order_confident=false ⇒ steps=[] + requested_view; confident-flag-without-steps downgraded by the parser).
- Reusing the Phase 3 deterministic gate for planning means HIGH risk has exactly one behavior across the product: professional referral, no instructions.

## Not Yet Implemented (Phase 5+)
- Visual verification of completed steps (compare expected_state against a fresh capture) and final repair confirmation
- Session persistence across app restarts (repair history for guided sessions)
- RevenueCat: products, entitlement, paywall, repair credits
- Demo Mode, post-repair analytics, Phase 6+ functionality

## Next Recommended Task (as of Phase 4)
Phase 5: visual verification at the existing READY_FOR_VERIFICATION seam — capture one user-triggered verification frame when the user confirms a step, send it with the step's expected_state to a new backend verify endpoint (reusing the quality gate + provider fallback), map the result to VerificationResult (Verified / FailedMismatch / Inconclusive) and drive RepairStepState from it — no changes to the state machine itself should be needed.

---

# Phase 3 record (prior session)

## Files Added (Phase 3)
- `backend/tests/test_phase3_schema_and_quality.py` (components, confidence bands, quality gate, empty-causes policy — 12 tests)

## Files Modified (Phase 3)
- `backend/app/schemas.py` (+ComponentItem, +ConfidenceBand, components/observations/confidence_band fields, conditional causes validator)
- `backend/app/providers/base.py` (component normalization, band derivation, components_visible alias)
- `backend/app/prompts.py` (component rules, user-context rules, better-view specificity)
- `backend/app/imaging.py` (quality gate: luma mean/stddev + min dimension)
- `backend/app/safety.py` (corpus now includes component names/statuses)
- `backend/tests/test_schemas_and_safety.py`, `backend/tests/test_diagnose_endpoint.py` (Phase 3 field renames + noise-based test images for the quality gate)
- `android/.../network/DiagnosisModels.kt` (+ComponentDto, observations, confidenceBand)
- `android/.../ui/screens/DiagnosisResultScreen.kt` (full §10 layout rebuild, safety-stop + better-view variants)
- `android/.../ui/screens/PhotoCaptureScreen.kt` (better-view retry → fresh capture)
- `android/.../test/.../DiagnoseResponseTest.kt` (Phase 3 fields)
- `README.md`, `FIXLENS_PROGRESS.md`

## AI Provider
- **Primary: Gemini** (`gemini-flash-latest` via candidate list) — WORKING, served all live tests this session
- **Fallback: OpenRouter** — implemented with per-model candidate fallback; free pools were 429-saturated during this session (live-verified once in Phase 2)

## Diagnosis
PASS — verified live with real images through the real pipeline:
- **TEST 1 (chair + context "The chair won't rotate.")**: `swivel chair` [furniture]; components: seat/backrest/swivel mechanism/base/casters with statuses; model correctly refused to treat the illustration as proof of the user's claim → ASK_FOR_VIEW with a specific underside instruction. Confidence band LOW→ASK_FOR_VIEW path exercised. (An earlier run on a photographic chair image returned 0.95/HIGH-band.)
- **TEST 2 (screw/hardware + context)**: `hardware store display shelving`; components with OBSERVED statuses; "no visible issue" honestly reported; band HIGH; GUIDE.
- **TEST 3 (disassembled parts)**: `metal shelving unit`; components metal frame + hex bolt (OBSERVED/intact); "no visible issue"; GUIDE.
- **TEST 4 (wiring)**: `three-gang light switch electrical box` [electrical]; components incl. "electrical wiring and wire connectors (OBSERVED, exposed)" and "wall plate (INFERRED, missing)"; issue: switches hanging loose with exposed wiring; conf 0.95 HIGH band; safety HIGH → **SAFETY_STOP** + electrician referral.
- **TEST 5 (ambiguous)**: `unknown`; conf 0.00 → LOW band; **needs_better_view=true** + specific re-shoot instruction; ASK_FOR_VIEW.
- **TEST 6 (unrelated object, bicycle rack)**: honest `bicycle rack` identification, "no visible issue", components listed, HIGH band, GUIDE — not forced into a demo scenario. This test exposed and fixed the empty-causes 502 (model honestly returns no causes for healthy objects).

## Safety
PASS — deterministic layer re-verified: live wiring → HIGH → SAFETY_STOP with professional referral rendered; offline policy tests (escalation-only, gas→HIGH, better-view override) all pass; safety corpus now also scans component names/statuses.

## Photo Mode
PASS (emulator) — capture → preview → Analyze → loading → real diagnosis → Phase 3 result screen, verified end-to-end on-device (see E2E below); quality gate rejects unusable images with specific messages (unit-tested).

## Live Camera
PASS (emulator) — same pipeline with mode=LIVE; explicit user-triggered frame only, no continuous analysis; better-view "Try Again" returns to the live camera for a fresh intentional frame.

## Physical Device
NOT TESTED (physical) — no physical phone connected this session (`adb devices` shows only emulator-5554). **End-to-end verified on the API 35 emulator instead**: app camera → upload via adb reverse → Gemini → Phase 3 result screen (see E2E below). Physical-phone runbook unchanged in docs/DEVICE_SETUP.md.

## Tests Run
- `backend: python -m pytest tests/ -q` — PASS (47/47: 30 prior + 17 Phase 3)
- `android: :app:testDebugUnitTest` — PASS (20/20)
- `android: :app:lintDebug` — PASS
- `android: :app:assembleDebug` — PASS
- Live AI matrix — 6 scenarios, all PASS (results quoted above; raw JSON in /tmp/p3_*.json)
- **E2E (emulator)**: app camera → Use image → Diagnosis screen rendered: "FIXLENS / I SEE television / Components: • display panel — abnormal display output, • stand — intact, ○ internal power supply / POSSIBLE ISSUE: checkerboard pattern... firmware or internal signal processing error / Likely causes / WHAT I FOUND: [OBSERVED]... / CONFIDENCE: High confidence / SAFETY / Try Again, Done" — matches spec §10 layout exactly (raw UI dump in /tmp/e2e2.log)

## Known Issues
- Free-tier quotas: Gemini daily cap exhausts under heavy testing; OpenRouter free pools frequently 429 — both handled honestly (friendly error + retry). Demo Mode remains essential for the final video.
- Background servers die between agent tool calls in this environment (emulator/backend verification is run as self-contained scripts); irrelevant to real usage.
- LocalLifecycleOwner deprecation warning (cosmetic).
- Physical-device verification still pending (user must connect a phone).

## Blockers
- None software-side. Physical-phone verification awaits a connected device.

## Architecture Decisions
- Confidence is a controlled band derived by deterministic mapping from the model score; UI never shows fake-precise percentages (spec §4).
- Components carry an explicit OBSERVED/INFERRED kind; UI renders "•" vs "○" so users can distinguish seen parts from assumed parts.
- Empty likely-causes permitted only for "no visible issue" results — encodes "never fabricate" as schema law, discovered via live TEST 6.
- Image quality gate runs before the provider call: quota is never spent on unusable frames, and each rejection tells the user exactly what to fix.
- User context is prompt guidance only — the model is instructed (and live-verified) to treat it as a report, not evidence.
- Better-view "Try Again" always captures a fresh frame rather than resending the rejected one.

## API Quota/Cost Notes
- Phase 3 live testing consumed ~15 Gemini free-tier calls; preprocessing (~60% byte reduction) and the quality gate keep per-request cost bounded.
- No caching of results in this phase (identical-image dedup considered; rejected to avoid stale diagnoses masking product behavior).

## Not Yet Implemented (Phase 4+)
- Guided repair plan + repair state machine (spec §11), step-by-step guidance UI, visual-target overlay, camera verification flow, RevenueCat (products/entitlement/paywall/credits), Demo Mode, fresh-clone verification, final demo video.

## Next Recommended Task
Phase 4 (spec §17.6): repair-plan endpoint (`/api/v1/plan`) consuming the validated diagnosis, the repair state machine (GUIDE/LIMITED_GUIDE/SAFETY_STOP/ASK_FOR_VIEW → USER_ACTION → VERIFY), and step-by-step guidance screens — the safety gate already guarantees HIGH never receives instructions.
