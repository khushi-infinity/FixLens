# FixLens Test Store purchase demo (Shipaton video runbook)

Goal: show a real purchase flowing through RevenueCat in the demo video, then
show what unlocks. This is RevenueCat's development-mode sandbox: no real
money moves, the entitlement state is real, and the exact code path ships to
production. Say that one line on camera, it builds credibility.

## 0. One-time setup (before recording)

1. Create the RevenueCat project (free): https://app.revenuecat.com → New
   project → name it `FixLens` → platform **Android**.
2. The **Test Store** is created automatically with the project. Open
   **Product catalog → Test Store** and create four products:
   - `fixlens_monthly`, $7.99, subscription, 1 month
   - `fixlens_annual`, $59.99, subscription, 1 year
   - `fixlens_repair_pack_5`, $3.99, one-time
   - `fixlens_repair_pack_10`, $6.99, one-time
3. Create the entitlement **`fixlens_pro`** and attach all four products to
   it. Put monthly + annual in the current **Offering**.
4. Copy the **Test Store API key** (Project Settings → API keys, starts with
   `testn_`). Never use a Test Store key in a release build.
5. Also grab the **project ID** (Project Settings) for the Devpost form.

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
   pricing page: live prices, annual marked recommended with the savings
   badge. Linger 2 seconds so judges read the prices.
2. Tap **Pro Annual**. The RevenueCat **Test Store purchase sheet** slides up
   showing the plan and a **Simulate success** button (this is the sandbox).
3. Tap **Simulate success**. The sheet closes, a confirm haptic fires, and
   the app closes the paywall automatically: the entitlement flipped for
   real.
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
