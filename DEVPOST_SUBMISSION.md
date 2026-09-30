# FixLens, RevenueCat Shipaton 2026 Submission Content

Repo: https://github.com/khushi-infinity/FixLens · Android (Kotlin + Jetpack Compose) · backend v0.6.0 · app v0.8.0

---

## Built with (tags, paste as comma-separated list)

```
Kotlin, Jetpack Compose, Material 3, CameraX, RevenueCat, Google Gemini, FastAPI, Python, OpenRouter, OkHttp, kotlinx.serialization, Android TextToSpeech, Pydantic, Pillow, Uvicorn, REST API, Gradle, pytest
```

Ordering rationale: languages and UI framework first (judges scan left to right), then the monetization and AI hooks RevenueCat organizers look for, then backend stack, then supporting libraries. All entries are real and verified in `android/gradle/libs.versions.toml` and `backend/requirements.txt`.

## Elevator pitch

FixLens is a camera-first AI repair companion. Point your phone at something broken, stuck, or half-assembled and FixLens tells you what it sees, what's likely wrong, and whether it's safe to touch, then walks you through the repair step by step, marks exactly where to look with a hand-drawn target on your live camera view, and verifies each step with a follow-up photo before letting you move on. It's an AI technician in your pocket, drawn like a beautifully illustrated workshop field guide, and it's honest: it never claims success without visual evidence, and it refuses unsafe work outright.

## Tagline (short options)

1. **Point. See. Fix.**, an AI repair guide that looks at the world through your camera. *(recommended)*
2. The illustrated field guide to fixing things.
3. Your pocket repair journal, now it can see.

## Project Story

### Inspiration

Every repair app we've ever used falls into one of two traps: a wall of unrelated forum answers, or a chatbot that confidently invents steps for a machine it has never seen. The moment that inspired FixLens was watching someone hold a wrench, a phone, and a dying office chair all at once, hands busy, eyes on the work, with no free hand to type a question. The phone should do the looking. Cameras are the one sensor everyone carries, and "fixing things" is fundamentally a visual problem: find the part, see what's wrong, know if it's safe.

We also noticed that repair knowledge is visual and *sequential*, not conversational. You don't need a chat buddy; you need a patient instructor standing over your shoulder saying "look here, do this, now show me you did it." That became FixLens's whole shape: **I SEE → POSSIBLE ISSUE → WHAT I FOUND → CONFIDENCE → SAFETY → guided steps → Show Me → verification**, with no chat surface anywhere in the app.

And since the brief is a watercolor field guide: real repair manuals, the kind folded into a box of screws, are illustrated, dense, and quietly beautiful. We wanted an app that felt like that notebook, not like a neon dashboard.

### What it does

Point the camera at something broken, stuck, or half-assembled:

1. **It looks.** A captured photo travels to the FastAPI backend through a quality gate (dark, blank, or tiny images are rejected before any AI quota is spent) and comes back as a structured diagnosis: the object, its components marked OBSERVED or INFERRED, the possible issue, likely causes, a controlled confidence band, and a deterministic safety assessment. Insufficient evidence produces exactly one specific "I NEED A BETTER VIEW" instruction, never a shrug.
2. **It decides if it's safe to help.** A policy layer runs before any generation: HIGH risk (exposed wiring, gas, structural hazards) produces a safety stop with a licensed-professional referral and *no instructions exist at all*; MEDIUM risk requires an explicit acknowledgement before any step is shown.
3. **It teaches, with voice and motion.** *Start Fix* generates a plan once, then each step speaks itself via on-device text to speech (mute toggle included), shows an animated HOW IT MOVES diagram whose hand-drawn arrow matches the action verb (move, rotate, press, lift, slide, fasten, place, apply), and lists TOOL, CAREFUL, and WHAT YOU SHOULD SEE AFTER cards. Show Me overlays a sketchy ink target and pencilled arrow on the live camera.
4. **It checks your work.** Every step can be proven with a fresh camera scan: PASS, INCOMPLETE, or UNCERTAIN with evidence. Only PASS advances a step. The verdict is spoken and felt (haptics). The app never claims success without visual evidence, and completion says plainly: "Double-check the repair yourself before relying on it."
5. **It assembles.** Photograph disassembled parts and get an ordered build plan, or an explicit "I can't determine the order yet" naming the one view that would settle it. Order is never guessed.
6. **It monetizes honestly.** 3 free scans every month; Pro at $9.99/month or $79.99/year (or $99.99 once, forever) unlocks unlimited scans and guided work; one-time Repair Packs ($3.99, $6.99) for single repairs, never renewing. Safety information is never paywalled. Purchases run on RevenueCat with real entitlement state and Restore, and the pricing page shows the full catalog even before a store key is configured.
7. **It demos without risk.** Demo Mode replays three deterministic scripted journeys (stuck office chair, furniture assembly, unsafe electrical wiring) on the real camera, permanently badged "DEMO MODE, scripted result, not live AI".

