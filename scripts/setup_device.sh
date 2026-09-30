#!/bin/bash
# FixLens — one-shot physical device setup (USB).
# Installs the debug APK, forwards the backend port, writes the device
# config, and launches the app. Safe to re-run after every reconnect.
#
# Usage:  bash scripts/setup_device.sh

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$(command -v adb || true)"
if [ -z "$(command -v "$ADB" 2>/dev/null; echo x)" ] && [ ! -x "$ADB" ]; then
  echo "ERROR: adb not found. Install Android platform-tools first."
  exit 1
fi

"$ADB" start-server >/dev/null 2>&1

echo "== 1. Looking for a physical device (plug it in now; 60s timeout) =="
DEV=""
for i in $(seq 1 60); do
  DEV=$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /emulator/ {print $1; exit}')
  [ -n "$DEV" ] && break
  sleep 1
done
if [ -z "$DEV" ]; then
  echo "ERROR: no physical device in 'device' state."
  "$ADB" devices -l
  echo "If your phone shows 'unauthorized': unlock it and accept the USB-debugging prompt."
  echo "If nothing shows: check the cable, USB mode (File transfer), and Developer options."
  exit 1
fi
echo "Found device: $DEV"

echo "== 2. Installing the debug APK =="
APK="/Users/khushisarawagi/Desktop/FixLens/android/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "ERROR: APK not found at $APK — build it with :app:assembleDebug"; exit 1; }
"$ADB" -s "$DEV" install -r "$APK" || { echo "ERROR: install failed"; exit 1; }

echo "== 3. Forwarding backend port (adb reverse) =="
"$ADB" -s "$DEV" reverse tcp:8000 tcp:8000 && echo "phone:8000 -> computer:8000"

echo "== 4. Writing device config =="
"$ADB" -s "$DEV" shell am start -n com.fixlens.app/.MainActivity >/dev/null 2>&1
sleep 3
"$ADB" -s "$DEV" shell am force-stop com.fixlens.app
"$ADB" -s "$DEV" shell run-as com.fixlens.app mkdir -p files
echo "backend.url=http://127.0.0.1:8000" | "$ADB" -s "$DEV" shell "run-as com.fixlens.app sh -c 'cat > files/fixlens.properties'"
echo "== verify =="
"$ADB" -s "$DEV" shell run-as com.fixlens.app cat files/fixlens.properties

echo "== 5. Launching FixLens =="
"$ADB" -s "$DEV" shell am start -n com.fixlens.app/.MainActivity
echo
echo "DONE. On the phone: grant camera permission, then try the demo or a live scan"
echo "(backend must be running:  cd backend && uvicorn app.main:app --host 127.0.0.1 --port 8000 )"
