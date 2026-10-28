(function () {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

  // A short note when a demo button needs the installed app (files, windows).
  const toast = $('#toast');
  let toastTimer;
  KronDemo.onToast((msg) => {
    toast.textContent = msg;
    toast.style.opacity = '1';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.style.opacity = '0'; }, 3200);
  });

  // Demo frames below the fold load when they get near the screen.
  const lazy = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      const f = e.target;
      if (f.dataset.src) { f.src = f.dataset.src; delete f.dataset.src; }
      lazy.unobserve(f);
    });
  }, { rootMargin: '600px 1200px' });
  $$('iframe[data-src]').forEach((f) => lazy.observe(f));

  // Entrances: heading, then text, then picture, once. Rows and list items stagger within their group.
  $$('.tbl .r > div, .qs li, .trust h2, .trust .txt > *, .foot > *, .showcase h2, .showcase .cap').forEach((el) => {
    el.setAttribute('data-rise', '');
    const sibs = [...el.parentElement.children];
    el.style.setProperty('--i', Math.min(sibs.indexOf(el), 6));
  });
  const rise = new IntersectionObserver((entries) => {
    entries.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('in'); rise.unobserve(e.target); } });
  }, { threshold: 0, rootMargin: '0px 0px -4% 0px' });
  $$('[data-rise]').forEach((el) => rise.observe(el));

  // The one loud move: the stage opens from its notched corner.
  requestAnimationFrame(() => requestAnimationFrame(() => $('#stage').classList.add('open')));

  // Showcase row: the centred part is lit; the caption follows it.
  const row = $('#row');
  const items = $$('.item', row);
  const capName = $('#cap-name'), capText = $('#cap-text');
  let lit = items[0];
  function light(el) {
    if (el === lit) return;
    lit.classList.remove('lit');
    el.classList.add('lit');
    lit = el;
    capName.textContent = el.dataset.name;
    capText.textContent = el.dataset.text;
  }
  let raf;
  function nearest() {
    raf = null;
    const mid = row.getBoundingClientRect().left + row.clientWidth / 2;
    let best = null, bestD = Infinity;
    items.forEach((el) => {
      const r = el.getBoundingClientRect();
      const d = Math.abs(r.left + r.width / 2 - mid);
      if (d < bestD) { bestD = d; best = el; }
    });
    if (best) light(best);
  }
  row.addEventListener('scroll', () => { if (!raf) raf = requestAnimationFrame(nearest); }, { passive: true });
  // The row is pinned while the page scrolls: each pixel of vertical scroll moves the row one pixel sideways,
  // so the page stays on this section until the last part is reached, then carries on down.
  const pin = $('#showcase'), inner = $('#pin-inner');
  let travel = 0;
  function measure() {
    travel = Math.max(0, row.scrollWidth - row.clientWidth);
    pin.style.height = `${inner.offsetHeight + travel}px`;
    // If the pinned content is taller than the window, pin its bottom edge instead of cutting it off.
    inner.style.top = `${Math.min(0, innerHeight - inner.offsetHeight)}px`;
    follow();
  }
  function follow() {
    const top = pin.getBoundingClientRect().top - Number.parseFloat(inner.style.top || '0');
    row.scrollLeft = Math.max(0, Math.min(travel, -top));
  }
  addEventListener('scroll', follow, { passive: true });
  addEventListener('resize', measure);
  addEventListener('load', measure);
  measure();
  // Moving to a part means scrolling the page to where that part is centred.
  function centre(el, smooth) {
    const left = el.offsetLeft - row.offsetLeft - (row.clientWidth - el.offsetWidth) / 2;
    const y = pin.getBoundingClientRect().top + scrollY - Number.parseFloat(inner.style.top || '0') + Math.max(0, Math.min(travel, left));
    scrollTo({ top: y, behavior: smooth && !reduced ? 'smooth' : 'auto' });
  }
  items.forEach((el) => el.addEventListener('click', () => { if (el !== lit) centre(el, true); }));
  row.addEventListener('keydown', (e) => {
    const i = items.indexOf(lit);
    const next = e.key === 'ArrowRight' ? items[i + 1] : e.key === 'ArrowLeft' ? items[i - 1] : null;
    if (next) { e.preventDefault(); centre(next, true); }
  });
  requestAnimationFrame(() => measure());

  // How it goes: one slider drives the clause and the fragment.
  const CLAUSES = ['Play anything, anywhere.', 'Hover to open the panel.', 'Sing along on your desktop.', 'Make it match yours.'];
  const NAMES = ['Play', 'Hover', 'Sing along', 'Make it yours'];
  const SLOT = [0, 1, 2, 1];
  const steps = $('#steps'), clause = $('#clause'), fill = $('#fill');
  const names = $$('#names span'), slots = $$('.how .slot');
  function step() {
    const i = Number(steps.value);
    clause.textContent = CLAUSES[i];
    names.forEach((n, k) => n.classList.toggle('on', k === i));
    slots.forEach((s, k) => s.classList.toggle('on', k === SLOT[i]));
    steps.setAttribute('aria-valuetext', NAMES[i]);
    fill.style.width = `calc(${(i / 3) * 100}% - ${(i / 3) * 32}px + 16px)`;
    KronDemo.hub('steps').invoke('set_page', { page: i === 3 ? 'appearance' : 'music' });
  }
  steps.addEventListener('input', step);
  step();

  // Lyrics missing: the panel's magnifier and + open a search window (kRon's real one) or a lyrics file to write.
  const mwin = $('#mwin'), mhint = $('#mhint'), simSearch = $('#sim-search'), simEdit = $('#sim-edit');
  const sframe = $('#searchframe'), editor = $('#editor'), editStatus = $('#edit-status'), simBody = $('#simbody');
  function showPanel(which) {
    mhint.hidden = !!which;
    simSearch.hidden = which !== 'search';
    simEdit.hidden = which !== 'edit';
  }
  function fitSearch() { simBody.style.setProperty('--s', String(simBody.clientWidth / 1000)); }
  new ResizeObserver(fitSearch).observe(simBody);
  KronDemo.ui.openSearch = () => { showPanel('search'); fitSearch(); sframe.src = 'demo/lyrsearch.html?hub=miss'; mwin.scrollIntoView({ block: 'nearest', behavior: reduced ? 'auto' : 'smooth' }); };
  KronDemo.ui.openEditor = (id, hub) => {
    const s = hub.song();
    const sample = s.key === 'lantern-season'
      ? '[00:08.00] Lanterns on the water, slow\n[00:16.00] Every small light has a name\n[00:24.00] Carry them down to the dark\n[00:32.00] And let the river explain\n'
      : '[00:10.00] First line of the song\n[00:18.00] Second line of the song\n';
    $('#edit-title').textContent = `${s.artist} - ${s.title}.lrc`;
    editor.value = `[ar:${s.artist}]\n[ti:${s.title}]\n[al:${s.album}]\n# Paste lyrics below and save. Synced lines look like: [01:23.45] words\n\n${sample}`;
    editStatus.textContent = 'In the app this opens in Notepad. Edit the lines, then save, and kRon picks the file up.';
    showPanel('edit');
    mwin.scrollIntoView({ block: 'nearest', behavior: reduced ? 'auto' : 'smooth' });
  };
  KronDemo.ui.closePanel = () => { showPanel(''); sframe.removeAttribute('src'); };
  $$('[data-close]').forEach((b) => b.addEventListener('click', () => KronDemo.ui.closePanel()));
  $('#save').addEventListener('click', () => {
    const name = KronDemo.hub('miss').applyLyrics(editor.value);
    editStatus.textContent = name ? `Saved as \u201c${name}\u201d. kRon is using it now.` : 'No lyrics found in that file. Add lines like [00:10.00] words.';
  });

  // Questions: the answer shows in the pane (or opens in place on a phone).
  const qs = $$('#qlist button');
  const pane = $('#pane');
  function pick(btn) {
    qs.forEach((b) => b.setAttribute('aria-expanded', String(b === btn)));
    const p = document.createElement('p');
    p.textContent = btn.parentElement.querySelector('.a').textContent;
    pane.replaceChildren(p);
  }
  qs.forEach((b) => {
    b.addEventListener('click', () => pick(b));
    b.addEventListener('mouseenter', () => { if (matchMedia('(min-width: 901px)').matches) pick(b); });
    b.addEventListener('focus', () => { if (matchMedia('(min-width: 901px)').matches) pick(b); });
  });
  pick(qs[0]);
})();
