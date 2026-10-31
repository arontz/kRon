# kRon for Android, Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A native Android app that shows synced lyrics for whatever media is playing on a small, draggable, frosted-glass card floating over other apps.

**Architecture:** A `NotificationListenerService` reads the active media session and publishes a `PlaybackSnapshot` to a `NowPlayingSource`. A `LyricsLoader` fetches and caches LRCLIB lyrics for the snapshot's track. A foreground `OverlayService` renders a Compose card in a card-sized `WindowManager` overlay. The card reads only `NowPlayingSource`, so the later Listen feature can supply songs without changing it.

**Tech Stack:** Kotlin, Jetpack Compose, Gradle (Kotlin DSL, version catalog), kotlinx-coroutines, kotlinx-serialization-json, JUnit 4. minSdk 31, compileSdk/targetSdk 35, JDK 17.

**Spec:** `docs/superpowers/specs/2026-11-01-android-lyrics-overlay-design.md`

## Global Constraints

- All Android code lives in `android/`. Do not modify `src/`, `src-tauri/`, or `.github/workflows/release.yml`.
- Application ID `app.kron.lyrics`, app name "kRon", Kotlin package `app.kron.lyrics`.
- minSdk 31 (Android 12). No code paths for older versions.
- Lyrics come only from LRCLIB (`https://lrclib.net`). Send User-Agent `kRon-Android/0.1 (https://github.com/arontz/kRon)`. The only other network traffic is `api.github.com` (release list), contacted only when the user taps "Check for updates"; there is no background update check. Everything the app reads or sends is shown on the in-app Activity log screen (in-memory, never written to disk).
- Read only media metadata (title, artist, duration, state, position) from sessions. Never read or store other notification content.
- No foreground service and no polling when nothing is playing.
- Card: about 45% of screen width, current line ~12 sp, other lines ~10 sp, song/artist line very small, no buttons. Fades out 10 s after pause.
- Only the card's own bounds are touchable; the overlay window is card-sized, not full-screen.
- Blur-behind when `WindowManager.isCrossWindowBlurEnabled` is true; otherwise a plain translucent dark tint.
- Signing reads `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (env), or a git-ignored `android/keystore.properties`. Never put a password in a file that is committed or in chat.
- Phase 1 builds no Listen button, only leaves an empty slot for it on the main screen.

## Review Focus

- Several media apps have sessions at once: the one that is playing wins, otherwise the most recently updated one. (Task 6)
- LRC files with several timestamps on one line, out-of-order stamps, blank lines, and tag lines like `[ar:...]`: no crash, lines sorted, tags ignored. (Task 3)
- Skipping tracks quickly: a slow response for the previous song must never replace the current song's lyrics. (Task 5)
- The user revokes overlay or notification access while the service runs: the card goes away and the service stops, no crash. (Tasks 8, 11)
- Rotating the phone or a smaller display leaves the saved card position off-screen: the card is clamped back inside the screen. (Task 8)

---

## File Structure

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml
  keystore.properties            (git-ignored, optional)
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/kotlin/app/kron/lyrics/
    model/Models.kt               Track, LrcLine, LyricsResult, PlaybackSnapshot, NowPlayingSource
    lyrics/LrcParser.kt           parseLrc, currentIndex
    lyrics/LrclibFetcher.kt       LyricsFetcher, parseLrclib, parseLrclibSearch, LrclibFetcher
    lyrics/LyricsCache.kt         disk cache of LRC text
    lyrics/LyricsRepository.kt    cache-first LyricsFetcher
    lyrics/LyricsLoader.kt        LyricsUiState, stale-safe loading
    playback/PositionClock.kt     PositionClock, cardVisible
    media/NowPlayingStore.kt      NowPlayingSource singleton
    media/SessionMapping.kt       SessionInfo, pickSession, toSnapshot
    media/MediaListenerService.kt NotificationListenerService
    overlay/CardLayout.kt         CardLines, visibleLines, cardWidthPx, Pos, clampToScreen
    overlay/GlassCard.kt          composable
    overlay/CardPositionStore.kt  SharedPreferences position
    overlay/OverlayController.kt  WindowManager window, drag, blur
    overlay/OverlayService.kt     foreground service
    MainActivity.kt               setup screen
  app/src/test/kotlin/app/kron/lyrics/...   JVM unit tests mirroring the packages above
  app/src/debug/kotlin/app/kron/lyrics/debug/FakePlayerActivity.kt
.github/workflows/android.yml
```

