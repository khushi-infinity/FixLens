# FixLens, Physical Device Development Guide

This guide takes you from a stock Android phone to FixLens running against your
local FastAPI backend. It follows the master spec's runbook (§14): USB + `adb
reverse` is the preferred development connection; no IP address is ever
hardcoded into the app.

## 0. What you need

- Android Studio (with its bundled JDK), https://developer.android.com/studio
- A Mac/Linux/Windows machine for the backend
- A physical Android phone (Android 8.0 / API 26 or newer) + USB cable
- Python 3.10+ for the backend

## 1. Enable Developer options on the phone

1. Open **Settings → About phone**.
2. Tap **Build number** 7 times.
3. Enter your lock-screen PIN if asked. "You are now a developer!" appears.

## 2. Enable USB debugging

1. Open **Settings → System → Developer options** (location varies by OEM).
2. Toggle **USB debugging** on.

## 3. Connect the phone

1. Plug the phone into your computer with USB.
2. On the phone, choose **File transfer / Android Auto** mode if prompted.
3. A dialog "Allow USB debugging?" appears, check **Always allow from this
   computer** and tap **Allow**.

## 4. Verify the device with adb

```bash
adb devices
```

Expected output, the device must say `device`, not `unauthorized`:

```text
List of devices attached
ABC123XYZ   device
```

If nothing is listed: try another cable/port, re-check step 2-3, and run
`adb kill-server && adb start-server`.

## 5. Start the FastAPI backend

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Smoke-test it in another terminal:

```bash
curl http://127.0.0.1:8000/health
# {"status":"ok","version":"0.1.0"}
```

## 6. Forward the backend port to the phone

```bash
adb reverse tcp:8000 tcp:8000
```

Now the phone's own `127.0.0.1:8000` reaches your computer's backend, no LAN
IP, no firewall changes. You must repeat this after every reconnection.

## 7. Configure the app's backend URL on the device

FixLens never hardcodes a backend address. In development you write the URL
into the app's private config file (debug builds only).

**Quoting matters here:** the file redirect must run *inside* `run-as`, so the
whole remote command must be wrapped and the content piped in. Use this exact
form:

```bash
# 1. Launch the app once so its files/ directory exists
adb shell am start -n com.fixlens.app/.MainActivity && sleep 3
adb shell am force-stop com.fixlens.app

# 2. Ensure the directory exists (relative path, run-as safe)
adb shell run-as com.fixlens.app mkdir -p files

# 3. Write the config (piped through cat so the redirect runs as the app user)
echo "backend.url=http://127.0.0.1:8000" | \
  adb shell "run-as com.fixlens.app sh -c 'cat > files/fixlens.properties'"

# 4. Verify
adb shell run-as com.fixlens.app cat files/fixlens.properties
# backend.url=http://127.0.0.1:8000
```

If step 3 fails with `Permission denied`, the redirect leaked outside the
quotes, copy the command exactly as written (double quotes outside, single
quotes inside).

## 8. Install the Android debug build

Option A, command line:

```bash
cd android
./gradlew installDebug
```

Option B, Android Studio: **Open** the `android/` folder, wait for Gradle sync,
select your phone in the device dropdown, press **Run ▶**.

## 9. Launch FixLens

```bash
adb shell am start -n com.fixlens.app/.MainActivity
```

Or tap the FixLens icon on the phone.

## 10. Verify the app reaches the backend

1. Grant camera permission when asked (Photo Mode or Live Camera).
2. Capture anything and tap **Use image**.
3. The "Scan saved" screen shows the true backend status chip:
   - `Backend: OK (v0.1.0)`, adb reverse works end-to-end
   - `Backend: unreachable`, check step 6 (`adb reverse`) and step 5
   - `Backend: not configured`, repeat step 7 and restart the app

## Alternative: LAN mode (no USB)

If `adb reverse` is unavailable, run the backend on `0.0.0.0`:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Find your computer's LAN IP (`ipconfig getifaddr en0` on macOS) and write it
into the device config instead:

```bash
echo "backend.url=http://YOUR_LAN_IP:8000" | \
  adb shell "run-as com.fixlens.app sh -c 'cat > files/fixlens.properties'"
```

The phone and computer must share the same Wi-Fi network. The URL lives only in
the device config file, it is never compiled into the app.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `adb` not found | Install Android platform-tools and add to PATH |
| Device `unauthorized` | Re-accept the debugging prompt on the phone |
| `run-as` permission denied | Debug build required; reinstall via `installDebug` |
| `Backend: unreachable` | Re-run `adb reverse tcp:8000 tcp:8000`; confirm curl works on the computer |
| Camera preview black in emulator | Start the emulator with camera passthrough or grant camera permission in emulator settings |

## RevenueCat purchases (Phase 6)

Monetization uses RevenueCat with the **Test Store** in development. The API
key is a public, app-specific value, but like every FixLens identifier it
lives in the device config file, never in source:

1. Create a RevenueCat project (the Test Store is created automatically).
2. In the Product catalog, create the spec §12 products on the Test Store and
   attach them to the current offering:
   - `fixlens_monthly`, $7.99 / month
   - `fixlens_annual`, $59.99 / year
   - `fixlens_repair_pack_5`, $3.99 one-time
   - `fixlens_repair_pack_10`, $6.99 one-time
   - Entitlement: `fixlens_pro` (both subscriptions attach it)
3. Copy the **Test Store API key** and add it to the device config:

   ```bash
   adb shell "run-as com.fixlens.app sh -c 'cat >> files/fixlens.properties'" <<'EOF'
   revenuecat.api_key=testn_YOUR_TEST_STORE_KEY
   EOF
   ```

4. Restart the app. Tapping **FixLens Pro** on Home now opens the paywall
   with real offerings; purchases present the Test Store modal (simulate
   success / failure / cancel) and update the real entitlement state.

Without a key the app stays in free mode: the paywall shows an explicit
"not configured" state and purchases are disabled, nothing is faked.

Test-store subscriptions renew on a compressed schedule (a 1-month test
product renews every ~5 minutes, up to 5 renewals), useful for watching
entitlement lifecycle live. **Never ship a Test Store key**: the SDK crashes
release builds that contain one, by design.
