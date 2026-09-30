# FixLens demo video guide (for beginners)

This is a complete, hold-your-hand guide for recording the Devpost / RevenueCat
Shipaton demo video. No video experience needed. Follow it top to bottom.

---

## 1. The rules you are recording against

- The Shipaton "Next Gen" video must be **under 2 minutes**.
- Judges must see **premium features in the first 3 minutes** (RevenueCat
  rule), so the paywall and a purchase appear around 1:30 in the script below.
- One video covers both requirements: the purchase happens inside the 2
  minutes, so both are satisfied at once.

## 2. What to show (the judge checklist)

Judges reward working product, honesty, and monetization. Your video must
show, in order:

| # | What | Why judges care |
|---|------|-----------------|
| 1 | Home screen with the "FIXLENS PRO" badge | Real RevenueCat state, not a mock |
| 2 | A real scan of a real object | The camera-first core loop |
| 3 | Typing "the stand screw is loose" (or tapping "Loose") | The tell-the-app-what's-broken input, image stays the evidence |
| 4 | Diagnosis card (I SEE / POSSIBLE ISSUE / SAFETY) | The editorial design + safety-first policy |
| 5 | Start Fix, one guided step with voice + HOW IT MOVES arrow + haptic | The multisensory guidance, unique vs other AI apps |
| 6 | Show Me camera annotation | The pencil-on-the-world moment, the most memorable shot |
| 7 | Verify This Step with PASS | Verification, not fake cheerleading |
| 8 | Paywall with real prices + Test Store purchase + badge flips to FIXLENS PRO | RevenueCat integration, live |
| 9 | Rewarded ad button on paywall | The Catvertising award entry |
| 10 | Demo Mode picker | Safety net if anything goes wrong, and honesty story |

## 3. Your physical objects

You said you have a laptop stand with a loose screw. That is perfect for shot
2 through 7. Backup objects that also work:

- **Laptop stand with loose screw** (your main object), scan it, tell the app
  "the stand screw is loose", fix along, verify.
- **Any chair that wobbles** (dining chair is fine), Demo Mode has a scripted
  chair journey, but a real wobbly chair also scans well.
- **Remote control with the battery door slid off**, reads as "Missing part".
- **A shelf or cabinet with a visibly loose bolt**, "Loose" again.

Nothing here needs to actually be repaired on camera. The demo shows the app
diagnosing and guiding, you can stop after one verified step.

## 4. Setup (do this once, the night before)

### Phone
1. Install the app on your iQOO Z6: plug it into the computer with USB,
   enable Developer options + USB debugging on the phone, then run
   `bash scripts/setup_device.sh` (it installs the APK, sets up port
   forwarding, and writes the backend + RevenueCat config for you).
2. Screen recording: swipe down twice from the top, find **Screen recorder**
   in the quick settings. If asked, choose 1080p, 60 fps is unnecessary.
3. Do a **practice run** of the whole script once without recording. Every
   first take hits a surprise (permission dialog, slow AI, wrong tap).

### Backend (needed for live scans)
1. On the computer: `cd backend && source .venv/bin/activate && uvicorn app.main:app --port 8000`
2. Keep `adb reverse tcp:8000 tcp:8000` connected the whole shoot (the setup
   script adds it; re-run it if you unplug the phone).
3. Test one live scan before recording. If the AI is slow that day
   (free-tier latency can reach ~90 s), record on a calmer network or lean
   harder on Demo Mode, which never touches the network.

### The room
- Daylight from a window, lamp behind the phone is bad, lamp beside the
  object is good.
- Plain table surface, no clutter in frame.
- Phone on a stack of books or a stand, so the screen recording is steady.

## 5. The shot list (record in pieces, assemble later)

Record each shot as its own screen-recording file. You will trim and join
them (free tools: CapCut, iMovie, or DaVinci Resolve).

**Shot A (about 10 s), Home + voice-over.** Open FixLens. Say: "FixLens is a
camera-first repair assistant. You show it what is broken, and it walks you
through the fix, and tells you when NOT to fix." Point at the FIXLENS PRO
badge only if you already have Pro active on the device, otherwise skip the
badge mention.

