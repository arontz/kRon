# Changelog

Each release's notes on GitHub come from the matching section below. Add a new `## vX.Y.Z` section on top before tagging a release.

## v0.3.0

- **Themes.** New **Appearance** page in the hover panel (use the side arrows). **Auto** follows your Windows taskbar: dark text on a light taskbar, light text on a dark one, and it switches when you change Windows' theme. Or force **Dark** / **Light**.
- **Accent colour:** six presets plus a slider for any hue. Colours that would be hard to read on the current background are adjusted automatically.
- **The pill no longer disappears when the Start menu is open.** Windows 11 moves the taskbar above ordinary windows while Start is open; the pill is now owned by the taskbar so it stays visible.
- **Lyrics colours:** pick the lyrics text colour and the highlight colour for the words being sung, with a live preview. (Desktop lyrics use the colours as chosen, since they sit on your wallpaper.)

## v0.2.1

- **Check for updates…** in the tray menu: asks GitHub for the latest release and offers to open the download page when there's a newer one. The tray menu also shows the version you're running.

## v0.2.0

- **Move the pill anywhere.** Click the six-dot move icon in the hover panel (or **Move pill…** in the tray menu) and drag it off a crowded taskbar. The position is remembered.
- **Docks to a side edge.** Drop the pill at the left or right screen edge and it becomes a narrow vertical column with the track as a tooltip. Drag it away to undock.
- **Calendar flyout mode.** Optionally move kRon off the taskbar: a now-playing card flies in above the Windows notifications when you open the clock, and flies back out when it closes.
- **Lyrics where you want them:** off, on the desktop, or one line in the taskbar pill. **Customize** the desktop lyrics: drag them, resize the text, 3 or 5 lines, frosted backdrop, and timing offset.
- **Your own lyrics.** Put `Artist - Title.lrc` in `Music\kRon Lyrics`, with Search / Add / Folder / Reload buttons on the lyrics card.
- The pill stays on top when you switch or minimize apps, and only hides for real fullscreen games and videos.

## v0.1.0

- First version: taskbar pill with music controls, hover panel with Now playing and System pages, synced desktop lyrics from LRCLIB, tray menu.
