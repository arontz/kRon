(function () {
  const { ICONS } = K;
  const $ = (id) => document.getElementById(id);

  $('art').innerHTML = ICONS.note;
  $('prev').innerHTML = ICONS.prev;
  $('next').innerHTML = ICONS.next;
  $('lyr').innerHTML = ICONS.lyrics;
  $('grip').innerHTML = ICONS.move;
  document.querySelector('.sys-icon').innerHTML = ICONS.pulse;

  $('prev').onclick = () => K.cmd('prev');
  $('next').onclick = () => K.cmd('next');
  $('play').onclick = () => K.cmd('toggle');
  $('lyr').onclick = () => K.setLyricsOn(!K.state.settings.lyrics_on);

  // ---- one-line lyrics on the taskbar ----
  let lyricKey = null;
  let scrollTimer = null;

  function lyricMode(s) {
    const st = s.settings;
    return st.page === 'music' && st.lyrics_on && st.lyr_place === 'taskbar' && s.media.active && s.lyrics.status === 'synced' && s.lyrics.lines.length > 0;
  }

  function updateLyric(force) {
    const s = K.state;
    const on = lyricMode(s);
    if (document.body.classList.contains('lyric-mode') !== on) {
      document.body.classList.toggle('lyric-mode', on);
      reportSize();
    }
    if (!on) return;

    const pos = K.position();
    const idx = K.lineIndex(pos);
    const key = `${s.lyrics.track_key}|${idx}`;
    const text = $('lyric-text');
    if (force || key !== lyricKey) {
      const changed = lyricKey !== null && key !== lyricKey;
      lyricKey = key;
      // Before the first line, show the track instead of an empty row.
      const line = idx >= 0 ? K.lineText(idx) : `${s.media.title}`;
      text.textContent = line;
      text.title = line;
      $('lyric-sub').textContent = [s.media.title, s.media.artist].filter(Boolean).join(' · ');
      if (changed) {
        text.classList.remove('enter');
        void text.offsetWidth;
        text.classList.add('enter');
      }

      // Lines longer than the pill scroll sideways while they're sung.
      clearTimeout(scrollTimer);
      text.style.transition = 'none';
      text.style.transform = 'translateX(0)';
      const overflow = text.scrollWidth - $('lyric-box').clientWidth;
      document.body.classList.toggle('fits', overflow <= 0);
      if (overflow > 0 && idx >= 0) {
        const lines = s.lyrics.lines;
        const dur = lines[idx + 1] ? lines[idx + 1].t - lines[idx].t : 4000;
        scrollTimer = setTimeout(() => {
          text.style.transition = `transform ${Math.max(1200, dur * 0.75 - 700)}ms linear`;
          text.style.transform = `translateX(${-overflow - 8}px)`;
        }, 700);
      }
    }
  }

  // ---- move mode: drag the handle to put the pill anywhere; near a side edge it docks as a column ----
  $('pill-done').onclick = () => K.setPillEdit(false);
  $('pill-reset').onclick = () => K.resetPill();  let last = null;
  let acc = { dx: 0, dy: 0 };
  let flushQueued = false;
  function flush(commit) {
    flushQueued = false;
    if (acc.dx || acc.dy || commit) {
      K.movePillBy(acc.dx, acc.dy, commit);
      acc = { dx: 0, dy: 0 };
    }
  }
  $('grip').addEventListener('pointerdown', (e) => {
    $('grip').setPointerCapture(e.pointerId);
    last = { x: e.screenX, y: e.screenY };
  });
  $('grip').addEventListener('pointermove', (e) => {
    if (!last) return;
    acc.dx += e.screenX - last.x;
    acc.dy += e.screenY - last.y;
    last = { x: e.screenX, y: e.screenY };
    if (!flushQueued) {
      flushQueued = true;
      requestAnimationFrame(() => flush(false));
    }
  });
  const endDrag = () => {
    if (!last) return;
    last = null;
    flush(true); // save once at the end
  };
  $('grip').addEventListener('pointerup', endDrag);
  $('grip').addEventListener('pointercancel', endDrag);

  let lastSize = '';
  function reportSize() {
    const r = $('pill').getBoundingClientRect();
    const w = Math.ceil(r.width);
    const h = Math.ceil(r.height);
    const key = `${w}x${h}`;
    if (w && h && key !== lastSize) {
      lastSize = key;
      K.invoke('pill_size', { w, h });
    }
  }
  new ResizeObserver(reportSize).observe($('pill'));

  let wasVertical = false;
  function render(s, what) {
    const vertical = K.isVertical();
    document.body.classList.toggle('vertical', vertical);
    // The pill only has a music and a system face; the Appearance panel page shows the music one.
    document.body.dataset.page = s.settings.page === 'system' ? 'system' : 'music';
    document.body.classList.toggle('open', s.flyoutOpen);
    document.body.classList.toggle('moving', s.pillEdit);
    const m = s.media;
    $('title').textContent = m.active ? m.title : 'Nothing playing';
    $('artist').textContent = m.active ? m.artist || m.source : 'Play something in any app';
    // The column has no room for text, so the track is a tooltip instead.
    $('pill').title = vertical && m.active ? [m.title, m.artist].filter(Boolean).join(' - ') : '';
    for (const id of ['prev', 'play', 'next']) $(id).disabled = !m.active;
    $('play').innerHTML = m.playing ? ICONS.pause : ICONS.play;
    $('play').setAttribute('aria-label', m.playing ? 'Pause' : 'Play');
    K.setArt($('art'), m.active ? s.artwork : '');

    $('lyr').classList.toggle('on', s.settings.lyrics_on);
    $('lyr').setAttribute('aria-pressed', String(s.settings.lyrics_on));

    const st = s.stats;
    const memPct = st.mem_total ? Math.round((st.mem_used / st.mem_total) * 100) : 0;
    $('cpu').textContent = `${Math.round(st.cpu)}%`;
    $('cpu-bar').firstElementChild.style.width = `${Math.min(100, st.cpu)}%`;
    $('mem').textContent = `${memPct}%`;
    $('mem-bar').firstElementChild.style.width = `${memPct}%`;
    const rate = vertical ? K.fmtRateShort : K.fmtRate;
    $('down').textContent = rate(st.down);
    $('up').textContent = rate(st.up);

    // Re-render the line only when it could change, so a long line's scroll isn't restarted.
    updateLyric(what === 'lyrics' || what === 'settings' || what === 'all' || vertical !== wasVertical);
    wasVertical = vertical;
    reportSize();
  }

  K.on(render);
  K.init();
  setInterval(() => updateLyric(false), 100);
})();
