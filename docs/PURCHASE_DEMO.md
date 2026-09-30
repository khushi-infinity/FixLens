# FixLens Test Store purchase demo (Shipaton video runbook)

Goal: show a real purchase flowing through RevenueCat in the demo video, then
show what unlocks. This is RevenueCat's development-mode sandbox: no real
money moves, the entitlement state is real, and the exact code path ships to
production. Say that one line on camera, it builds credibility.

## 0. One-time setup (before recording)

Your project exists and the key is already on the emulator. The remaining
step that makes the purchase actually flip Pro:

1. **Attach the products to the entitlement.** RevenueCat dashboard →
   Product catalog → Entitlement **`fixlens_pro`** → Attach: `monthly`,
   `yearly`, `lifetime` (and the packs if you keep them). Without this the
   purchase completes but the app correctly stays in free mode, and the
   paywall now says so explicitly.
2. **Offering:** your current offering already serves monthly ($9.99),
   yearly ($79.98), and lifetime ($99.99) — the paywall picks these up live
   and computes the annual savings badge (33%).
3. Optional rename to the spec catalog (`fixlens_monthly`, `fixlens_annual`,
   `fixlens_lifetime`, `fixlens_repair_pack_5/10`): the app matches packages
   by RevenueCat package type, so either naming works. If you rename, update
   the Devpost HAMM prices to the dashboard prices.
4. Also grab the **project ID** (Project Settings) for the Devpost form.

## 1. Configure the phone (iQOO Z6)

With the phone plugged in and recognized by adb:

```bash
cd /Users/khushisarawagi/Desktop/FixLens
bash scripts/setup_device.sh   # installs APK, sets backend.url
adb shell "run-as com.fixlens.app sh -c 'cat >> files/fixlens.properties'" <<'EOF'
revenuecat.api_key=testn_YOUR_TEST_STORE_KEY
EOF
adb shell am force-stop com.fixlens.app
adb shell am start -n com.fixlens.app/.MainActivity
```

Verify: Home shows the badge `3 free scans left` (it reflects real billing
state once the key is present).

## 2. The purchase scene (shot list, about 35 seconds)

1. **Home** → tap **FixLens Pro** (badge). The paywall opens with the full
   pricing page: live dashboard prices, annual marked recommended with the
   computed savings badge. Linger 2 seconds so judges read the prices.
2. Tap **Pro Monthly** (or Annual). The RevenueCat **Test Store purchase
   sheet** slides up showing the product and price (sandbox mode).
3. Tap **TEST VALID PURCHASE**. The sheet closes, a confirm haptic fires,
   and the app closes the paywall automatically: the entitlement flipped
   for real.
4. Back on **Home**, the badge now reads **FIXLENS PRO**. Say the line:
   "That's RevenueCat's Test Store sandbox, no real money, but the
   entitlement is real state that ships unchanged to production."
5. **Show what unlocked**, in one fluid pass:
   - **Scan a photo** now says no scan-count anything: scans are unlimited.
   - Start any repair: guided steps open with **no paywall interstitial**.
   - Toggle the voice guide: steps read aloud (previously unmetered, keep
     the walkthrough short), the HOW IT MOVES diagram animates.
   - Open **My repairs**: the purchase unlocked the journal's full history.
   - Optional, 5 seconds: **Restore Purchases** from the paywall returns
     "Pro is active", proof the receipt is real state, not a local flag.

## 3. Fallbacks (if the sheet misbehaves on camera)

- Products empty / "not configured": the key line is missing or wrong in
  `fixlens.properties`. Re-check step 1 and restart the app.
- Sheet spins forever: airplane-mode the phone, retry; Test Store needs
  network. Re-record that 5-second segment, the edit hides it.
- Purchased already: **RevenueCat dashboard → Customers → your test user →
  Grant/Reset**, or uninstall + reinstall the app for a fresh sandbox user.
- A one-time **Repair Pack** purchase is an equally strong scene and shows
  the credits increment on Home, use it if subscriptions misbehave.

## 4. Optional: compressed renewal for extra credibility

Test Store subscriptions renew on a compressed schedule (a 1-month product
renews about every 5 minutes, up to 5 times). Record 10 seconds of the
entitlement surviving a renewal, or mention it: "the sandbox renews every
five minutes so we could watch the whole lifecycle in one sitting."
