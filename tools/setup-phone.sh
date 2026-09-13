#!/bin/bash
# One-time setup for a phone. Connect it over adb first, then run this from the repo root.
#
#   tools/setup-phone.sh [path/to.apk]
#
# Both gates this app needs are grantable over adb. The accessibility one is the interesting
# case: writing the secure setting directly is what sidesteps the Android 13 restricted-settings
# dialog that a sideloaded app otherwise hits.

set -uo pipefail

PKG=dev.todor.fassistantclick
SERVICE="$PKG/$PKG.ClickService"
ADB="${ADB:-adb}"
APK="${1:-}"

if [ -z "$APK" ]; then
  APK=$(ls -t dist/*.apk 2>/dev/null | head -1)
fi

if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No APK found. Run ./gradlew :app:dist first, or pass a path as the first argument." >&2
  exit 1
fi

step() {
  local label="$1"
  shift
  printf '==> %s\n' "$label"
  if ! "$ADB" shell "$@" >/dev/null 2>&1; then
    printf '    could not set this over adb — do it by hand in Settings\n'
  fi
}

printf '==> installing %s\n' "$APK"
if ! "$ADB" install -r "$APK"; then
  # Android 15 and later refuse to install below targetSdk 24; this app sits at 25, but if a
  # future release raises the floor past us, the block can still be bypassed for sideloading.
  "$ADB" install -r --bypass-low-target-sdk-block "$APK"
fi

# The panel and the tap points are overlay windows, so without this there is no way to aim.
step "allow drawing over other apps" appops set "$PKG" SYSTEM_ALERT_WINDOW allow

# The only way to send a touch. Appended rather than assigned: this setting is a shared list, and
# overwriting it would switch off any accessibility tool already in use on this phone.
printf '==> enabling the accessibility service\n'
CURRENT=$("$ADB" shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
case "$CURRENT" in
  null|"") NEXT="$SERVICE" ;;
  *"$SERVICE"*) NEXT="$CURRENT" ;;
  *) NEXT="$CURRENT:$SERVICE" ;;
esac
if ! "$ADB" shell settings put secure enabled_accessibility_services "$NEXT" >/dev/null 2>&1; then
  printf '    could not set this over adb — turn it on by hand in Accessibility settings\n'
fi
step "switching accessibility on" settings put secure accessibility_enabled 1

# Only needed on Android 13+, and only so the run notification is not hidden.
step "allow posting notifications" pm grant "$PKG" android.permission.POST_NOTIFICATIONS

# Needed before the app can install its own updates.
step "allow installing apps" appops set "$PKG" REQUEST_INSTALL_PACKAGES allow

printf '==> starting the app\n'
"$ADB" shell am start -n "$PKG/$PKG.ui.MainActivity" >/dev/null 2>&1

cat <<'DONE'

Done. Check the app's Permissions section — both gates should read "On". If the accessibility one
says "On, not connected", Android has a stale entry: turn the service off and on again in
Accessibility settings.

Then:

  1. Manage scripts, make one, add a step.
  2. Place points on screen — the app steps aside, so switch to whatever you are automating and
     tap where the gesture belongs.
  3. Show the panel, and drag it somewhere none of your points sit underneath. A run whose points
     are under the panel is refused, because the panel has to stay touchable for Stop to work.
DONE