### How we built it

- **Android app (Kotlin + Jetpack Compose, single module):** CameraX capture (photo + live preview), a navigation flow running HOME → capture → diagnosis → guided repair → per-step verification → completion, RevenueCat billing (`fixlens_pro` entitlement, subscriptions, one-time packs, restore, Test Store support), scripted Demo Mode, and a watercolor design system built from a single palette object.
- **Backend (FastAPI, Python):** `/diagnose`, `/plan`, `/assembly`, `/verify`; a provider layer over Gemini with OpenRouter as a config-driven fallback; strict Pydantic validation so malformed model output can never reach the UI; a deterministic safety policy; structured logging with no keys and no images.
- **Interactive guidance:** on-device Android TextToSpeech (no network, no API key, nothing recorded) drives the voice guide for steps, verdicts, and completion; step diagrams are pure Compose Canvas (verb-matched arrows animated along ghost paths); haptics use view-level feedback constants (no vibration permission needed).
- **Quality:** 109 backend tests and 72 Android unit tests (offline, fake providers injected), a zero-warning lint build, and full emulator E2E runs walking the real journey, including live Gemini diagnoses and the safety stop blocking a HIGH-risk wiring scenario before any generation.

### Challenges we ran into

- **Making the safety gate genuinely zero-model-call.** It's easy to "check safety" after asking the model. We restructured the pipeline so risk classification and the block decision happen deterministically server-side; we proved it in tests and live logs (model invocation count = 0 on blocked requests).
- **Structured vision on a free tier.** Free-tier vision calls are slow (46 to 150 s) and flaky (503/429). We designed every screen to communicate that honestly: explicit "this may take up to two minutes" copy, retry affordances everywhere, and an Analyzing state that never spins silently.
- **Verification without over-claiming.** Teaching the verify endpoint to return INCOMPLETE or UNCERTAIN instead of a hopeful PASS took several iterations of prompt plus post-validation, and the UX needed to treat UNCERTAIN as a normal, calm outcome with exactly one better-view instruction.
- **Making uncertainty feel trustworthy.** The hardest UI problem wasn't drawing the UI, it was earning belief in an AI's doubt. Controlled confidence bands, visible reasoning ("what I found"), honest demo banners, and a paper-and-ink aesthetic that feels hand-made turned out to reinforce each other.
- **Keeping the demo honest while making it cinematic.** Judges want a flawless run; credibility wants disclosure. We resolved it with Demo Mode: identical screens and engine, pre-authored data, and a permanent badge so a beautiful demo can never be mistaken for live AI.

### Accomplishments that we're proud of

- **A safety gate we can prove, not just promise.** HIGH-risk diagnoses are blocked with zero model calls, verified in tests and in live server logs. Nothing to leak exists because nothing is generated.
- **Verification that refuses to flatter.** The verify path returns PASS / INCOMPLETE / UNCERTAIN with evidence, only PASS advances a step, and the completion screen still tells you to double-check the repair yourself.
- **181 automated tests, a zero-warning lint build, and every screen verified by emulator E2E** across nine build phases, each logged in a public build journal.
- **A multisensory repair experience:** steps that speak, draw their own motion diagrams, and confirm with haptics, all on-device and free to run.
- **An honest pricing page:** real RevenueCat offerings with a computed savings badge when configured, and the same catalog shown as a clearly-labeled sketch when not, so monetization is transparent either way.
- **A visual identity nobody else submitted:** a watercolor workshop journal with hand-drawn camera annotations, generated 1024×1024 icon, and zero purple gradients.