### Task 1: Project scaffold

**Files:**
- Create: everything under `android/` listed above as Gradle config, `AndroidManifest.xml`, `MainActivity.kt` (blank Compose "kRon" screen), `app/src/test/kotlin/app/kron/lyrics/SmokeTest.kt`
- Modify: `.gitignore`

**Interfaces:**
- Produces: a Gradle project that builds with `./gradlew` (wrapper committed), package `app.kron.lyrics`, Compose enabled, unit tests run on the JVM.

- [ ] **Step 1: Bootstrap the Gradle wrapper.** No Gradle is installed. Tell the user you are downloading `gradle-8.9-bin.zip` from `services.gradle.org` (about 130 MB) and continue once they agree. Unzip it into its own new empty directory in the scratchpad, then in `android/` run that copy's `gradle wrapper --gradle-version 8.9`. Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/*`.
- [ ] **Step 2: Write the Gradle files.** Version catalog pins: AGP 8.7.3, Kotlin 2.0.21 (with the `org.jetbrains.kotlin.plugin.compose` plugin), Compose BOM 2024.12.01, kotlinx-coroutines 1.9.0 (plus `-test`), kotlinx-serialization-json 1.7.3 (plus the serialization plugin), activity-compose 1.9.3, lifecycle 2.8.7, core-ktx 1.15.0, JUnit 4.13.2. If a version will not resolve, move to the latest stable and say so in the commit message. `app/build.gradle.kts`: namespace and applicationId `app.kron.lyrics`, minSdk 31, targetSdk/compileSdk 35, versionName `0.1.0`, versionCode 1, Java/Kotlin target 17.
- [ ] **Step 3: Update `.gitignore`.** Add `android/.gradle/`, `android/build/`, `android/app/build/`, `android/local.properties`, `android/keystore.properties`, `*.jks`, `*.keystore`, `.superpowers/`.
- [ ] **Step 4: Write the smoke test** `SmokeTest.kt`: `fun appIdIsStable() = assertEquals("app.kron.lyrics", BuildConfig.APPLICATION_ID)` (enable `buildConfig = true`).
- [ ] **Step 5: Run** `cd android; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. Expected: BUILD SUCCESSFUL, `app/build/outputs/apk/debug/app-debug.apk` exists.
- [ ] **Step 6: Commit** `git add .gitignore android; git commit -m "feat(android): project scaffold"`.

### Task 2: Emulator for on-device checks

No code. Needed by Tasks 6, 8, 9 and 11.

- [ ] **Step 1: Check acceleration.** `systeminfo` must show a hypervisor or Windows Hypervisor Platform available. If not, stop and tell the user; fall back to asking for a USB-debugging phone.
- [ ] **Step 2: Install packages.** Tell the user you are installing the emulator and the `system-images;android-34;google_apis;x86_64` image (about 1.5 GB) via `sdkmanager`, then run `%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat "emulator" "system-images;android-34;google_apis;x86_64"` (accept licenses with `--licenses` only after the user agrees).
- [ ] **Step 3: Create and boot the AVD.** `avdmanager create avd -n kron34 -k "system-images;android-34;google_apis;x86_64" -d pixel_6`, then start `emulator -avd kron34` in a background terminal tab.
- [ ] **Step 4: Verify.** `adb wait-for-device; adb shell getprop ro.build.version.sdk` prints `34`, and `adb shell ping -c 1 lrclib.net` gets a reply (needs internet). `adb` is at `%ANDROID_HOME%\platform-tools\adb.exe` and is not on PATH, so use the full path.

### Task 3: LRC parsing and current-line lookup

**Files:**
- Create: `model/Models.kt`, `lyrics/LrcParser.kt`
- Test: `lyrics/LrcParserTest.kt`

**Interfaces:**
- Produces in `Models.kt`:
  - `data class LrcLine(val timeMs: Long, val text: String)`
  - `data class Track(val title: String, val artist: String, val durationMs: Long)` (`durationMs <= 0` means unknown)
  - `sealed interface LyricsResult { data class Synced(val lines: List<LrcLine>) : LyricsResult; data object NotSynced : LyricsResult; data object NotFound : LyricsResult; data object Error : LyricsResult }`
  - `data class PlaybackSnapshot(val track: Track?, val positionMs: Long, val speed: Float, val playing: Boolean, val updatedAtMs: Long)` (`updatedAtMs` is `SystemClock.elapsedRealtime()` time)
  - `interface NowPlayingSource { val snapshot: StateFlow<PlaybackSnapshot> }`
- Produces in `LrcParser.kt`: `fun parseLrc(text: String): List<LrcLine>`, `fun currentIndex(lines: List<LrcLine>, positionMs: Long): Int`

- [ ] **Step 1: Write failing tests** in `LrcParserTest.kt`:
  - `parsesMinutesSecondsAndFraction`: `"[00:12.34]Hello"` → `[LrcLine(12340,"Hello")]`; `"[01:02]x"` → 62000; `"[00:10.5]a"` → 10500; `"[00:10.123]a"` → 10123.
  - `expandsMultipleTimestamps`: `"[00:01.00][00:05.00]La"` → two lines at 1000 and 5000.
  - `sortsOutOfOrderLines`: lines given at 5 s then 1 s come out 1 s first.
  - `ignoresTagLinesAndGarbage`: `"[ar:Someone]\n[ti:Song]\nnot a lyric\n[00:01.00]ok"` → one line `ok`.
  - `keepsBlankTextLines`: `"[00:03.00]"` → `LrcLine(3000, "")`.
  - `currentIndexCases`: before the first stamp → -1; exactly on a stamp → that index; between stamps → the earlier one; after the last → last index; empty list → -1.
- [ ] **Step 2: Run** `.\gradlew.bat :app:testDebugUnitTest --tests "*LrcParserTest"`. Expected: FAIL (unresolved reference).
- [ ] **Step 3: Implement** the two functions plus `Models.kt`. `currentIndex` uses binary search.
- [ ] **Step 4: Run the same command.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): LRC parsing and line lookup`.

