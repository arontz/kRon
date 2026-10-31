# kRon for Android, phase 1: floating lyrics card

## Intent

kRon today is a Windows taskbar app that shows lyrics for whatever is playing. The Android app brings the same idea to a phone: while music plays in any app, a small see-through glass card floats over whatever the user is doing and shows the lyrics, synced to the song.

A later phase adds song recognition: a **Listen** button inside the kRon app itself (its main screen, not on the overlay card) that identifies a song from the microphone, then feeds the same card. Phase 1 must be built so recognition can plug in without changing the card, and the main screen should leave room for that button.

## Goals

- Detect the song playing in any media app (title, artist, duration, playback position, paused or playing).
- Fetch synced lyrics for it and show them on a floating card, highlighting the current line.
- The card is small, frosted glass, draggable, and remembers where the user left it.
- Touches outside the card reach the app underneath.
- Minimum Android 12 (API 31).
- A signed release APK that builds locally and in GitHub Actions using the four `ANDROID_*` secrets already added to the repo.

## Non-goals (phase 1)

- Song recognition and the Listen button (next phase).
- Searching for lyrics by hand, user `.lrc` files, calendar flyout, system stats, a settings theme editor.
- Play Store release.
- Android 11 and older.
- Any change to the Windows app.

## Anti-goals

- The card must never steal focus or block touches outside its own bounds.
- Nothing runs when no music is playing: no foreground service, no polling.
- The app reads only media metadata from notifications. It never stores or sends other notification content.
- The only network traffic is to the lyrics service.

## What the user sees

1. **First launch:** a short screen explaining the two permissions the app needs, with a button for each: notification access and "display over other apps". The screen shows which are granted.
2. **While a song plays:** the glass card appears. Top line: song title and artist in very small text. Below it three lyric lines (previous, current, next) in small text; the current line is brightest and bold. The card has no buttons.
3. **No lyrics found:** the card shows the title and artist and a quiet "No lyrics found" line. It does not retry in a loop.
4. **Pause:** the lyrics stop advancing. The card stays for 10 seconds, then fades out. It comes back on play.
5. **Drag:** the user drags the card by holding it. Its position is remembered across songs and restarts.
6. **Card look:** compact, about 45% of screen width (roughly 160 dp on a typical phone), with the current line around 12 sp and the other lines around 10 sp. Rounded corners, blurred translucent background, thin light edge. On devices where blur-behind is unavailable or turned off, it falls back to a plain translucent dark tint, which is still readable.

## Constraints

- Platform: Android 12+ (minSdk 31), current compile SDK.
- Language and UI: Kotlin; Jetpack Compose for the setup screen and card contents.
- Song source: `MediaSessionManager.getActiveSessions` through a `NotificationListenerService` the user enables.
- Overlay: `WindowManager` with `TYPE_APPLICATION_OVERLAY`, not focusable, only the card area touchable, hosted by the notification listener service (which the system keeps bound), and running only while a session is playing. No foreground service: Android can refuse to start one from the background, and the listener does not need it.
- Card background: a see-through dark tint with a light edge. No blur-behind flag: it blurs the whole screen, not just the card. A real blur under the card is a possible later improvement.
- Lyrics source: LRCLIB (`lrclib.net`), the same one the Windows app uses. Match on title, artist and duration; fall back to its search endpoint if the exact match misses. Prefer synced lyrics; treat plain-only as "not synced".
- Playback position: take the session's reported position and extrapolate with elapsed time and playback speed. Re-sync whenever the session reports a change.
- Cache fetched lyrics on disk so a repeated song does not hit the network.
- Settings (card position) stored locally, e.g. DataStore.

## Decisions the user made

- Overlay style: the movable card (option C), made smaller still, and glass.
- Android 12 and up only.
- Phase 2 is song recognition via a Listen button, which lives in the kRon app itself and not on the overlay card.

## Calls I made (user can change these)

- Native Kotlin app in `android/` next to the desktop app, not Tauri. The overlay and notification access need native code either way, and the Windows Rust code does not carry over.
- Application ID `app.kron.lyrics`, app name "kRon".
- Dropped the playback buttons from the card; the media app already has them.
- Card fades out 10 seconds after pause.
- Lyrics source is LRCLIB only.
- CI: a separate workflow, `android.yml`, that builds a signed release APK when a tag matching `android-v*` is pushed and attaches it to a GitHub release. The existing `release.yml` for Windows must not trigger on these tags.
- Release signing reads the four `ANDROID_*` values from environment variables; locally, from the same variable names or a git-ignored `keystore.properties`.

## Left to the builder

- Project structure, Gradle setup, and library choices.
- Exact card dimensions, fonts, animation timing, and colors, within the look described above.
- How to enable the blur-behind effect and detect when it is off.
- How to keep the song-and-position source separate from the card, so recognition can supply them later.
- Testing approach: unit tests for LRC parsing, line lookup, and position extrapolation; on-device checks for the overlay.

## Done means

- App installs on an Android 12+ device, both permissions can be granted from the first screen, and playing a song in a media app shows the card with correct, synced lyrics.
- The card can be dragged, keeps its place, ignores touches outside itself, and disappears after pause and when playback stops.
- `./gradlew assembleRelease` produces a signed APK locally and in the workflow.
- Windows build and its release workflow are unaffected.
