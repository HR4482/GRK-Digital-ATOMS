#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-evidence
pkg=com.grk.spatialaudio
trap 'adb logcat -d > smoke-evidence/logcat.txt || true' EXIT
adb wait-for-device
adb shell input keyevent 82
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS
adb shell am force-stop "$pkg"
adb logcat -c
adb shell am start -W -n "$pkg/.MainActivity" | tee smoke-evidence/main-start.txt
grep -q 'Status: ok' smoke-evidence/main-start.txt
sleep 2
dump_ui() {
  adb shell uiautomator dump /sdcard/grk-window.xml >/dev/null
  adb pull /sdcard/grk-window.xml "smoke-evidence/$1.xml" >/dev/null
}
tap_text() {
  local coords
  coords=$(python3 - "$1" "$2" <<'PY'
import sys, re, xml.etree.ElementTree as E
root=E.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    if sys.argv[2] in n.get('text',''):
        a=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
        if len(a)==4:
            print((a[0]+a[2])//2,(a[1]+a[3])//2)
            break
else:
    raise SystemExit('UI element not found: '+sys.argv[2])
PY
)
  adb shell input tap $coords
}
dump_ui main
tap_text smoke-evidence/main.xml "Open GRK Immersive 5.1.2 DSP player"
sleep 2
dump_ui dsp
grep -q 'Choose WAV' smoke-evidence/dsp.xml
adb shell dumpsys activity activities > smoke-evidence/activities.txt
grep -q 'com.grk.spatialaudio/.DspActivity' smoke-evidence/activities.txt
adb exec-out screencap -p > smoke-evidence/dsp.png
tap_text smoke-evidence/dsp.xml "Choose WAV"
sleep 2
dump_ui picker
grep -qi 'documentsui\|files' smoke-evidence/picker.xml
adb shell input keyevent 4
sleep 1
adb logcat -d -b crash > smoke-evidence/crashes.txt
if grep -q 'Process: com.grk.spatialaudio' smoke-evidence/crashes.txt; then
  echo 'GRK application crash detected' >&2
  exit 1
fi
echo 'PASS: emulator install, launch, DSP screen navigation, and file chooser; no GRK crash detected.' | tee smoke-evidence/result.txt
echo 'Not a physical device, actual sound, playback endurance, or production signing test.' >> smoke-evidence/result.txt
