// Shared state for all kRon windows: one snapshot from Rust, then live events.
(function () {
  const { invoke } = window.__TAURI__.core;
  const { listen } = window.__TAURI__.event;

  const ICONS = {
    note: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>',
    prev: '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h2v14H6zM20 5v14L9 12z"/></svg>',
    next: '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M16 5h2v14h-2zM4 5v14l11-7z"/></svg>',
    play: '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>',
    pause: '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/></svg>',
    lyrics: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h11M4 17h7"/></svg>',
    pulse: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12h4l3-8 4 16 3-8h4"/></svg>',
    shuffle: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/></svg>',
    repeat: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 2l4 4-4 4"/><path d="M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4"/><path d="M21 13v2a3 3 0 0 1-3 3H3"/></svg>',
    move: '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="9" cy="6" r="1.6"/><circle cx="15" cy="6" r="1.6"/><circle cx="9" cy="12" r="1.6"/><circle cx="15" cy="12" r="1.6"/><circle cx="9" cy="18" r="1.6"/><circle cx="15" cy="18" r="1.6"/></svg>',
    calendar: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M3 10h18M8 3v4M16 3v4"/></svg>',
    taskbar: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M3 15h18"/><circle cx="8" cy="17.5" r=".6"/><circle cx="12" cy="17.5" r=".6"/></svg>',
    search: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="M20 20l-4.5-4.5"/></svg>',
    plus: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
    edit: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13.5 6.5l4 4"/></svg>',
    folder: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/></svg>',
    reload: '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/></svg>',
    left: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>',
    right: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg>',
  };

  const state = {
    media: { active: false, title: '', artist: '', album: '', source: '', playing: false, position_ms: 0, duration_ms: 0, updated_at_ms: 0, shuffle: false, repeat: 'none', track_key: '' },
    artwork: '',
    lyrics: { track_key: '', status: 'idle', lines: [] },
    stats: { cpu: 0, cores: 0, cpu_history: [], mem_used: 0, mem_total: 0, down: 0, up: 0 },
    settings: { lyrics_on: true, page: 'music', pill_side: '', dock_on: false, lyr_place: 'desktop', lyr_scale: 1, lyr_lines: 3, lyr_backdrop: false, lyr_offset_ms: 0, lyr_dx: 0, lyr_dy: 0 },
    flyoutOpen: false,
    overlayEdit: false,
    pillEdit: false,
    dockShown: false,
  };
  const subs = [];
  const notify = (what) => subs.forEach((f) => f(state, what));

  const K = {
    state,
    ICONS,
    invoke,
    on(fn) { subs.push(fn); },
    async init() {
      try {
        Object.assign(state, await invoke('get_state'));
      } catch (e) { console.error(e); }
      for (const ev of ['media', 'artwork', 'lyrics', 'stats', 'settings']) {
        listen(ev, (e) => { state[ev] = e.payload; notify(ev); });
      }
      listen('flyout', (e) => { state.flyoutOpen = e.payload; notify('flyout'); });
      listen('overlay_edit', (e) => { state.overlayEdit = e.payload; notify('overlay_edit'); });
      listen('pill_edit', (e) => { state.pillEdit = e.payload; notify('pill_edit'); });
      listen('dock_vis', (e) => { state.dockShown = e.payload; notify('dock_vis'); });
      notify('all');
    },
    cmd(action, ms) {
      if (action === 'toggle') {
        // Feels instant; the next poll confirms it.
        const m = state.media;
        m.position_ms = K.position();
        m.updated_at_ms = Date.now();
        m.playing = !m.playing;
        notify('media');
      }
      invoke('media_cmd', { action, ms: ms ?? null });
    },
    setLyricsOn(on) { invoke('set_lyrics_on', { on }); },
    setPage(page) { invoke('set_page', { page }); },
    /** Patch desktop-lyrics settings: { scale, lines, backdrop, offset_ms, dx, dy, save }. */
    setLyr(patch) {
      const s = state.settings;
      const map = { place: 'lyr_place', scale: 'lyr_scale', lines: 'lyr_lines', backdrop: 'lyr_backdrop', offset_ms: 'lyr_offset_ms', dx: 'lyr_dx', dy: 'lyr_dy' };
      for (const [k, v] of Object.entries(patch)) if (map[k]) s[map[k]] = v; // optimistic
      notify('settings');
      const args = { place: null, scale: null, lines: null, backdrop: null, offsetMs: null, dx: null, dy: null, save: true };
      for (const [k, v] of Object.entries(patch)) args[k === 'offset_ms' ? 'offsetMs' : k] = v;
      invoke('set_lyr', args);
    },
    setOverlayEdit(on) { invoke('set_overlay_edit', { on }); },
    setPillEdit(on) { invoke('set_pill_edit', { on }); },
    /** Drag the pill by (dx, dy) logical px; `commit` when the drag ends so it's saved. */
    movePillBy(dx, dy, commit) { invoke('move_pill_by', { dx, dy, commit: !!commit }); },
    resetPill() { invoke('reset_pill'); },
    /** Show / hide the now-playing card above the Windows notification centre. */
    setDock(on) { invoke('set_dock_on', { on }); },
    /** Docked to a left / right screen edge as a vertical column. */
    isVertical() { return !!state.settings.pill_side; },
    position() {
      const m = state.media;
      if (!m.active) return 0;
      let p = m.position_ms + (m.playing ? Date.now() - m.updated_at_ms : 0);
      if (m.duration_ms > 0) p = Math.min(p, m.duration_ms);
      return Math.max(0, p);
    },
    /** Index of the line being sung, -1 before the first, or -1 for unsynced lyrics. */
    lineIndex(pos) {
      const lines = state.lyrics.lines;
      if (state.lyrics.status !== 'synced' || !lines.length) return -1;
      pos += state.settings.lyr_offset_ms || 0;
      let lo = 0, hi = lines.length - 1, ans = -1;
      while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (lines[mid].t <= pos + 150) { ans = mid; lo = mid + 1; } else hi = mid - 1;
      }
      return ans;
    },
    /** 0..1 estimate of how far through line `idx` the singer is (we only have line timing). */
    lineProgress(idx, pos) {
      const lines = state.lyrics.lines;
      if (idx < 0 || !lines[idx]) return 0;
      const p = pos + (state.settings.lyr_offset_ms || 0);
      const start = lines[idx].t;
      const end = lines[idx + 1] ? lines[idx + 1].t : start + 4000;
      return Math.min(1, Math.max(0, (p - start) / Math.max(400, (end - start) * 0.85)));
    },
    lineText(i) {
      const l = state.lyrics.lines[i];
      if (!l) return '';
      return l.text.trim() || '• • •';
    },
    fmtTime(ms) {
      const s = Math.max(0, Math.floor(ms / 1000));
      return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    },
    fmtRate(bps) {
      if (bps >= 1e6) return `${(bps / 1e6).toFixed(1)} MB/s`;
      if (bps >= 1e3) return `${Math.round(bps / 1e3)} KB/s`;
      return `${bps} B/s`;
    },
    /** Short form for the narrow vertical pill: "4.2M", "320K". */
    fmtRateShort(bps) {
      if (bps >= 1e6) return `${(bps / 1e6).toFixed(1)}M`;
      if (bps >= 1e3) return `${Math.round(bps / 1e3)}K`;
      return `${bps}B`;
    },
    fmtGB(bytes) { return (bytes / 1073741824).toFixed(1); },
    esc(s) {
      return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    },
    setArt(el, url) {
      el.style.backgroundImage = url ? `url("${url}")` : '';
      el.classList.toggle('has-img', !!url);
    },
  };

  window.K = K;
})();