### What we learned

- **Constraint breeds honesty.** Because we refuse to render a fix for HIGH-risk objects, the safest code we wrote is the code that never calls the model.
- **Vision models are witnesses, not oracles.** Gemini describes remarkably well but hallucinates confidently if allowed. Structured output, OBSERVED vs INFERRED kinds, controlled confidence bands, and UNCERTAIN as a first-class answer (always carrying exactly one better-view instruction) keep it honest.
- **Verification is the hard, valuable part.** Letting a user mark a step "done" is trivial. Making them prove it with a camera scan is what turns an information app into a repair companion.
- **Monetization can align with honesty.** Gating the *work* rather than the *safety information* means free users still get hazard warnings, and one-time packs meet episodic repair intent without forcing a subscription. RevenueCat made the hybrid painless.
- **A visual identity is a product decision.** The watercolor restyle wasn't decoration: paper backgrounds keep camera overlays legible, serif headings signal "manual, not chatbot", hand-drawn targets read as annotations made *for you*.
- **Multisensory beats pretty.** The voice guide and motion diagrams changed usability testing more than any color change: hands stay on the work while the app reads the step aloud.

### What's next for FixLens

- **Per-step target boxes.** Extend the plan schema so Show Me's ring and the motion diagrams anchor to the actual component's position, not just its name.
- **Before/after pairs in My Repairs.** The workshop journal already records sessions; next it stores the "before" capture and the verification capture side by side.
- **Production billing.** Move from Test Store to Play Billing with the same entitlement, plus a free trial on Pro Annual.
- **Play Store beta.** A closed track for real devices, expanding device verification beyond the emulator.
- **Broader safety corpus and localization.** More hazard phrases, regional plug/wiring standards, and translated voice guidance.
- **Community repair library.** Anonymous, verified repair outcomes aggregated into an open dataset of what actually fixes what.

> **Note for the demo video:** the recording shows a real Test Store purchase flowing through RevenueCat (paywall → purchase sheet → entitlement flip → Pro features unlocking). Test Store purchases are RevenueCat's development-mode sandbox; no real money moves, and the same code path ships to production.

### The watercolor workshop identity

The interface is deliberately anti-"AI app": no purple gradients, no glassmorphism, no chat bubbles. Warm paper (`#F5F1E7`), cream cards, charcoal ink, terracotta primary actions, sage and dusty-lavender washes, thin rule lines, serif display headings, and hand-drawn sketch targets overlaid on the live camera, so the AI feels like an instructor's pencil marking up your view of the world, not a HUD. Even the Demo Mode honesty banner is typeset like a field-note stamp.

---

## Additional info, draft answers for every form field

> Fields marked **[YOU]** need something only you have. Everything else is ready to paste.

