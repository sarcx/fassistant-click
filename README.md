# fassistant-click

An auto-clicker for Android. Drop tap points onto the screen, set the timing, and let the phone
drive itself.

Sideloaded onto your own phones, like its sibling [fassistant](../fassistant-android). Not on the
Play Store, so it gets to make choices a published app could not.

- **minSdk 24** (Android 7.0) — `dispatchGesture` does not exist below it, and it is the whole app
- **targetSdk 25** — deliberate, same reasoning as the sibling
- **68 KB** release APK, zero runtime dependencies beyond the Kotlin standard library

## Problem

Tapping the same spot four hundred times is work a phone should be doing for itself. The
auto-clickers that do this well are all published apps, which means ads, a subscription, an
analytics SDK, and a permission list padded out with things a tapping tool has no business asking
for. Click Assistant is the closest match for what is wanted here, so this app aims at the same
capability set — written from scratch, with nothing in it that does not serve the tapping.

Nothing was taken out of the original APK. No decompiled code, no extracted assets, no icon, no
branding. The feature list is the only thing borrowed.

## One call does all of it

Every gesture is the same `GestureDescription` handed to the same `dispatchGesture`, and the
difference between them is entirely how the strokes are laid out in time and space.

| Gesture | Points | Strokes | Shape |
| --- | --- | --- | --- |
| Tap | 1 | 1 | a path that goes nowhere; the duration is how long the finger stays down |
| Swipe | 2 | 1 | a straight path |
| Curved swipe | 3 | 1 | one path with a `quadTo` through the middle point |
| Pinch in | 2 | 2 | both points travel to their midpoint, starting together |
| Pinch out | 2 | 2 | the same, outwards |
| Taps at once | *n* | *n* | one zero-length path per point, all starting at t=0 |

Repeat count is a queue of gestures with each step's own delay between them. The two ceilings —
how many strokes one gesture may hold and how long it may last — are read from
`GestureDescription.getMaxStrokeCount()` and `getMaxGestureDuration()` at startup rather than
hardcoded, logged under the `fclick` tag, and shown on the main screen. A step wanting more
fingers than the phone allows is refused with the limit in the message.

Holding a finger down across more than one path (`willContinue` / `continueStroke`) needs
Android 8.0, so it is not used — every gesture here is single-path.

## The app fights its own overlay

A dispatched gesture is a real touch, not a message addressed to an app. It goes to whichever
window sits at those coordinates — including this app's own tap-point markers, which by definition
sit exactly there. Two consequences shape the code:

- **Markers are their own overlay windows**, touchable while you drag them and carrying
  `FLAG_NOT_TOUCHABLE` for the length of a run. Without that second state a marker swallows the
  gesture it is aiming, the run reports success, and nothing happens.
- **The panel has to stay touchable** so Stop is reachable. So a run whose points sit under the
  panel is refused up front, with the step number, rather than starting and appearing to work.

Every overlay window uses `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`, so window coordinates
are raw screen pixels — the same space `dispatchGesture` works in. Without that a marker at y=0
would be a status bar's height away from where its tap lands.

## Two gates, and the Android 13 problem

| Gate | Without it | Cleared by |
| --- | --- | --- |
| Draw over other apps | no panel, no markers, no way to aim | `appops set <pkg> SYSTEM_ALERT_WINDOW allow` |
| Accessibility service | nothing works at all | `settings put secure enabled_accessibility_services <pkg>/<pkg>.ClickService` |

On Android 13 and later a sideloaded app is greyed out in the accessibility list until you go to
App info, the three-dot menu, and *Allow restricted settings* — a deliberate response to banking
trojans abusing the API. Writing the secure setting over adb sidesteps that dialog entirely,
because it never consults the list. That is what `tools/setup-phone.sh` does, and it **appends** to
the setting rather than replacing it, so an accessibility tool you already rely on keeps working.

The permissions screen reports the accessibility gate in three states, not two: Android's list can
say the service is enabled while the service has not connected, and telling those apart is the
only way to explain a phone that looks configured and does nothing.

## Getting it onto a phone

```sh
./gradlew :app:dist          # release APK, stable-named copy, and update.json, into dist/
tools/setup-phone.sh         # installs and clears both gates over adb
```

`local.properties` needs `sdk.dir`; copy `local.properties.example` and fill it in. Without a
signing key the build still works, signed with the local debug key — it just cannot produce an APK
that upgrades an installed copy in place.

Then, on the phone: **Manage scripts**, make one, add a step, and **Place points on screen** — the
app steps aside so you can switch to whatever you are automating and tap where the gesture
belongs. **Show the panel**, drag it clear of your points, and press Start.

Scripts live as one JSON file each under `files/scripts/`, readable with
`adb shell run-as dev.todor.fassistantclick`.

## Why targetSdk 25

Same trick as the sibling: `compileSdk` and `targetSdk` are independent, so the app is built
against Android 16 while opting out of behaviour changes gated on the level it targets. Here that
buys no runtime notification permission and no foreground-service type to declare. Android 15 and
16 refuse to install anything below targetSdk 24, so 25 clears the floor by one.

There is no foreground service at all. An accessibility service is already bound by the system and
long-lived, so a second one would add a notification requirement and buy nothing. The ongoing
notification during a run is posted directly by the accessibility service, and exists for the one
case the panel cannot cover: an endless run whose panel has been dragged half off the screen.

## Out of scope

- The Play Store.
- Gesture recording. Replaying a fixed sequence is what this does; recording a live interaction
  with another app would need to observe touches without consuming them, which the accessibility
  API does not appear to offer.
- Cloud sync, accounts, or sharing scripts between phones.
- Anything that needs root: input injection outside the accessibility API, silent install.

## Unknowns

Two things could not be settled without a phone in hand, and both are worth checking on first
install:

1. **Is a targetSdk 25 accessibility service offered in the Accessibility list on Android 13+?**
   Nothing in the documentation says the enumeration filters on target level, and the adb route
   writes the setting without consulting that list — but the sibling repo learned that a low
   `targetSdk` buys less than the docs imply. If it turns out not to be listed, raise
   `targetSdk` to 28; it costs nothing this app relies on.
2. **Does a dispatched tap actually get eaten by a touchable overlay of ours?** The code assumes
   yes, which is why markers go non-touchable during a run. If the assumption is wrong the app
   still works — it is just being careful for no reason.

## Status

Builds and passes lint. Not yet run on a phone.

Execution plan: https://claude.ai/code/artifact/6b1ea71c-0165-4e02-a9d6-98618d3e01b6
(source: `docs/fassistant-click-plan.html`)
