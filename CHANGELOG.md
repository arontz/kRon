# Changelog

Each release's notes on GitHub come from the matching section below. Add a new `## vX.Y.Z` section on top before tagging a release.

## Next

- **Desktop lyrics stay visible while the hover panel is open.** They used to disappear when you pointed at the pill; now the panel opens in front of them.
- **New logo:** an equalizer whose lit segments spell kR, with a plain k and a mint-highlighted R, like the lyrics highlight.

## v0.4.0

- **Search for lyrics window.** The magnifier button on the lyrics card (and **Search for lyrics…** in the tray menu) now opens its own window, like foobar2000's: edit Title / Album / Artist, search several sources at once, see which results are timestamped, preview the lyrics, then **Apply** or **OK** to save them for the song that's playing. Results come from QQ Music and LRCLIB at once, timestamped ones first (QQ Music isn't an official public service, so it may stop working). **Search the web** is still there as a button in the window.
- **"No lyrics found · Search" hint.** When a song has no lyrics, a faint hint shows on the desktop where the lyrics would be; click it to open the search window.
- **The line being sung is highlighted as a whole**, in your highlight colour, on the desktop and in the taskbar lyric line. (Highlighting word by word needs word-level timing, which lyric files don't have.)

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
