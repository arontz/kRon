(function () {
  const { ICONS } = K;
  const $ = (id) => document.getElementById(id);
  const PAGES = ['music', 'system', 'appearance'];
  const NAMES = { music: 'Now playing', system: 'System', appearance: 'Appearance' };

  $('art').innerHTML = ICONS.note;
  $('go-prev').innerHTML = ICONS.left;
  $('go-next').innerHTML = ICONS.right;
  $('shuffle').innerHTML = ICONS.shuffle;
  $('prev').innerHTML = ICONS.prev;
  $('next').innerHTML = ICONS.next;
  $('repeat').innerHTML = ICONS.repeat;

  $('prev').onclick = () => K.cmd('prev');
  $('next').onclick = () => K.cmd('next');
  $('play').onclick = () => K.cmd('toggle');
  $('shuffle').onclick = () => K.cmd('shuffle');
  $('repeat').onclick = () => K.cmd('repeat');
  document.querySelectorAll('.place-seg button').forEach((btn) => {
    btn.onclick = () => {
      const place = btn.dataset.place;
      if (place === 'off') {
        K.setLyricsOn(false);
      } else {
        K.setLyr({ place });
        if (!K.state.settings.lyrics_on) K.setLyricsOn(true);
      }
    };
  });
  $('customize').onclick = () => K.setOverlayEdit(true);
  $('attach-pill').innerHTML = ICONS.calendar;
  $('attach-pill').onclick = () => K.setDock(true);
  $('move-pill').innerHTML = ICONS.move;
  $('move-pill').onclick = () => K.setPillEdit(true);

  $('lyr-search').innerHTML = ICONS.search;
  $('lyr-folder').innerHTML = ICONS.folder;
  $('lyr-reload').innerHTML = ICONS.reload;
  for (const a of ['search', 'add', 'folder', 'reload']) {
    $(`lyr-${a}`).onclick = () => K.invoke('lyrics_action', { action: a });
  }

  function go(dir) {
    const i = PAGES.indexOf(K.state.settings.page);
    const page = PAGES[(i + dir + PAGES.length) % PAGES.length];
    const el = $(`page-${page}`);
    el.classList.remove('slide-L', 'slide-R');
    void el.offsetWidth; // restart the animation
    el.classList.add(dir < 0 ? 'slide-L' : 'slide-R');
    K.state.settings.page = page; // optimistic; Rust echoes it back
    render(K.state);
    K.setPage(page);
  }
  $('go-prev').onclick = () => go(-1);
  $('go-next').onclick = () => go(1);

  $('seekbar').onclick = (e) => {
    const m = K.state.media;
    if (!m.active || !m.duration_ms) return;
    const r = $('seekbar').getBoundingClientRect();
    const frac = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    const ms = Math.round(frac * m.duration_ms);
    m.position_ms = ms;
    m.updated_at_ms = Date.now();
    K.cmd('seek', ms);
    tick();
  };

  document.querySelectorAll('.lyr-line').forEach((btn) => {
    btn.onclick = () => {
      const t = Number(btn.dataset.t);
      if (t >= 0) K.cmd('seek', t);
    };
  });

  // ---- Appearance page ----
  const ACCENTS = [
    ['#7fe0c6', 'Mint'], ['#6cb6ff', 'Blue'], ['#b79cff', 'Violet'], ['#ff8fb1', 'Pink'], ['#ffb35c', 'Orange'], ['#ffe066', 'Yellow'],
  ];
  const LYR_TEXT = [
    ['', 'Automatic (white)'], ['#ffffff', 'White'], ['#14161b', 'Black'], ['#ffe066', 'Yellow'], ['#7fe0c6', 'Mint'], ['#ff8fb1', 'Pink'],
  ];
  const LYR_HI = [
    ['', 'Automatic (accent colour)'], ['#7fe0c6', 'Mint'], ['#6cb6ff', 'Blue'], ['#ffe066', 'Yellow'], ['#ff8fb1', 'Pink'], ['#ffb35c', 'Orange'],
  ];
  function buildSwatches(id, list, onPick) {
    const box = $(id);
    list.forEach(([color, name]) => {
      const b = document.createElement('button');
      b.className = 'sw' + (color ? '' : ' auto');
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-label', name);
      b.title = name;
      b.dataset.color = color;
      if (color) b.style.background = color;
      b.onclick = () => onPick(color);
      box.appendChild(b);
    });
  }
  buildSwatches('accent-sw', ACCENTS, (c) => K.setTheme({ accent: c }));
  buildSwatches('ltext-sw', LYR_TEXT, (c) => K.setTheme({ lyrText: c }));
  buildSwatches('lhi-sw', LYR_HI, (c) => K.setTheme({ lyrHi: c }));
  document.querySelectorAll('.theme-seg button').forEach((b) => { b.onclick = () => K.setTheme({ mode: b.dataset.theme }); });

  const hue = $('accent-hue');
  const hueColor = () => K.hslToHex(Number(hue.value), 0.75, 0.68);
  hue.addEventListener('input', () => K.setTheme({ accent: hueColor(), save: false }));
  hue.addEventListener('change', () => K.setTheme({ accent: hueColor() }));

  function renderAppearance(s) {
    const st = s.settings;
    document.querySelectorAll('.theme-seg button').forEach((b) => b.setAttribute('aria-checked', String(b.dataset.theme === st.theme_mode)));
    $('theme-note').textContent = st.theme_mode === 'auto'
      ? `Following your taskbar: ${s.taskbarLight ? 'light' : 'dark'}. Text switches to match when you change Windows' theme.`
      : 'Fixed, whatever the taskbar looks like.';
    const mark = (id, current) => document.querySelectorAll(`#${id} .sw`).forEach((b) => b.setAttribute('aria-checked', String(b.dataset.color.toLowerCase() === (current || '').toLowerCase())));
    mark('accent-sw', st.accent);
    mark('ltext-sw', st.lyr_text);
    mark('lhi-sw', st.lyr_hi);
    if (document.activeElement !== hue && !hue.matches(':active')) hue.value = String(Math.round(K.hexToHsl(st.accent)[0]));
  }

  // Keep the window exactly as tall as the panel.
  let lastH = 0;
  new ResizeObserver(() => {
    const h = Math.ceil($('panel').getBoundingClientRect().height);
    if (h && h !== lastH) {
      lastH = h;
      K.invoke('flyout_size', { h });
    }
  }).observe($('panel'));

  const LYR_MSG = {
    idle: '',
    loading: 'Finding lyrics…',
    none: 'No lyrics found. Search the web, then add them with +',
    plain: 'Only unsynced lyrics found (no timestamps)',
    error: "Couldn't reach the lyrics service",
  };

  let lastIdx = null;
  function renderLyrics(force) {
    const s = K.state;
    const idx = K.lineIndex(K.position());
    if (!force && idx === lastIdx) return;
    lastIdx = idx;
    const synced = s.lyrics.status === 'synced' && s.lyrics.lines.length > 0;
    $('lyr-lines').style.display = synced ? 'flex' : 'none';
    $('lyr-msg').textContent = synced ? '' : s.media.active ? LYR_MSG[s.lyrics.status] || '' : 'Nothing playing';
    const local = s.lyrics.source === 'local';
    const SOURCE_NAMES = { local: 'Your file', qq: 'QQ Music', lrclib: 'LRCLIB' };
    $('lyr-label').textContent = s.lyrics.source ? `Lyrics · ${SOURCE_NAMES[s.lyrics.source] || 'LRCLIB'}` : 'Lyrics';
    $('lyr-add').innerHTML = local ? ICONS.edit : ICONS.plus;
    $('lyr-add').title = local ? 'Edit your lyrics file' : 'Add your own lyrics file';
    for (const a of ['search', 'add', 'reload']) $(`lyr-${a}`).disabled = !s.media.active;
    if (!synced) return;
    document.querySelectorAll('.lyr-line').forEach((btn) => {
      const i = idx + Number(btn.dataset.off);
      const line = s.lyrics.lines[i];
      btn.textContent = line ? K.lineText(i) : '';
      btn.title = btn.textContent;
      btn.dataset.t = line ? line.t : -1;
      btn.disabled = !line;
    });
  }

  function tick() {
    const m = K.state.media;
    const pos = K.position();
    const frac = m.duration_ms ? pos / m.duration_ms : 0;
    $('fill').style.width = `${frac * 100}%`;
    $('knob').style.left = `${frac * 100}%`;
    $('seekbar').setAttribute('aria-valuenow', String(Math.round(frac * 100)));
    $('pos').textContent = K.fmtTime(pos);
    $('dur').textContent = K.fmtTime(m.duration_ms);
    renderLyrics(false);
  }

  function render(s, what) {
    const page = s.settings.page;
    document.body.dataset.page = page;
    $('page-name').textContent = NAMES[page];
    $('dot-music').classList.toggle('on', page === 'music');
    $('dot-system').classList.toggle('on', page === 'system');
    $('dot-appearance').classList.toggle('on', page === 'appearance');

    const m = s.media;
    $('title').textContent = m.active ? m.title : 'Nothing playing';
    $('sub').textContent = m.active ? [m.artist, m.album].filter(Boolean).join(' · ') : 'Play something in Spotify, a browser or any player';
    $('source').textContent = m.active && m.source ? `Playing in ${m.source}` : '';
    K.setArt($('art'), m.active ? s.artwork : '');
    for (const id of ['prev', 'play', 'next', 'shuffle', 'repeat']) $(id).disabled = !m.active;
    $('play').innerHTML = m.playing ? ICONS.pause : ICONS.play;
    $('play').setAttribute('aria-label', m.playing ? 'Pause' : 'Play');
    $('shuffle').classList.toggle('on', m.shuffle);
    $('shuffle').setAttribute('aria-pressed', String(m.shuffle));
    $('repeat').classList.toggle('on', m.repeat !== 'none');
    $('repeat').dataset.mode = m.repeat;
    $('repeat').setAttribute('aria-label', `Repeat: ${m.repeat === 'none' ? 'off' : m.repeat === 'track' ? 'this track' : 'all'}`);

    const place = s.settings.lyrics_on ? s.settings.lyr_place : 'off';
    document.querySelectorAll('.place-seg button').forEach((btn) => btn.setAttribute('aria-checked', String(btn.dataset.place === place)));
    $('customize').style.display = place === 'desktop' ? '' : 'none';
    // The pill is a narrow column at a side edge, which has no room for a lyric line.
    const docked = !!s.settings.pill_side;
    const taskbarBtn = document.querySelector('.place-seg button[data-place="taskbar"]');
    taskbarBtn.disabled = docked;
    taskbarBtn.title = docked ? 'Not available while the pill is docked to a side edge' : '';

    const st = s.stats;
    $('cpu').textContent = Math.round(st.cpu);
    $('cores').textContent = st.cores ? `${st.cores} cores` : '';
    const hist = st.cpu_history.length ? st.cpu_history : [0];
    const step = 140 / Math.max(1, hist.length - 1);
    $('spark-line').setAttribute('points', hist.map((v, i) => `${(i * step).toFixed(1)},${(26 - (Math.min(100, v) / 100) * 24).toFixed(1)}`).join(' '));
    const memPct = st.mem_total ? Math.round((st.mem_used / st.mem_total) * 100) : 0;
    $('mem').textContent = memPct;
    $('mem-gb').textContent = st.mem_total ? `${K.fmtGB(st.mem_used)} / ${K.fmtGB(st.mem_total)} GB` : '';
    $('mem-bar').firstElementChild.style.width = `${memPct}%`;
    $('down').textContent = K.fmtRate(st.down);
    $('up').textContent = K.fmtRate(st.up);

    renderAppearance(s);
    if (what !== 'stats') {
      renderLyrics(true);
      tick();
    }
  }

  K.on(render);
  K.init();
  setInterval(() => {
    if (K.state.flyoutOpen) tick();
  }, 250);
})();
