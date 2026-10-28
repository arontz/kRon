// The website's stand-in for kRon's Rust side. The real interface files (copied from src/) run unmodified in iframes and call
// window.__TAURI__ (see shim.js); this hub answers those calls with sample songs and sample system stats and pushes the same
// events the app would, so everything on the page that looks like kRon is kRon's own code responding to the pointer.
// Songs, lyrics and numbers are made up for the demo.
(function () {
  const art = (a, b, c) => 'data:image/svg+xml;utf8,' + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="300"><rect width="300" height="300" fill="${a}"/><circle cx="210" cy="90" r="46" fill="${c}"/><path d="M0 220 L70 170 L130 210 L200 150 L300 230 V300 H0Z" fill="${b}"/></svg>`);

  const clone = (v) => (v === undefined ? v : JSON.parse(JSON.stringify(v)));
  const L = (rows) => rows.map(([t, text]) => ({ t, text }));
  const SONGS = [
    {
      key: 'midnight-drive', title: 'Midnight Drive', artist: 'Lumen Park', album: 'Night Shift', duration_ms: 214000,
      art: art('#d4603a', '#1d1740', '#ffd9a0'),
      lines: L([[8000, 'City lights are folding into rain'], [16500, 'We chase the signal down the avenue'], [25000, 'Every window hums a different tune'],
        [33500, 'Hold the night a little longer'], [42000, 'Say you will stay until the morning'], [50500, 'And we will never need a map'],
        [59000, 'Radio low, the engine sings'], [67500, 'Taillights drawing lines for us'], [76000, 'Take the long way, take the long way home'],
        [85000, 'Midnight drive, midnight drive'], [94000, 'Nothing here is in a hurry'], [102000, 'Nothing here but you and me']]),
    },
    {
      key: 'low-tide-letters', title: 'Low Tide Letters', artist: 'Orchard Hours', album: 'Salt and Paper', duration_ms: 187000,
      art: art('#2f7f9a', '#12303f', '#f4e7b8'),
      lines: L([[7000, 'I wrote your name along the shore'], [15000, 'The water read it, then it left'], [23000, 'Every letter sails away'],
        [31000, 'Back to where the quiet starts'], [39500, 'Low tide, low tide, bring them back'], [48000, 'Bring me one that says you are near'],
        [56500, 'Footprints filling up with sky'], [65000, 'Gulls above are keeping time'], [73500, 'I will write it all again'],
        [82000, 'Slower, so the sea can stay']]),
    },
    {
      key: 'glass-sunday', title: 'Glass Sunday', artist: 'Pale Cartographers', album: 'Quiet Atlas', duration_ms: 203000,
      art: art('#5b4fb8', '#1a1b3a', '#9be3c9'),
      lines: L([[9000, 'Sunday sits like glass on the table'], [17500, 'Light comes through the kettle steam'], [26000, 'Nobody has asked for anything'],
        [34500, 'The street below is learning how to rest'], [43000, 'Fold the map and let it lie'], [51500, 'We already know the way'],
        [60000, 'Slow clouds over a slow river'], [68500, 'Let the afternoon be long'], [77000, 'Glass Sunday, hold the light'],
        [86000, 'Hold it till the evening comes']]),
    },
    {
      key: 'paper-moons', title: 'Paper Moons', artist: 'Quiet Harbour', album: 'Small Hours', duration_ms: 198000,
      art: art('#3d8f86', '#10282d', '#f6d9a8'), lines: [],
    },
    {
      key: 'lantern-season', title: 'Lantern Season', artist: 'Harbour and Co', album: 'Small Hours', duration_ms: 176000,
      art: art('#c9863a', '#2a1a14', '#ffe9b8'), lines: [],
    },
  ];

  // What the search window finds for "Paper Moons" (made up). Anything else finds nothing, so the second song needs a file of your own.
  const PAPER_MOONS_LRC = [
    '[ar:Quiet Harbour]', '[ti:Paper Moons]', '[al:Small Hours]',
    '[00:09.00] Paper moons above the harbour', '[00:17.50] Folded small enough to keep', '[00:26.00] Every boat a quiet answer',
    '[00:34.50] To the questions of the deep', '[00:43.00] Hang them up along the railing', '[00:51.50] Let the tide run through the string',
    '[01:00.00] Paper moons above the harbour', '[01:08.50] Nothing here is ever lost',
  ].join('\n');
  const PAPER_MOONS_PLAIN = 'Paper moons above the harbour\nFolded small enough to keep\nEvery boat a quiet answer\nTo the questions of the deep';
  const HITS = [
    { source: 'LRCLIB', id: 'pm1', title: 'Paper Moons', artist: 'Quiet Harbour', album: 'Small Hours', synced: true, content: PAPER_MOONS_LRC },
    { source: 'QQ Music', id: 'pm2', title: 'Paper Moons', artist: 'Quiet Harbour', album: 'Small Hours', synced: null, content: null },
    { source: 'LRCLIB', id: 'pm3', title: 'Paper Moons (live)', artist: 'Quiet Harbour', album: 'Live at the Pier', synced: false, content: PAPER_MOONS_PLAIN },
  ];

  // Reads an .lrc file: "[mm:ss.xx] words" lines are synced; if there are none, plain lines are kept as unsynced.
  function parseLrc(text) {
    const synced = [], plain = [];
    for (const raw of String(text).split(/\r?\n/)) {
      const line = raw.trim();
      if (!line || line.startsWith('#')) continue;
      const stamps = [...line.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
      if (stamps.length) {
        const words = line.replace(/\[[^\]]*\]/g, '').trim();
        for (const m of stamps) {
          const frac = m[3] ? Number(m[3].padEnd(3, '0').slice(0, 3)) : 0;
          synced.push({ t: Number(m[1]) * 60000 + Number(m[2]) * 1000 + frac, text: words });
        }
      } else if (!/^\[[a-z]+:.*\]$/i.test(line)) plain.push({ t: 0, text: line });
    }
    synced.sort((a, b) => a.t - b.t);
    return synced.length ? { status: 'synced', lines: synced } : { status: plain.length ? 'plain' : 'none', lines: plain };
  }

  const baseSettings = () => ({
    lyrics_on: true, page: 'music', pill_side: '', dock_on: false, theme_mode: 'dark', accent: '#7fe0c6', lyr_text: '', lyr_hi: '',
    lyr_place: 'desktop', lyr_scale: 1, lyr_lines: 3, lyr_backdrop: false, lyr_offset_ms: 0, lyr_dx: 0, lyr_dy: 0,
  });

  const reduced = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const hubs = {};
  const presets = {};
  const toastFns = new Set();

  function makeHub(id, preset) {
    const listeners = {};
    const playlist = (preset.playlist || [0, 1, 2]).map((i) => SONGS[i]);
    let songIx = preset.track || 0;
    const song = () => playlist[songIx];
    const custom = {}; // lyrics the visitor found or wrote for a song
    let cpu = 34, down = 2.4e6, up = 3.1e5;
    const hist = Array.from({ length: 40 }, (_, i) => 30 + 12 * Math.sin(i / 4) + (i % 5));

    const hub = {
      id,
      flyoutOpen: !!preset.flyoutOpen,
      state: {
        media: {
          active: true, title: '', artist: '', album: '', source: 'Media Player', playing: preset.playing ?? !reduced,
          position_ms: preset.position_ms ?? 20500, duration_ms: 0, updated_at_ms: Date.now(), shuffle: false, repeat: 'none', track_key: '',
        },
        artwork: '',
        lyrics: { track_key: '', status: 'synced', source: 'lrclib', lines: [] },
        stats: { cpu: 34, cores: 8, cpu_history: hist, mem_used: 9.8 * 1073741824, mem_total: 16 * 1073741824, down, up },
        settings: Object.assign(baseSettings(), preset.settings || {}),
        overlayEdit: false, pillEdit: false, taskbarLight: false,
      },
      listen(ev, cb) {
        (listeners[ev] = listeners[ev] || new Set()).add(cb);
        if (ev === 'flyout' && hub.flyoutOpen) setTimeout(() => cb({ payload: true }), 0);
        return () => listeners[ev].delete(cb);
      },
      // Every listener gets its own copy, as a message from the Rust side would: the interface mutates its state optimistically.
      emit(ev, payload) { (listeners[ev] || []).forEach((cb) => cb({ payload: clone(payload) })); },
      loadSong(ix, position) {
        songIx = (ix + playlist.length) % playlist.length;
        const s = song(), m = hub.state.media;
        Object.assign(m, { title: s.title, artist: s.artist, album: s.album, duration_ms: s.duration_ms, track_key: s.key,
          position_ms: position ?? 0, updated_at_ms: Date.now() });
        hub.state.artwork = s.art;
        const mine = custom[s.key];
        hub.state.lyrics = mine ? Object.assign({ track_key: s.key }, mine)
          : s.lines.length ? { track_key: s.key, status: 'synced', source: 'lrclib', lines: s.lines }
          : { track_key: s.key, status: 'none', source: '', lines: [] };
        hub.emit('media', m); hub.emit('artwork', hub.state.artwork); hub.emit('lyrics', hub.state.lyrics);
      },
      position() {
        const m = hub.state.media;
        return Math.min(m.duration_ms, m.position_ms + (m.playing ? Date.now() - m.updated_at_ms : 0));
      },
      media(action, ms) {
        const m = hub.state.media;
        if (action === 'toggle') { m.position_ms = hub.position(); m.updated_at_ms = Date.now(); m.playing = !m.playing; }
        else if (action === 'next') return hub.loadSong(songIx + 1);
        else if (action === 'prev') return hub.loadSong(hub.position() > 4000 ? songIx : songIx - 1);
        else if (action === 'seek') { m.position_ms = Math.max(0, Math.min(m.duration_ms, ms || 0)); m.updated_at_ms = Date.now(); }
        else if (action === 'shuffle') m.shuffle = !m.shuffle;
        else if (action === 'repeat') m.repeat = { none: 'all', all: 'track', track: 'none' }[m.repeat] || 'none';
        hub.emit('media', m);
      },
      // Saves lyrics for the playing song the way the app does: as a file of the visitor's own, which then comes first.
      applyLyrics(content) {
        const s = song(), parsed = parseLrc(content);
        if (!parsed.lines.length) return null;
        custom[s.key] = { status: parsed.status, source: 'local', lines: parsed.lines };
        hub.state.lyrics = Object.assign({ track_key: s.key }, custom[s.key]);
        hub.emit('lyrics', hub.state.lyrics);
        return `${s.artist} - ${s.title}.${parsed.status === 'synced' ? 'lrc' : 'txt'}`;
      },
      song,
      settings(patch) { Object.assign(hub.state.settings, patch); hub.emit('settings', hub.state.settings); },
      invoke(cmd, a, win) {
        const s = hub.state;
        switch (cmd) {
          case 'get_state': return clone(Object.assign({}, s, { flyoutOpen: hub.flyoutOpen }));
          case 'media_cmd': hub.media(a.action, a.ms); return;
          case 'set_lyrics_on': hub.settings({ lyrics_on: !!a.on }); return;
          case 'set_page': hub.settings({ page: a.page }); return;
          case 'set_lyr': {
            const map = { place: 'lyr_place', scale: 'lyr_scale', lines: 'lyr_lines', backdrop: 'lyr_backdrop', offsetMs: 'lyr_offset_ms', dx: 'lyr_dx', dy: 'lyr_dy' };
            const patch = {};
            for (const [k, v] of Object.entries(a)) if (map[k] && v !== null && v !== undefined) patch[map[k]] = v;
            hub.settings(patch); return;
          }
          case 'set_theme': {
            const patch = {};
            if (a.mode != null) patch.theme_mode = a.mode;
            if (a.accent != null) patch.accent = a.accent;
            if (a.lyrText != null) patch.lyr_text = a.lyrText;
            if (a.lyrHi != null) patch.lyr_hi = a.lyrHi;
            hub.settings(patch); return;
          }
          case 'set_overlay_edit': s.overlayEdit = !!a.on; hub.emit('overlay_edit', s.overlayEdit); return;
          case 'pill_size': case 'overlay_size': case 'flyout_size': case 'dock_size': {
            const el = win && win.frameElement;
            if (el && el.dataset.autosize !== 'off') {
              if (a.w) el.style.width = a.w + 'px';
              if (a.h) el.style.height = a.h + 'px';
            }
            return;
          }
          case 'lyrics_action': {
            const ui = window.KronDemo.ui || {};
            if (a.action === 'search' && ui.openSearch) { ui.openSearch(id); return; }
            if (a.action === 'add' && ui.openEditor) { ui.openEditor(id, hub); return; }
            if (a.action === 'reload') { toastFns.forEach((f) => f(hub.state.lyrics.status === 'none' ? 'Checked again: nothing is saved for this song yet.' : 'Checked again: lyrics found.')); return; }
            toastFns.forEach((f) => f(a.action === 'search_web' ? 'In the app this opens a web search for the lyrics in your browser.' : 'That part works with files and windows on your PC, so it only runs in the installed app.'));
            return;
          }
          case 'lyrics_search': {
            const q = `${a.title || ''}`.toLowerCase();
            return /paper moon/.test(q) ? clone(HITS.map((x) => Object.assign({}, x, { content: null }))) : [];
          }
          case 'lyrics_fetch': {
            const hit = HITS.find((x) => x.id === a.id);
            return hit ? (hit.id === 'pm2' ? PAPER_MOONS_LRC : hit.content) : '';
          }
          case 'lyrics_apply': {
            const name = hub.applyLyrics(a.content);
            if (!name) throw new Error('That file has no lyrics in it.');
            return name;
          }
          case 'close_lyrics_search': {
            const ui = window.KronDemo.ui || {};
            if (ui.closePanel) ui.closePanel(id);
            return;
          }
          case 'set_pill_edit': case 'set_dock_on': case 'move_pill_by': case 'reset_pill':
            toastFns.forEach((f) => f('That part works with files and windows on your PC, so it only runs in the installed app.'));
            return;
          default: return;
        }
      },
    };

    hub.loadSong(songIx, preset.position_ms ?? 20500);
    hub.state.media.playing = preset.playing ?? !reduced;

    setInterval(() => {
      const m = hub.state.media;
      if (m.playing && hub.position() >= m.duration_ms - 300) hub.loadSong(songIx + 1);
    }, 500);
    setInterval(() => {
      cpu = Math.max(8, Math.min(78, cpu + (Math.random() - 0.5) * 14));
      down = Math.max(5e4, Math.min(8e6, down * (0.6 + Math.random() * 0.9)));
      up = Math.max(2e4, Math.min(1.2e6, up * (0.6 + Math.random() * 0.9)));
      hist.push(cpu); hist.shift();
      Object.assign(hub.state.stats, { cpu: Math.round(cpu), cpu_history: hist.slice(), down: Math.round(down), up: Math.round(up),
        mem_used: (9.6 + Math.random() * 0.4) * 1073741824 });
      hub.emit('stats', hub.state.stats);
    }, 1000);
    return hub;
  }

  window.KronDemo = {
    songs: SONGS,
    parseLrc,
    ui: {},
    configure(id, preset) { presets[id] = preset; },
    hub(id) { return hubs[id] || (hubs[id] = makeHub(id, presets[id] || {})); },
    onToast(fn) { toastFns.add(fn); },
  };
})();
