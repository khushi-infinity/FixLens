# FixLens, RevenueCat Shipaton 2026 Submission Content

Repo: https://github.com/khushi-infinity/FixLens · Android (Kotlin + Jetpack Compose) · backend v0.6.0 · app v0.8.0

---

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

### What we learned

- **Constraint breeds honesty.** Because we refuse to render a fix for HIGH-risk objects, we had to build a deterministic safety gate that runs *before* any model call, which taught us the most valuable lesson of the project: the safest code is the code that never calls the model.
- **Vision models are witnesses, not oracles.** Gemini describes what it sees remarkably well, but it will also hallucinate confidently if you let it. We learned to force structured output (components with OBSERVED vs INFERRED kinds), collapse confidence into controlled bands instead of fake percentages, and make "UNCERTAIN" a first-class answer that always carries exactly one instruction for getting a better view.
- **Verification is the hard, valuable part.** Letting a user mark a step "done" is trivial. Making them *prove it with a camera scan*, PASS / INCOMPLETE / UNCERTAIN, never a success claim without visual evidence, is what turns an information app into a repair companion.
- **Monetization can align with honesty.** Gating the *work* (guided repair, assembly, verification) rather than the *safety information* means free users still get hazard warnings. RevenueCat made it painless to mix a subscription with one-time repair packs.
- **A visual identity is a product decision.** The watercolor workshop-journal restyle wasn't decoration: paper backgrounds keep camera overlays legible, serif headings signal "manual, not chatbot," and hand-drawn targets read as *annotations made for you*, not machine HUD.

### How we built it

- **Android app (Kotlin + Jetpack Compose, single module):** camera capture (photo + live preview via CameraX), a navigation flow that runs HOME → capture → diagnosis → guided repair → per-step verification → completion, RevenueCat billing (3 free scans/month, `fixlens_pro` entitlement, monthly/annual subscriptions, one-time repair packs), and a fully scripted, honestly-badged Demo Mode for risk-free demos.
- **Backend (FastAPI, Python):** endpoints for `/diagnose`, `/plan`, `/assembly`, `/verify`; a provider layer over Gemini (OpenRouter as config-driven fallback); Pydantic-validated structured responses; and a deterministic policy layer that enforces the safety gate, HIGH risk returns `BLOCKED_HIGH_RISK` with zero model calls, MEDIUM requires explicit acknowledgement, and verification never fabricates a PASS.
- **Design system:** warm paper background, charcoal ink, terracotta/sage/lavender watercolor washes, serif editorial headings over a humanist sans body, sketchy Canvas-drawn target overlays and paper-grain textures, an "illustrated workshop journal" rendered entirely in Compose.
- **Quality:** 109 backend tests, 72 Android unit tests, lint-clean build, and full emulator E2E runs walking the real journey, including live Gemini diagnoses and the safety stop blocking a HIGH-risk wiring scenario before any generation.

### Challenges

- **Making the safety gate genuinely zero-shot-call.** It's easy to "check safety" after asking the model. We restructured the pipeline so risk classification and the block decision happen deterministically server-side; we proved it in tests and live logs (model invocation count = 0 on blocked requests).
- **Structured vision on a free tier.** Free-tier vision calls are slow (46-150 s) and flaky (503/429). We designed every screen to communicate that honestly: explicit "this may take up to two minutes" copy, retry affordances everywhere, and an Analyzing state that never spins silently.
- **Verification without over-claiming.** Teaching the verify endpoint to return INCOMPLETE or UNCERTAIN instead of a hopeful PASS took several iterations of prompt plus post-validation, and the UX needed to treat UNCERTAIN as a normal, calm outcome with exactly one better-view instruction.
- **Designing trust.** The hardest UI problem was making an AI's uncertainty feel trustworthy. Controlled confidence bands, visible reasoning ("what I found"), and a paper-and-ink aesthetic that feels hand-made turned out to reinforce each other.

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
> FixLens monetizes the *work*, never the *warning*. Everyone gets the diagnosis, including the safety assessment, a hazard should never be behind a paywall. What's gated is doing something about it: guided step-by-step repair, Show Me visual targeting, per-step camera verification, and Assembly mode. The structure: **3 free scans per month**, then **FixLens Pro at $7.99/month or $59.99/year (~37% savings)**, plus **one-time Repair Packs ($3.99 / $6.99)** for users with a single broken thing and no interest in a subscription, repair intent is often episodic, and packs meet users exactly at that moment of need. We chose this hybrid because it mirrors how people actually repair: some fix things constantly (subscription fits), most fix things occasionally (pack fits), and nobody should pay to find out something is dangerous. All entitlements run through RevenueCat (`fixlens_pro`) with real purchase state and Restore, and the free tier itself is honest: scan counts are real, the paywall explains itself, and Demo Mode lets anyone experience the full journey without a purchase or a network risk. Judges can see the whole flow in the video: paywall at ~1:35, Pro unlock at ~1:45.

