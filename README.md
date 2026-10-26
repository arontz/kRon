<div align="center">

# kRon

**Music, synced lyrics and a system overview, right on the Windows taskbar.**

*(the k is silent)*

![Platform](https://img.shields.io/badge/platform-Windows%2010%20%7C%2011-0078d4)
![Built with](https://img.shields.io/badge/built%20with-Tauri%202%20%2B%20Rust-dea584)
![Status](https://img.shields.io/badge/status-early%20development-orange)

</div>

---

## Download

**[⬇ Download the latest kRon installer](https://github.com/arontz/kRon/releases/latest)**

1. Open the link and, under **Assets**, click **`kRon_x.y.z_x64-setup.exe`**.
2. Run it. kRon appears on the taskbar next to your system tray.
3. Play something in Spotify, a browser or any media app.

Needs Windows 10 or 11. If Windows says "Windows protected your PC", click **More info → Run anyway**, because the installer isn't code-signed yet.

> You can ignore the *Source code* files in the Assets list; they're only for developers.

---

## What it does

kRon puts a small pill next to your system tray. It shows what's playing, and hovering it opens a panel above the taskbar.

| | |
|---|---|
| **Taskbar pill** | Album art, title, artist and prev / play / next, always visible. Switches to a CPU / RAM / internet readout when you pick the System page. |
| **Hover panel** | Opens after a short hover and closes when you leave. Side arrows switch between **Now playing** and **System**. |
| **Synced lyrics** | Shown on the desktop, on the taskbar, or turned off. Works with any player. |
| **System overview** | CPU with a live graph, memory used, and download / upload speed. |
| **Move it anywhere** | Drag the pill off a crowded taskbar. Drop it at a left or right screen edge and it becomes a narrow vertical column. |
| **Calendar flyout** | Optionally move kRon off the taskbar entirely: a now-playing card slides in above the Windows notifications when you open the clock. |

### Now playing

- Seek bar, shuffle, repeat and play controls
- Lyric preview with clickable lines that jump to that part of the song
- Works with anything that reports to Windows media controls: Spotify, Apple Music, browsers, Media Player…

### Lyrics

Pick where lyrics appear: **Off**, **Desktop** or **Taskbar**.

- **Desktop**: 3 or 5 lines in the bottom-right corner with no background. Sung words light up, and clicks pass through.
- **Taskbar**: the current line replaces the title in the pill, scrolling sideways if it's too long.
- Hidden automatically during fullscreen games, videos and presentations.

**Customizing the desktop lyrics.** Click **Customize** in the panel, choose it from the tray menu, or hold **Ctrl** and hover over the lyrics. A toolbar lets you:

- drag them anywhere on screen
- change the text size
- switch between 3 and 5 lines
- turn a frosted backdrop on or off
- shift the timing earlier or later, 0.25 s at a time

Click **Done** to lock them again. Everything is remembered.

### Where lyrics come from

kRon looks in this order:

1. **Your own files** in `Music\kRon Lyrics`
2. **Its saved copy** of lyrics it already downloaded
3. **[LRCLIB](https://lrclib.net)**, a free, open database of synced lyrics

Name a file `Artist - Title.lrc` (or `.txt`, or just `Title.lrc`) and kRon uses it, picking up edits within a few seconds. Synced lines look like this:

```
[01:23.45] Words that start at 1 minute 23 seconds
```

The buttons on the panel's lyrics card help with this:

| Button | What it does |
|---|---|
| Search | Googles the lyrics for the current track |
| Add / Edit | Creates the lyrics file for the current track and opens it in Notepad |
| Folder | Opens your lyrics folder |
| Reload | Forgets the saved copy and looks the track up again |

### Moving the pill

Click the six-dot move icon in the top-right corner of the hover panel (or choose **Move pill…** from the tray menu), then drag the pill by its handle anywhere on the screen. **Reset** puts it back beside the tray, and **Done** locks it. The position is remembered.

- Drag it to within a few pixels of the **left or right screen edge** and it docks as a narrow column: art, controls and lyrics button stacked, with the track shown as a tooltip. Drag it away to undock.
- **Lyrics on taskbar** needs the wide pill, so that option is unavailable while the pill is docked to a side edge.

### In the calendar flyout

Click the calendar icon in the hover panel's top-right corner (or tick **Show in calendar flyout** in the tray menu) to move kRon off the taskbar. Whenever you open the Windows clock / notification centre, a now-playing card with controls and the current lyric line slides in above the Notifications card. Use the taskbar icon on the card to bring the pill back.

### Tray menu

Right-click the kRon icon for **Lyrics off / on desktop / on taskbar**, **Customize lyrics…**, **Move pill…**, **Show in calendar flyout**, **Start with Windows** and **Quit**.

---

## Development

### Requirements

- Windows 10 or 11 with [WebView2](https://developer.microsoft.com/microsoft-edge/webview2/) (already installed on most PCs)
- [Node.js](https://nodejs.org)
- [Rust](https://rustup.rs) with the MSVC toolchain
- Visual Studio C++ Build Tools

### Run it

```bash
npm install
npm run dev
```

`npm run dev` rebuilds and restarts kRon whenever you change a file.

> **Note:** If `cargo` isn't found right after installing Rust, open a new terminal so it picks up the updated PATH.

### Build an installer

```bash
npm run build
```

The installer ends up in `src-tauri/target/release/bundle/nsis`.

> **Note:** Windows Smart App Control can block release builds on your machine. Building on GitHub (below) avoids this.

### Publish a release

Releases are built by GitHub Actions, not on your PC.

1. Bump the version in `package.json`, `src-tauri/Cargo.toml` and `src-tauri/tauri.conf.json`
2. Commit and push
3. Tag and push the tag:

   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

A few minutes later the installer appears under [Releases](https://github.com/arontz/kRon/releases). The first build takes longer than later ones.

### Project layout

```
src-tauri/src/
├─ main.rs      app setup, settings, tray menu, commands
├─ media.rs     Windows media controls: track, timeline, artwork, controls
├─ lyrics.rs    lyrics lookup, disk cache, LRC parsing
├─ stats.rs     CPU / memory / network sampling (only while shown)
└─ shell.rs     pill placement and docking, hover open/close, notification-centre card, fullscreen detection

src/
├─ pill.*       the taskbar pill
├─ flyout.*     the hover panel
├─ dock.*       the card shown above the notification centre
├─ overlay.html the desktop lyrics
└─ common.*     shared state and styles
```

### Where kRon keeps things

| What | Location |
|---|---|
| Settings | `%APPDATA%\app.kron.taskbar\settings.json` |
| Downloaded lyrics | `%LOCALAPPDATA%\app.kron.taskbar\lyrics` |
| Your lyrics files | `Music\kRon Lyrics` |

---

## Roadmap

- [ ] More System metrics: GPU, disk, battery, temperature
- [ ] Real acrylic blur on the hover panel
- [ ] Top and bottom edge docking, auto-hide taskbar and multiple monitors
- [ ] In-app update check

## Credits

Lyrics from [LRCLIB](https://lrclib.net). Inspired by [Nusa](https://rama2555.github.io/nusa/).