### Task 4: Position clock and card visibility

**Files:**
- Create: `playback/PositionClock.kt`
- Test: `playback/PositionClockTest.kt`

**Interfaces:**
- Consumes: `PlaybackSnapshot`, `Track` (Task 3).
- Produces: `class PositionClock { fun set(positionMs: Long, speed: Float, atMs: Long); fun positionAt(nowMs: Long): Long }` and `fun cardVisible(s: PlaybackSnapshot, nowMs: Long, hideAfterPauseMs: Long = 10_000): Boolean`.

- [ ] **Step 1: Write failing tests:**
  - `advancesWhilePlaying`: `set(10_000, 1f, atMs=1_000)`; `positionAt(3_500) == 12_500`.
  - `respectsSpeed`: speed 2f, 1 s later → +2000.
  - `frozenWhenPaused`: speed 0f → same position at any `nowMs`.
  - `neverNegative`: `set(0, 1f, 5_000)`; `positionAt(4_000) == 0`.
  - `visibleWhilePlaying`; `visibleUntilTenSecondsAfterPause` (paused at `updatedAtMs=0`: visible at 9_999, not at 10_000); `hiddenWhenNoTrack`.
- [ ] **Step 2: Run** `--tests "*PositionClockTest"`. Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): position clock and card visibility`.

### Task 5: Lyrics fetch, cache and stale-safe loader

**Files:**
- Create: `lyrics/LrclibFetcher.kt`, `lyrics/LyricsCache.kt`, `lyrics/LyricsRepository.kt`, `lyrics/LyricsLoader.kt`
- Test: `lyrics/LrclibParseTest.kt`, `lyrics/LyricsCacheTest.kt`, `lyrics/LyricsLoaderTest.kt`

**Interfaces:**
- Consumes: `Track`, `LyricsResult`, `LrcLine`, `parseLrc` (Task 3).
- Produces:
  - `interface LyricsFetcher { suspend fun fetch(track: Track): LyricsResult }`
  - `fun parseLrclib(body: String): LyricsResult` (one JSON object with `syncedLyrics`, `plainLyrics`; non-null non-blank `syncedLyrics` → `Synced`, only plain → `NotSynced`, neither → `NotFound`, bad JSON → `Error`)
  - `fun parseLrclibSearch(body: String): LyricsResult` (JSON array; first element with synced lyrics wins; none → `NotSynced` if any has plain, else `NotFound`)
  - `class LrclibFetcher : LyricsFetcher` (`GET /api/get?track_name&artist_name&duration` with duration only when known, then on 404 `GET /api/search?track_name&artist_name`; `Dispatchers.IO`, 10 s timeouts, network failure → `Error`)
  - `class LyricsCache(dir: File) { fun get(track: Track): String?; fun put(track: Track, lrc: String) }`
  - `class LyricsRepository(fetcher: LyricsFetcher, cache: LyricsCache) : LyricsFetcher` (cache hit returns `Synced(parseLrc(cached))`; only `Synced` results are stored, as raw LRC text, so the repository needs the raw text: `LrclibFetcher` exposes `Synced` lines, so store them re-serialized as `[mm:ss.xx]text` lines)
  - `sealed interface LyricsUiState { data object Idle; data object Loading; data class Ready(val lines: List<LrcLine>); data object Missing }` and `class LyricsLoader(scope: CoroutineScope, fetcher: LyricsFetcher) { val state: StateFlow<LyricsUiState>; fun load(track: Track?) }`

- [ ] **Step 1: Write failing tests:**
  - `LrclibParseTest`: synced body → `Synced` with the expected first line; plain-only → `NotSynced`; `{"statusCode":404}` and `[]` → `NotFound`; `"not json"` → `Error`; search array whose first item has only plain and second has synced → `Synced` from the second.
  - `LyricsCacheTest` (JUnit `TemporaryFolder`): `put` then `get` returns the text; a different track returns null; the same title/artist with a different duration is a separate entry.
  - `LyricsLoaderTest` (`runTest`, fake `LyricsFetcher` with per-track `CompletableDeferred`): `load(null)` → `Idle`; `load(a)` → `Loading` then `Ready`; `Missing` for `NotFound`, `NotSynced` and `Error`; **stale response**: `load(a)`, `load(b)`, complete b, then complete a → state is b's `Ready`, never a's.
- [ ] **Step 2: Run** `--tests "*lyrics*"`. Expected: FAIL.
- [ ] **Step 3: Implement.** Use kotlinx-serialization `Json.parseToJsonElement` for the parsers, so no Android classes are needed in unit tests. Cache file name: SHA-256 hex of `"$artist|$title|$durationMs"` lowercased. `LyricsLoader.load` cancels its previous job before starting a new one.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Commit** `feat(android): LRCLIB fetch, cache and loader`.

### Task 6: Media session to NowPlayingSource, plus fake player

**Files:**
- Create: `media/NowPlayingStore.kt`, `media/SessionMapping.kt`, `media/MediaListenerService.kt`, `app/src/debug/kotlin/app/kron/lyrics/debug/FakePlayerActivity.kt`
- Modify: `AndroidManifest.xml` (listener service with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`, `INTERNET`; debug manifest in `app/src/debug/AndroidManifest.xml` for the fake activity)
- Test: `media/SessionMappingTest.kt`

