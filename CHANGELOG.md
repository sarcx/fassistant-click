# Changelog

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
