# Changelog

## 0.1.2

Fixes self-update never offering to install on Android 8 and later. The update screen said Android
needed "Install unknown apps" for this app, and kept saying so after it had been allowed.

Before offering the install, the app asked Android whether it was allowed to install apps. For an app
that targets Android 7.1 or older, as this one deliberately does, Android answers no to that
question whatever the setting says. The app no longer asks. The install button is always there, and
Android's own installer checks the real setting: if it is off, it says so, links to it and then
carries on with the install.

## 0.1.1

**This release is signed with a different key from 0.1.0, so it cannot replace it in place.** If
0.1.0 is on a phone, uninstall it first — and uninstalling deletes the saved scripts, so copy out
anything worth keeping before you do.

0.1.0 was signed with a key belonging to this app alone. The new catalogue app refuses any download
whose signing certificate does not match its own, which is how it tells a real Fassistant release
from anything else sitting at that address, so an app on its own key could be listed but never
installed from there. Fassistant and the catalogue already share one key; this app now uses it too.
Done at 0.1.1, while one release exists and almost no phone has it, because the same change made
later costs the same uninstall on every phone.

The published release manifest also gains two fields: the package this repository installs, and the
name to show for it. The catalogue builds its list from the manifest of every Fassistant app, and
without the package name a row could only ever say "here is an app", never whether it is already on
the phone and at which version.

## 0.1.0

First build. Everything the plan called for except gesture recording, which was dropped on
request.

Measured on this phone rather than assumed: the two gesture ceilings are read from
`GestureDescription.getMaxStrokeCount()` and `getMaxGestureDuration()` at startup and written to
logcat under the `fclick` tag, and a step asking for more fingers than the phone allows is refused
with the limit in the message instead of being silently truncated.

- Accessibility service that owns everything touching the screen: the floating panel, the tap-point
  markers, the point-placing overlay and the run itself. No second service — an accessibility
  service is already bound by the system, so a foreground service would add a notification
  requirement and buy nothing.
- Six gesture kinds, all built as stroke layouts over the same `dispatchGesture` call: tap, swipe,
  curved swipe, pinch in, pinch out, and simultaneous taps on several points.
- Per-step timing — how long the gesture takes, and how long to wait afterwards — plus a pass count
  or an endless run, and a countdown before the first gesture.
- Steps are chained off the dispatch callback rather than off a clock, so a phone that takes longer
  than asked to deliver a gesture falls behind instead of overlapping gestures.
- Pause lands at the next step boundary, because a gesture cannot be interrupted once dispatched.
  The panel says so while it is waiting.
- Markers are separate overlay windows, touchable while you aim and not touchable for the length of
  a run. Without that second state a marker would swallow the very tap it is aiming — a dispatched
  gesture is a real touch, and it goes to whatever window sits at those coordinates.
- The panel has to stay touchable so Stop is reachable, so a run whose points sit under it is
  refused up front with the step number, rather than running and appearing to do nothing.
- Scripts saved as one JSON file each under `files/scripts/`, with load, rename, duplicate and
  delete. Panel size, opacity, tap-point size and the panel's own position persist.
- Permissions screen showing both gates and a deep link to each. The accessibility gate reports
  three states, not two: Android's list can say the service is enabled while the service has not
  connected, and telling those apart is the only way to explain a phone that looks configured and
  does nothing.
- `tools/setup-phone.sh` clears both gates over adb. It writes
  `enabled_accessibility_services` directly, which is what sidesteps the Android 13 restricted-
  settings dialog — and it appends to that setting rather than replacing it, so an accessibility
  tool you already rely on keeps working.
- Self-update against one configurable manifest URL, baked in at build time. Two checks before you
  are ever asked to install: the SHA-256 from the manifest, and the candidate's signing
  certificate, which has to match the running app's.
- Tagging `v<version>` builds, signs and publishes a release, then deletes older releases so only
  the newest is ever published.

### Known limits

- Points are stored in screen pixels, so a script placed in portrait will not line up in
  landscape. Rotation-aware coordinates are not in this build.
- Gesture recording is not here at all. Replaying a fixed sequence is what the app does.
- Multi-finger swipes are not offered — the multi-finger kind is simultaneous taps. Pinch and
  spread cover the two-finger movement cases.