**Interfaces:**
- Consumes: `PlaybackSnapshot`, `Track`, `NowPlayingSource` (Task 3).
- Produces:
  - `object NowPlayingStore : NowPlayingSource { fun publish(s: PlaybackSnapshot); val EMPTY: PlaybackSnapshot }` (empty = `track=null, playing=false`)
  - `data class SessionInfo(val packageName: String, val playing: Boolean, val lastUpdateMs: Long)`
  - `fun pickSession(candidates: List<SessionInfo>): SessionInfo?` (playing sessions first, newest `lastUpdateMs` wins; if none playing, newest paused; empty → null)
  - `fun toSnapshot(title: String?, artist: String?, durationMs: Long, state: Int, positionMs: Long, speed: Float, updateTimeMs: Long): PlaybackSnapshot` (blank title → `track=null`; `state` is a `PlaybackState.STATE_*`; `playing` for `STATE_PLAYING`/`STATE_BUFFERING`; speed forced to 0 when not playing and to 1 when playing with speed 0; missing artist becomes `""`)

- [ ] **Step 1: Write failing tests** in `SessionMappingTest.kt`: `pickSession` — one playing and one newer paused → the playing one; two playing → the newer; two paused → the newer; empty → null. `toSnapshot` — playing at 42 s with speed 1 → `playing=true, speed=1f, positionMs=42000`; paused → `playing=false, speed=0f`; null title → `track==null`; playing with speed 0 → speed 1; `durationMs=-1` kept as unknown.
- [ ] **Step 2: Run** `--tests "*SessionMappingTest"`. Expected: FAIL.
- [ ] **Step 3: Implement the two pure functions and `NowPlayingStore`.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Implement `MediaListenerService`.** In `onListenerConnected`, get `MediaSessionManager`, call `getActiveSessions(ComponentName(this, MediaListenerService::class.java))`, register `OnActiveSessionsChangedListener` and a `MediaController.Callback` per controller (metadata and playback-state changes). On any change build `SessionInfo`s, `pickSession`, then `toSnapshot` from that controller and `NowPlayingStore.publish`. Publish `EMPTY` when no session. Unregister everything in `onListenerDisconnected` and publish `EMPTY`.
- [ ] **Step 6: Implement `FakePlayerActivity` (debug only).** It creates a `MediaSession` with title "Never Gonna Give You Up", artist "Rick Astley", duration 213000, shows Play and Pause buttons, and sets `PlaybackState` (PLAYING from position 0 at speed 1, then PAUSED at the current position). It plays no audio.
- [ ] **Step 7: Verify on the emulator.** Install, grant access with `adb shell cmd notification allow_listener app.kron.lyrics/app.kron.lyrics.media.MediaListenerService`, launch the fake player, press Play, and add a temporary `Log.d("kron", snapshot.toString())` in the service's publish path. `adb logcat -s kron` shows the right title, artist and `playing=true`; Pause shows `playing=false`. Remove the log line.
- [ ] **Step 8: Commit** `feat(android): read the active media session`.

