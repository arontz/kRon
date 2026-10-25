(function () {
  const { ICONS } = K;
  const $ = (id) => document.getElementById(id);

  $('art').innerHTML = ICONS.note;
  $('prev').innerHTML = ICONS.prev;
  $('next').innerHTML = ICONS.next;
  $('lyr').innerHTML = ICONS.lyrics;
  document.querySelector('.sys-icon').innerHTML = ICONS.pulse;

  $('prev').onclick = () => K.cmd('prev');
  $('next').onclick = () => K.cmd('next');
  $('play').onclick = () => K.cmd('toggle');
  $('lyr').onclick = () => K.setLyricsOn(!K.state.settings.lyrics_on);

  // ---- one-line lyrics on the taskbar ----
  let lyricKey = null;
  let lyricSung = -1;
  let lyricWords = [];
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
      lyricSung = -1;
      // Before the first line, show the track instead of an empty row.
      const line = idx >= 0 ? K.lineText(idx) : `${s.media.title}`;
      lyricWords = line.split(/(\s+)/);
      text.innerHTML = lyricWords.map((w) => `<span>${K.esc(w)}</span>`).join('');
      text.title = line;
      $('lyric-sub').textContent = [s.media.title, s.media.artist].filter(Boolean).join(' · ');
      if (changed) {
        text.classList.remove('enter');
        void text.offsetWidth;
        text.classList.add('enter');
      }

      // Lines longer than the pill scroll sideways over the time the line is sung.
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

    if (idx >= 0) {
      const sung = Math.ceil(K.lineProgress(idx, pos) * lyricWords.length);
      if (sung !== lyricSung) {
        lyricSung = sung;
        text.querySelectorAll('span').forEach((el, i) => el.classList.toggle('sung', i < sung));
      }
    }
  }

  let lastW = 0;
  function reportSize() {
    const w = Math.ceil($('pill').getBoundingClientRect().width);
    if (w && w !== lastW) {
      lastW = w;
      K.invoke('pill_size', { w });
    }
  }

  function render(s, what) {
    document.body.dataset.page = s.settings.page;
    document.body.classList.toggle('open', s.flyoutOpen);

    const m = s.media;
    $('title').textContent = m.active ? m.title : 'Nothing playing';
    $('artist').textContent = m.active ? m.artist || m.source : 'Play something in any app';
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
    $('down').textContent = K.fmtRate(st.down);
    $('up').textContent = K.fmtRate(st.up);

    // Re-render the line only when it could change, so a long line's scroll isn't restarted.
    updateLyric(what === 'lyrics' || what === 'settings' || what === 'all');
    reportSize();
  }

  K.on(render);
  K.init();
  setInterval(() => updateLyric(false), 100);
})();
