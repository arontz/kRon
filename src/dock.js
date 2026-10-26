// Now-playing card shown above the Windows notification centre while it is open.
(function () {
  const { ICONS } = K;
  const $ = (id) => document.getElementById(id);

  $('art').innerHTML = ICONS.note;
  $('prev').innerHTML = ICONS.prev;
  $('next').innerHTML = ICONS.next;
  $('prev').onclick = () => K.cmd('prev');
  $('next').onclick = () => K.cmd('next');
  $('play').onclick = () => K.cmd('toggle');
  $('detach').innerHTML = ICONS.taskbar;
  $('detach').onclick = () => K.setDock(false);

  $('seekbar').onclick = (e) => {
    const m = K.state.media;
    if (!m.active || !m.duration_ms) return;
    const r = $('seekbar').getBoundingClientRect();
    const ms = Math.round(Math.min(1, Math.max(0, (e.clientX - r.left) / r.width)) * m.duration_ms);
    m.position_ms = ms;
    m.updated_at_ms = Date.now();
    K.cmd('seek', ms);
    tick();
  };

  // The window is exactly as tall as the card.
  let lastH = 0;
  new ResizeObserver(() => {
    const h = Math.ceil($('card').getBoundingClientRect().height);
    if (h && h !== lastH) {
      lastH = h;
      K.invoke('dock_size', { h });
    }
  }).observe($('card'));

  function tick() {
    const m = K.state.media;
    const pos = K.position();
    const frac = m.duration_ms ? pos / m.duration_ms : 0;
    $('fill').style.width = `${frac * 100}%`;
    $('seekbar').setAttribute('aria-valuenow', String(Math.round(frac * 100)));
    $('pos').textContent = K.fmtTime(pos);
    $('dur').textContent = K.fmtTime(m.duration_ms);
    const idx = K.lineIndex(pos);
    const text = idx >= 0 ? K.lineText(idx) : '';
    if ($('lyric').textContent !== text) $('lyric').textContent = text;
  }

  function render(s) {
    const m = s.media;
    $('title').textContent = m.active ? m.title : 'Nothing playing';
    $('artist').textContent = m.active ? [m.artist, m.source].filter(Boolean).join(' · ') : 'Play something in any app';
    K.setArt($('art'), m.active ? s.artwork : '');
    for (const id of ['prev', 'play', 'next']) $(id).disabled = !m.active;
    $('play').innerHTML = m.playing ? ICONS.pause : ICONS.play;
    $('play').setAttribute('aria-label', m.playing ? 'Pause' : 'Play');
    tick();
  }

  K.on((s, what) => {
    if (what === 'dock_vis') {
      document.body.classList.toggle('shown', s.dockShown);
      return;
    }
    if (what !== 'stats') render(s);
  });
  K.init();
  setInterval(tick, 250);
})();