### Task 7: Glass card UI and card layout

**Files:**
- Create: `overlay/CardLayout.kt`, `overlay/GlassCard.kt`
- Test: `overlay/CardLayoutTest.kt`

**Interfaces:**
- Consumes: `LrcLine`, `LyricsUiState` (Tasks 3, 5).
- Produces:
  - `data class CardLines(val previous: String, val current: String, val next: String)`
  - `fun visibleLines(lines: List<LrcLine>, index: Int): CardLines` (index -1 → `previous=""`, `current=""`, `next=lines[0].text`; last index → `next=""`; out-of-range neighbors are `""`)
  - `fun cardWidthPx(screenWidthPx: Int): Int` (45% rounded)
  - `@Composable fun GlassCard(title: String, artist: String, state: LyricsUiState, lines: CardLines, blurAvailable: Boolean)`

- [ ] **Step 1: Write failing tests:** `visibleLines` for a middle index, index -1, last index, and a one-line list; `cardWidthPx(1080) == 486`.
- [ ] **Step 2: Run** `--tests "*CardLayoutTest"`. Expected: FAIL.
- [ ] **Step 3: Implement** the two functions.
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Implement `GlassCard`.** Title line `"$title · $artist"` at ~9 sp, previous/next at 10 sp at 70% white, current at 12 sp bold in a bright accent. `Missing` shows "No lyrics found" in place of the lines. `Loading` and `Idle` show only the title line. Rounded 16 dp corners, thin 1 dp light border, background `Color.White` at ~16% alpha when `blurAvailable`, otherwise `Color(0xCC121420)`. Text shadow for legibility. Add `@Preview`s for both backgrounds.
- [ ] **Step 6: Commit** `feat(android): glass card UI`.

