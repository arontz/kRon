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
  $$('.tbl > div, .qs li, .trust h2, .trust .txt > *, .foot > *, .showcase h2, .showcase .cap').forEach((el) => {
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
  // Scroll only the row sideways; scrollIntoView would also drag the page up or down.
  function centre(el, smooth) {
    const left = el.offsetLeft - row.offsetLeft - (row.clientWidth - el.offsetWidth) / 2;
    row.scrollTo({ left, behavior: smooth && !reduced ? 'smooth' : 'auto' });
  }
  items.forEach((el) => el.addEventListener('click', () => { if (el !== lit) centre(el, true); }));
  row.addEventListener('keydown', (e) => {
    const i = items.indexOf(lit);
    const next = e.key === 'ArrowRight' ? items[i + 1] : e.key === 'ArrowLeft' ? items[i - 1] : null;
    if (next) { e.preventDefault(); centre(next, true); }
  });
  // Start with the first part centred.
  requestAnimationFrame(() => centre(items[0], false));

  // How it goes: one slider drives the clause and the fragment.
  const CLAUSES = ['Play anything, anywhere.', 'Hover to open the panel.', 'Read along on your desktop.', 'Make it match yours.'];
  const NAMES = ['Play', 'Hover', 'Read along', 'Make it yours'];
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