**Shot B (about 25 s), the real scan.** Tap "Scan a photo". Frame the laptop
stand so the loose screw is visible. Capture. On the review screen, tap the
"Loose" chip, then type is optional: show it once for effect ("the stand
screw is loose"). Tap "Use image". While the analyzing spinner runs, say:
"I told it what I think is wrong. It still checks the image itself, my word
is a hint, not evidence." Diagnosis card appears. Pause here for 3 full
seconds so judges can read I SEE / POSSIBLE ISSUE / SAFETY.

**Shot C (about 30 s), guided repair.** Tap "Start Fix". Let the first step
speak (voice guide ON). Point out HOW IT MOVES (the animated arrow) and the
haptic tick on step completion. Tap "Show Me" if the step has it: the ink
contour + arrow over the live camera is the money shot, hold it 3 seconds.
Then tap "Verify This Step", capture the (now tightened, or deliberately
still loose) screw, show PASS (or honestly show UNCERTAIN + the better-view
instruction, that is ALSO a good look, honesty scores points).

**Shot D (about 25 s), paywall + purchase.** Open "FixLens Pro". Slowly
scroll the plans: Pro Monthly, Pro Annual (Save badge), Lifetime, Repair
Packs. Say: "Diagnosis and safety are always free. Pro unlocks guided
repair, verification, and assembly." Tap the monthly plan, the RevenueCat
Test Store sheet appears, tap TEST VALID PURCHASE. Return to Home, badge now
reads FIXLENS PRO.

**Shot E (about 15 s), the rewarded ad.** On the paywall (use a second
device state or reorder: easiest is to record this BEFORE shot D), point at
"Watch a short ad for 1 bonus scan". Say: "One opt-in rewarded ad, only when
a free user runs out of scans. Never for safety, never for Pro." Do NOT play
the whole ad on camera, 2 seconds of the button is enough.

**Shot F (about 15 s), Demo Mode + close.** Home, "Try a guided demo", pick
"Stuck office chair". Show the DEMO MODE banner and say: "Everything works
offline too, safely scripted, honestly badged." End on the home screen.

> Tip: recording E before D keeps the paywall free-user state for the ad
> button (it only shows for free users). Order in the final edit: A, B, C,
> E, D, F.

## 6. Voice-over

- Write the lines above on paper, read them slowly, smiling (it sounds
  different, really).
- Record in a quiet room, blanket on the bed kills echo, phone under the
  blanket trick works for narration.
- Alternatively record voice-over after, in CapCut: mute the screen audio,
  narrate over the picture. Cleaner result, 15 extra minutes of work.

## 7. Assembly

1. Join shots A to F in order, trim dead air, keep total **under 2 minutes**.
2. Add captions for the key phrases: "safety is never paywalled",
   "your word is a hint, the image is the evidence", "RevenueCat Test Store,
   live purchase".
3. Background music: pick something calm from the free YouTube Audio Library,
   volume 15% under the voice.
4. Export 1080p, upload to YouTube (unlisted is fine), paste the link in the
   Devpost form.

## 8. If things go wrong while recording

- **AI slow or backend down:** switch that scan to Demo Mode and keep going.
  The script only loses one shot.
- **Purchase sheet misbehaves:** the runbook `docs/PURCHASE_DEMO.md` has the
  recovery steps (restore purchases, re-launch).
- **You flub a line:** stop, breathe, redo that shot. Shots are cheap.

## 9. Final checklist before upload

- [ ] Under 2 minutes
- [ ] Premium features visible in the first 3 minutes (they are, at ~1:30)
- [ ] Real purchase completes on camera
- [ ] DEMO MODE banner visible at least once (honesty)
- [ ] No em dashes in on-screen text or captions (project rule)
- [ ] YouTube link pasted on Devpost, video set to public or unlisted
- [ ] Repo link on Devpost points to github.com/khushi-infinity/FixLens