### Task 8: Overlay window, drag and service

**Files:**
- Create: `overlay/CardPositionStore.kt`, `overlay/OverlayController.kt`, `overlay/OverlayService.kt`
- Modify: `overlay/CardLayout.kt` (add `Pos`, `clampToScreen`), `media/MediaListenerService.kt` (start/stop the overlay), `AndroidManifest.xml`
- Test: `overlay/ClampTest.kt`

**Interfaces:**
- Consumes: `NowPlayingStore`, `PositionClock`, `cardVisible`, `LyricsLoader`, `LyricsRepository`, `LrclibFetcher`, `visibleLines`, `currentIndex`, `cardWidthPx`, `GlassCard`.
- Produces:
  - `data class Pos(val x: Int, val y: Int)`
  - `fun clampToScreen(p: Pos, cardW: Int, cardH: Int, screenW: Int, screenH: Int): Pos` (keeps the whole card on screen; a card larger than the screen pins to 0)
  - `class CardPositionStore(prefs: SharedPreferences) { fun load(): Pos?; fun save(p: Pos) }`

- [ ] **Step 1: Write failing tests** in `ClampTest.kt`: negative x/y → 0; x past the right edge → `screenW - cardW`; y past the bottom → `screenH - cardH`; an in-bounds position is unchanged; a card wider than the screen → x 0.
- [ ] **Step 2: Run** `--tests "*ClampTest"`. Expected: FAIL.
- [ ] **Step 3: Implement `Pos` and `clampToScreen`.**
- [ ] **Step 4: Run.** Expected: PASS.
- [ ] **Step 5: Implement `OverlayController`.** Adds one `ComposeView` through `WindowManager` with `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL`, `PixelFormat.TRANSLUCENT`, gravity top-start, `width = cardWidthPx(screen width)`, `height = WRAP_CONTENT`, so only the card is touchable. Blur: set `FLAG_BLUR_BEHIND` and `blurBehindRadius = 24` while `windowManager.isCrossWindowBlurEnabled` is true, and follow changes with `addCrossWindowBlurEnabledListener`. Drag with a pointer-input drag gesture on the card; update `LayoutParams.x/y` through `clampToScreen` on every move, save the position on release, and clamp again when the configuration changes (rotation). A `ComposeView` outside an activity needs a lifecycle: add a small `LifecycleOwner + SavedStateRegistryOwner + ViewModelStoreOwner` holder and set it with `ViewTreeLifecycleOwner.set`, `ViewTreeSavedStateRegistryOwner.set` and `ViewTreeViewModelStoreOwner.set` before adding the view.
- [ ] **Step 6: Implement `OverlayService`.** Foreground service (low-importance channel "Lyrics overlay", type `specialUse` with the required `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property; permissions `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`). It collects `NowPlayingStore.snapshot`, calls `LyricsLoader.load(snapshot.track)` when the track changes, and ticks about every 100 ms while playing to compute `currentIndex(lines, clock.positionAt(now))`. It shows or hides the card with `cardVisible` and calls `stopSelf()` when `track == null` or `Settings.canDrawOverlays` becomes false.
- [ ] **Step 7: Start and stop from `MediaListenerService`.** When a snapshot has `track != null && playing` and `Settings.canDrawOverlays(this)`, call `startForegroundService`. If this throws `ForegroundServiceStartNotAllowedException`, host `OverlayController` and the loop directly inside `MediaListenerService` instead and drop `OverlayService`. Note which path you took in the commit message.
- [ ] **Step 8: Verify on the emulator.** `adb shell appops set app.kron.lyrics SYSTEM_ALERT_WINDOW allow`, start the fake player and press Play. Expected: the card appears over the fake player with the right title and synced lines (screenshot with `adb exec-out screencap -p > shot.png` and read it). Drag it with `adb shell input swipe`; it follows and stays put after a restart of the fake player.
- [ ] **Step 9: Commit** `feat(android): floating lyrics card`.

### Task 9: Setup screen

**Files:**
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `@Composable fun SetupScreen(listenerGranted: Boolean, overlayGranted: Boolean, onOpenListener: () -> Unit, onOpenOverlay: () -> Unit)` and an empty `@Composable fun ListenSlot()` placeholder above the permission rows.

- [ ] **Step 1: Implement** the screen: two rows, "Notification access" and "Display over other apps", each with a granted/not granted label and a button that opens `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` or `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` (with `package:app.kron.lyrics`). Recompute both states in `onResume` (`NotificationManagerCompat.getEnabledListenerPackages`, `Settings.canDrawOverlays`). Short explanation text under each row. Request `POST_NOTIFICATIONS` once on launch (Android 13+).
- [ ] **Step 2: Verify on the emulator.** Fresh install shows both rows as not granted; granting each through the real Settings screens flips the labels when you return. Screenshot both states.
- [ ] **Step 3: Commit** `feat(android): setup screen`.

### Task 10: Release signing and CI

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: repo secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.

- [ ] **Step 1: Add the release signing config.** Read the four values from environment variables, else from `android/keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`); if neither is present, leave `release` unsigned so debug and test builds still work.
- [ ] **Step 2: Write `android.yml`.** Trigger `on: push: tags: ['android-v*']`, `permissions: contents: write`, job on `ubuntu-latest` with `actions/checkout@v5`, `actions/setup-java@v4` (Temurin 17), a step that base64-decodes `ANDROID_KEYSTORE_BASE64` into `$RUNNER_TEMP/kron-release.jks` and exports `ANDROID_KEYSTORE_PATH`, `./gradlew assembleRelease` in `android/`, then `softprops/action-gh-release@v2` attaching `android/app/build/outputs/apk/release/*.apk` and naming the release `kRon Android ${{ github.ref_name }}`. Make the release step `prerelease: true`.
- [ ] **Step 3: Confirm the Windows workflow is untouched.** `release.yml` triggers on `v*`, and `android-v0.1.0` does not start with `v`. `git diff --stat main -- .github/workflows/release.yml` prints nothing.
- [ ] **Step 4: Verify locally.** `.\gradlew.bat :app:assembleRelease` with no signing variables produces `app-release-unsigned.apk`.
- [ ] **Step 5: Commit** `ci(android): signed release APK on android-v* tags`.
- [ ] **Step 6: Verify the signed build.** Pushing a tag is a public action: ask the user first, then push `android-v0.1.0`. When the run finishes, download the APK into its own new directory and run `apksigner verify --print-certs` on it. Expected: the certificate owner is `CN=Ronzo`.

### Task 11: End-to-end check against "Done means"

No new code, only fixes for what this finds. Run on the emulator with the fake player.

- [ ] **Step 1: Card shows synced lyrics.** Play in the fake player; screenshot at about 20 s. Expected: current line changes in step with the song; card is about 45% of the screen width.
- [ ] **Step 2: Touches pass through.** `adb shell input tap` on the fake player's Pause button at a point outside the card. Expected: playback pauses (the tap reached the app underneath). A tap on the card itself must not reach it.
- [ ] **Step 3: Fade after pause.** After Pause, the card is still there at 8 s and gone by 12 s; Play brings it back.
- [ ] **Step 4: Stop.** Close the fake player. Expected: card gone and `adb shell dumpsys activity services app.kron.lyrics` shows no running service.
- [ ] **Step 5: Revoked permission.** While the card is up, `adb shell appops set app.kron.lyrics SYSTEM_ALERT_WINDOW deny`. Expected: the card disappears, the service stops, and `adb logcat -d | findstr FATAL` shows nothing from `app.kron.lyrics`. Repeat with `adb shell cmd notification disallow_listener app.kron.lyrics/app.kron.lyrics.media.MediaListenerService`.
- [ ] **Step 6: Rotation.** Drag the card to the bottom-right, then `adb shell settings put system user_rotation 1` with `accelerometer_rotation 0`. Expected: the card is still fully on screen.
- [ ] **Step 7: Windows app unaffected.** `git diff --stat main -- src src-tauri .github/workflows/release.yml` prints nothing.
- [ ] **Step 8: Report** each check's result honestly, including any that failed or could not be run.
