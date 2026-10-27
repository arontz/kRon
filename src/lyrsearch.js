// "Search for lyrics": search several sources, preview, and save the pick for the song that's playing.
(function () {
  const $ = (id) => document.getElementById(id);
  const TS = /\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?\]/;
  const hasTimestamps = (t) => TS.test(t || '');

  let hits = [];
  let selected = -1;
  let seq = 0; // ignores answers to an older search
  let sort = { key: '', dir: 1 };

  function status(msg, err) {
    $('status').textContent = msg;
    $('status').classList.toggle('err', !!err);
  }

  function syncedLabel(h) {
    return h.synced === true ? 'Yes' : h.synced === false ? 'No' : '?';
  }

  function render() {
    const body = $('rows');
    body.innerHTML = '';
    $('empty').style.display = hits.length ? 'none' : '';
    hits.forEach((h, i) => {
      const tr = document.createElement('tr');
      tr.setAttribute('role', 'row');
      tr.setAttribute('aria-selected', String(i === selected));
      tr.dataset.i = String(i);
      for (const v of [h.title, h.album, h.artist, h.source]) {
        const td = document.createElement('td');
        td.textContent = v;
        td.title = v;
        tr.appendChild(td);
      }
      const ts = document.createElement('td');
      ts.textContent = syncedLabel(h);
      if (h.synced) ts.className = 'yes';
      tr.appendChild(ts);
      tr.onclick = () => select(i);
      tr.ondblclick = () => apply(true);
      body.appendChild(tr);
    });
    document.querySelectorAll('thead th').forEach((th) => {
      th.setAttribute('aria-sort', th.dataset.key === sort.key ? (sort.dir > 0 ? 'ascending' : 'descending') : 'none');
    });
  }

  function setPreview(text, hint) {
    const p = $('preview');
    p.textContent = text;
    p.classList.toggle('hint', !!hint);
    p.scrollTop = 0;
  }

  async function select(i) {
    selected = i;
    render();
    const h = hits[i];
    if (!h) return;
    $('rows').children[i]?.scrollIntoView({ block: 'nearest' });
    if (h.content == null) {
      setPreview('Loading…', true);
      try {
        h.content = await K.invoke('lyrics_fetch', { source: h.source, id: h.id });
        h.synced = hasTimestamps(h.content);
      } catch (e) {
        h.content = '';
        h.synced = false;
        if (selected === i) setPreview(String(e), true);
        render();
        return;
      }
      if (selected !== i) return; // the user moved on while it loaded
      render();
    }
    setPreview(h.content, false);
  }

  async function search() {
    const title = $('f-title').value.trim();
    const album = $('f-album').value.trim();
    const artist = $('f-artist').value.trim();
    if (!title && !album && !artist) { status('Type a title, album or artist first.', true); return; }
    const mine = ++seq;
    status('Searching…');
    $('search').disabled = true;
    hits = [];
    selected = -1;
    render();
    setPreview('Pick a result to preview it here.', true);
    try {
      const res = await K.invoke('lyrics_search', { title, album, artist });
      if (mine !== seq) return;
      hits = res;
      sort = { key: '', dir: 1 };
      render();
      const synced = hits.filter((h) => h.synced).length;
      status(hits.length ? `${hits.length} result${hits.length === 1 ? '' : 's'}${synced ? `, ${synced} timestamped` : ''}.` : 'Nothing found. Try fewer words, or search the web.');
      if (hits.length) select(0);
    } catch (e) {
      if (mine !== seq) return;
      status(`Search failed: ${e}`, true);
    } finally {
      if (mine === seq) $('search').disabled = false;
    }
  }

  async function apply(closeAfter) {
    const h = hits[selected];
    if (!h || !h.content) { status('Pick a result with lyrics first.', true); return; }
    try {
      const name = await K.invoke('lyrics_apply', { content: h.content });
      status(`Saved as “${name}”. kRon is using it now.`);
      if (closeAfter) await K.invoke('close_lyrics_search');
    } catch (e) {
      status(String(e), true);
    }
  }

  function sortBy(key) {
    sort = { key, dir: sort.key === key ? -sort.dir : 1 };
    const cur = hits[selected];
    const val = (h) => (key === 'synced' ? (h.synced === true ? 1 : h.synced === false ? 0 : 0.5) : String(h[key] || '').toLowerCase());
    hits.sort((a, b) => (val(a) < val(b) ? -1 : val(a) > val(b) ? 1 : 0) * sort.dir);
    selected = cur ? hits.indexOf(cur) : -1;
    render();
  }

  function renderTarget(s) {
    const m = s.media;
    $('target').innerHTML = m.active
      ? `Saves for the song playing now: <b>${K.esc([m.artist, m.title].filter(Boolean).join(' - '))}</b>`
      : 'Nothing is playing. Play the song you want lyrics for before you apply one.';
  }

  $('form').addEventListener('submit', (e) => { e.preventDefault(); search(); });
  $('ok').onclick = () => apply(true);
  $('apply').onclick = () => apply(false);
  $('cancel').onclick = () => K.invoke('close_lyrics_search');
  $('web').onclick = () => K.invoke('lyrics_action', { action: 'search_web' });
  document.querySelectorAll('thead th').forEach((th) => { th.onclick = () => sortBy(th.dataset.key); });

  $('rows-wrap').addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); if (selected < hits.length - 1) select(selected + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); if (selected > 0) select(selected - 1); }
    else if (e.key === 'Enter') { e.preventDefault(); apply(true); }
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') K.invoke('close_lyrics_search'); });

  let filled = false;
  K.on((s, what) => {
    if (what === 'stats') return;
    renderTarget(s);
    // Start from the song that's playing.
    if (!filled && s.media.active) {
      filled = true;
      $('f-title').value = s.media.title;
      $('f-album').value = s.media.album;
      $('f-artist').value = s.media.artist;
      search();
    }
  });
  K.init().then(() => {
    if (!filled) $('f-title').focus();
  });
})();