### Did you attach a 1024 × 1024 uncropped image of your app icon?
**Yes**, `assets/icon_1024.png` in the repo (1024×1024, uncropped, watercolor lens mark in the app's paper/ink palette).

### Did you attach a screenshot of your app WITHOUT device frames?
**Yes**, `docs/design/home.png`, `diagnosis.png`, `guided-repair.png`, `demo-picker.png` are raw emulator captures with no device frames.

### Was the first version of your app released on a store between Aug 1-Sep 30, 2026?
**No**, FixLens is pre-launch; it has never been published on the App Store, Google Play, or Galaxy Store. (This is why the Grand Prize post-launch-growth field is left blank.)

### Are you an employee of RevenueCat or a Shipaton Sponsor?
**No.**

### What type of app did you build? (select all that apply)
- ☑ **Android**
- ☐ iOS · ☐ Mac

### URL to your published iOS or Mac app on Apple's App Store
*(leave blank, not published)*

### URL to your published Android app on the Google Play Store
*(leave blank, not published)*

### URL to your published app on the Samsung Galaxy Store
*(leave blank, not published)*

### (Next Gen Only) URL to your code repository
```
https://github.com/khushi-infinity/FixLens
```

### (Next Gen Only) Student/academic email address
**[YOU]**, enter the student email you use for Next Gen validation.

### (Next Gen Only) Minor Entrant consent confirmation
**[YOU]**, check only what applies to you (confirm if no Minor Entrant, or that the guardian consent form is completed).

### RevenueCat project ID
**[YOU]**, from RevenueCat Dashboard → Project Settings. (The app expects products `fixlens_monthly`, `fixlens_annual`, `fixlens_repair_pack_5`, `fixlens_repair_pack_10` with entitlement `fixlens_pro`.)

### Promo code for unlocking premium features (optional)
**[YOU]**, if you create a promo code in RevenueCat's Test Store, include it here; otherwise the judges don't need one, **make sure the video demos premium features in the first 3 minutes** (paywall at 1:35 → Pro unlock at 1:45 per the shot list).

### For the Grand Prize Award: how did you grow your app after launch?
*(leave blank, pre-launch; no post-launch numbers exist. Do not fabricate any.)*

### For the Build in Public Award: how did building in public improve your app or process?
> Building FixLens in nine self-contained phases, each ending in honest tests, a runnable build, and a written progress log (`FIXLENS_PROGRESS.md` is effectively the build-in-public diary), changed how we made decisions. Writing each phase *as if someone were reading* forced us to state claims we could verify: test counts, live E2E transcripts, and a final audit that documents its own failures (and fixes) instead of hiding them. The biggest practical win was the audit itself: because every prior phase logged what "done" meant, the Phase 9 audit could check the app against its own promises, and it caught the one real visual inconsistency (a leftover hardcoded color) plus a build warning we then eliminated. Public-style documentation also became our safety conscience: writing down "HIGH risk is blocked before any model call" made us prove it with a zero-invocation log, and writing "Demo Mode is not live AI" made us badge it on every screen. Accountability improved pacing too: a phase that couldn't end with tests passing wasn't allowed to be "done," which killed scope creep more effectively than any deadline.

### For the Build in Public Award: links to public content documenting your progress
**[YOU]**, add the public posts/threads you've made (X/Twitter thread, LinkedIn posts, devlog URLs). If you haven't posted yet, publish a short thread this week linking the phase log: `https://github.com/khushi-infinity/FixLens/blob/main/FIXLENS_PROGRESS.md`, the audit section makes a great capstone post.

### For the HAMM Award: monetization model and why
> FixLens monetizes the *work*, never the *warning*. Everyone gets the diagnosis, including the safety assessment, a hazard should never be behind a paywall. What's gated is doing something about it: guided step-by-step repair, Show Me visual targeting, per-step camera verification, and Assembly mode. The structure: **3 free scans per month**, then **FixLens Pro at $9.99/month or $79.99/year (~33% savings, the same badge the live paywall computes from RevenueCat prices)** or **$99.99 once, forever**, plus **one-time Repair Packs ($3.99 / $6.99)** for users with a single broken thing and no interest in a subscription, repair intent is often episodic, and packs meet users exactly at that moment of need. We chose this hybrid because it mirrors how people actually repair: some fix things constantly (subscription fits), most fix things occasionally (pack fits), and nobody should pay to find out something is dangerous. All entitlements run through RevenueCat (`fixlens_pro`) with real purchase state and Restore, and the free tier itself is honest: scan counts are real, the paywall explains itself, and Demo Mode lets anyone experience the full journey without a purchase or a network risk. Judges can see the whole flow in the video: paywall at ~1:35, Pro unlock at ~1:45.

### For the RevenueCat Peace Prize: benefit to individuals, community, or society
> FixLens is built around one safety belief: knowing something is dangerous should always be free. Its deterministic safety gate blocks HIGH-risk scenarios (exposed wiring, gas, structural damage) *before any AI generation happens*, the app refuses and refers to a licensed professional instead of coaching a risky DIY fix. That protects exactly the people DIY advice usually fails: renters in old buildings, first-time homeowners, students with a toolbox and a tutorial. Beyond safety, FixLens shifts behavior from *replace* to *repair*: a stuck chair hinge or a wobbly shelf doesn't become landfill because no one knew where the screw was. Each successful repair is a small environmental win and a small competence win, the app is deliberately designed (calm paper aesthetic, honest confidence language, verification instead of cheerleading) to make people feel capable rather than dependent, and its Demo Mode exists so that anyone, including judges without a broken chair on hand, can experience the full journey safely. Longer-term, the same camera-first, safety-first pattern extends to assistive repair for users with limited mobility or vision, the app does the looking and the reading so the user can do the fixing.

### For the RevenueCat Design Award: distinctive design elements
> FixLens looks like an illustrated workshop field guide, not an AI app, because the visual identity *is* the product thesis that repair knowledge is human, tactile, and sequential. Judges should look for: (1) **the paper-and-ink system**, warm paper background, cream cards, charcoal text, thin rule lines, serif editorial headings over a humanist sans body, and numbered field-note sections ("01 / THE OBJECT") that make a diagnosis read like a manual, not a chat; (2) **hand-drawn camera annotations**, a sketchy ink contour and pencilled arrow drawn live over the camera view (Show Me), so the AI marks the physical world the way an instructor's pencil would, with paper bands keeping controls legible on any scene; (3) **watercolor washes and irregular geometry**, sage/lavender/terracotta tints, uneven card corners, paper-grain texture, and a custom watercolor icon; (4) **multisensory guidance**, every step speaks itself via on-device text to speech (mute toggle included), shows an animated HOW IT MOVES diagram whose hand-drawn arrow matches the action verb (move, rotate, press, lift, slide, fasten, place, apply), and haptics punctuate confirmation, PASS, completion, and the safety stop; (5) **honesty as a design material**, the Demo Mode banner is typeset like a field-note stamp, confidence is a controlled band rather than a fake percentage, the safety stop is a quiet red-lettered card, and the pricing page shows the real catalog (a computed savings badge from live RevenueCat prices when configured, a clearly-labeled planned-catalog sketch when not); (6) **calm motion**, short rise-and-fade entrances and a pulsing target ring that guides without shouting. Best screens to evaluate: Home (composition), Diagnosis (editorial density), Guided Repair (instructional design, voice, and diagrams), Show Me camera (annotation), Pricing (monetization design).

### For the Catvertising Award
> FixLens runs exactly ONE ad placement, and it exists only because we refused to build the cheap versions. No banners under the diagnosis, no interstitials between repair steps, no ads anywhere near safety information. The placement is an opt-in rewarded ad called **scan_unlock_rewarded**: when a free user has used all 3 monthly scans, the paywall offers a second door besides paying: "Watch a short ad for 1 bonus scan." The user chooses it explicitly; it never auto-plays, never gates diagnosis or the safety assessment (knowing something is dangerous stays free, always), and never appears for Pro subscribers. The reward is granted only on the ad's reward callback and is spent only after the backend accepted the next scan, so a crashed ad can never be farmed for scans and a failed upload never burns one. Integration into the product experience: the button lives on the paywall screen itself, styled as an equal choice next to the plans with an honest caption ("a short ad, you choose when"), and the bonus scan is month-scoped like the free allowance, so the arithmetic stays legible: 3 free + 1 earned, then packs or Pro. How ads work with the rest of monetization: FixLens is a hybrid model, free scans for the first look, one-time Repair Packs for episodic fixers, Pro subscriptions for frequent fixers. Rewarded ads complete the ladder: they monetize the free user at their exact moment of need WITHOUT forcing a subscription decision, and because we integrated through RevenueCat's AdMob adapter (loadAndTrack) instead of calling AdMob directly, ad impression revenue lands in the same RevenueCat dashboard as subscription revenue, one unified LTV per user instead of two disconnected numbers. That unified picture is the whole strategic point: we can see whether an ad-watching free user eventually converts to a pack or Pro, and tune the ladder accordingly. Implementation quality: the ad loads through the RevenueCat ad tracker, shows through one wrapper with exactly-once settle semantics, initializes the Google Mobile Ads SDK lazily and only when the placement is reachable, and runs on Google's official test ad unit in development so no revenue is generated from our own testing. We would rather have one placement judges can defend than five they cannot.

### For the Best Game Award
*(leave blank, not a game.)*

### Which Influencer Award category are you entering?
*(leave blank, not entering.)*

### For the Influencer Award: description
*(leave blank.)*

### For the Ship Kotlin Everywhere Award
*(leave blank, FixLens is pure Android Kotlin + Jetpack Compose, not Kotlin Multiplatform.)*

### For the Most Viral App Award: Noise account email
*(leave blank, not entering.)*

### For the Most Viral App Award: Noise description
*(leave blank.)*

### For the Best App for Galaxy Award
*(leave blank, no Galaxy Store listing or Samsung-specific optimization.)*

### For the Idea to Income Award: Replit URL + username
*(leave blank, not built on Replit.)*

### For the Idea to Income Award: Replit description
*(leave blank.)*

### For the Keep Them Coming Back Award: OneSignal App ID
*(leave blank, no OneSignal integration.)*

### For the Keep Them Coming Back Award: OneSignal description
*(leave blank.)*

### For The Growth Loop Award (Layers)
*(leave blank, Layers SDK not installed.)*

### For the Funnel Vision Award: RevenueCat Funnel URL
*(leave blank, no web funnel.)*

### For the Funnel Vision Award: Stripe Project ID
*(leave blank.)*

### For the Funnel Vision Award: funnel description
*(leave blank.)*

### Additional notes for the judges
> FixLens is pre-launch and runs against a live Gemini backend, so the **Demo Mode** (on Home: "Curious? Try a guided demo") shows the complete product journey, diagnosis, guided steps, camera verification, completion, with pre-authored results, on the real camera, honestly badged "DEMO MODE, scripted result, not live AI" on every screen. It needs no setup and no network. If judges want to try a live scan, the README's quick-start runs the FastAPI backend locally (a Gemini key in `backend/.env`) and points the app at it; on free-tier vision models a scan can take up to two minutes, which the app communicates honestly. The safety story is the heart of the project: a HIGH-risk diagnosis is blocked *before any model call* (verified with server logs), Demo Mode never touches the network, and verification never claims success without visual evidence. RevenueCat billing runs against a Test Store key in the demo video; the products are `fixlens_monthly`, `fixlens_annual`, `fixlens_repair_pack_5`, `fixlens_repair_pack_10`, entitlement `fixlens_pro`. 1024×1024 icon: `assets/icon_1024.png`; frameless screenshots: `docs/design/`. Repo: https://github.com/khushi-infinity/FixLens

### Interested in the RevenueCat Growth Fund?
**Yes**, FixLens is exactly the kind of early-stage, monetization-enabled app the fund is for; the next milestones are a Test Store → production RevenueCat setup, Play Store beta, and short-form demo content for the repair/DIY community.

---

## Video shot list (spec §19, for the < 2 min Next Gen video)

| Time | Shot |
|------|------|
| 0:03 | Open app, home, paper/ink identity |
| 0:08 | Live camera recognition |
| 0:15 | Diagnosis, I SEE / ISSUE / FOUND / CONFIDENCE / SAFETY |
| 0:25 | Show Me, hand-drawn target on camera |
| 0:35 | Guided repair steps |
| 0:45 | Camera verification, PASS |
| 0:52 | Assembly mode |
| 1:20 | Electrical safety stop (HIGH risk blocked) |
| 1:35 | Paywall |
| 1:45 | Pro unlock |
| 1:50 | Architecture slide |
| 1:57 | Logo |