### For the RevenueCat Peace Prize: benefit to individuals, community, or society
> FixLens is built around one safety belief: knowing something is dangerous should always be free. Its deterministic safety gate blocks HIGH-risk scenarios (exposed wiring, gas, structural damage) *before any AI generation happens*, the app refuses and refers to a licensed professional instead of coaching a risky DIY fix. That protects exactly the people DIY advice usually fails: renters in old buildings, first-time homeowners, students with a toolbox and a tutorial. Beyond safety, FixLens shifts behavior from *replace* to *repair*: a stuck chair hinge or a wobbly shelf doesn't become landfill because no one knew where the screw was. Each successful repair is a small environmental win and a small competence win, the app is deliberately designed (calm paper aesthetic, honest confidence language, verification instead of cheerleading) to make people feel capable rather than dependent, and its Demo Mode exists so that anyone, including judges without a broken chair on hand, can experience the full journey safely. Longer-term, the same camera-first, safety-first pattern extends to assistive repair for users with limited mobility or vision, the app does the looking and the reading so the user can do the fixing.

### For the RevenueCat Design Award: distinctive design elements
> FixLens looks like an illustrated workshop field guide, not an AI app, because the visual identity *is* the product thesis that repair knowledge is human, tactile, and sequential. Judges should look for: (1) **the paper-and-ink system**, warm paper background, cream cards, charcoal text, thin rule lines, serif editorial headings over a humanist sans body, and numbered field-note sections ("01 / THE OBJECT") that make a diagnosis read like a manual, not a chat; (2) **hand-drawn camera annotations**, a sketchy ink contour and pencilled arrow drawn live over the camera view (Show Me), so the AI marks the physical world the way an instructor's pencil would, with paper bands keeping controls legible on any scene; (3) **watercolor washes and irregular geometry**, sage/lavender/terracotta tints, uneven card corners, paper-grain texture, and a custom watercolor icon; (4) **multisensory guidance**, every step speaks itself via on-device text to speech (mute toggle included), shows an animated HOW IT MOVES diagram whose hand-drawn arrow matches the action verb (move, rotate, press, lift, slide, fasten, place, apply), and haptics punctuate confirmation, PASS, completion, and the safety stop; (5) **honesty as a design material**, the Demo Mode banner is typeset like a field-note stamp, confidence is a controlled band rather than a fake percentage, the safety stop is a quiet red-lettered card, and the pricing page shows the real catalog (a computed savings badge from live RevenueCat prices when configured, a clearly-labeled planned-catalog sketch when not); (6) **calm motion**, short rise-and-fade entrances and a pulsing target ring that guides without shouting. Best screens to evaluate: Home (composition), Diagnosis (editorial density), Guided Repair (instructional design, voice, and diagrams), Show Me camera (annotation), Pricing (monetization design).

### For the Catvertising Award
*(leave blank, FixLens does not use RevenueCat Ads.)*

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
