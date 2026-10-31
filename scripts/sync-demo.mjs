// Copies kRon's real interface files from src/ into docs/demo/ for the website's live demo,
// adding one script tag that points the app's `window.__TAURI__` at the demo hub (docs/demo/shim.js).
// Run after changing anything in src/:  node scripts/sync-demo.mjs
// To preview the site, serve docs/ (the demo frames need http, not file://):  python3 -m http.server -d docs 8000
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(root, 'docs', 'demo');
mkdirSync(out, { recursive: true });

const copy = ['common.css', 'common.js', 'pill.js', 'flyout.js', 'lyrsearch.js'];
const pages = ['pill.html', 'flyout.html', 'overlay.html', 'lyrsearch.html'];

for (const f of copy) writeFileSync(join(out, f), readFileSync(join(root, 'src', f)));
for (const f of pages) {
  let html = readFileSync(join(root, 'src', f), 'utf8');
  if (!html.includes('<script src="common.js">')) throw new Error(`${f}: no common.js script tag to hook`);
  html = html.replace('<script src="common.js">', '<script src="shim.js"></script>\n<script src="common.js">');
  writeFileSync(join(out, f), html);
}
// Version stamp: every script, stylesheet and demo frame URL gets ?v=<hash of the site's files>, so a browser never mixes a
// new page with an old cached stylesheet or script (GitHub Pages caches them for about ten minutes).
const docs = join(root, 'docs');
const stamped = ['site.css', 'site.js', 'index.html', ...[...copy, ...pages, 'hub.js', 'shim.js'].map((f) => `demo/${f}`)];
const hash = createHash('sha256');
for (const f of stamped) hash.update(readFileSync(join(docs, f), 'utf8').replace(/(?:\?|&amp;|&)v=[0-9a-f]{10}/g, ''));
const v = hash.digest('hex').slice(0, 10);
const withV = (url) => {
  const [path, query = ''] = url.split('?');
  const params = query.split('&').filter((x) => x && !x.startsWith('v=') && x !== 'amp;');
  return `${path}?${[...params, `v=${v}`].join('&amp;')}`.replace(/&amp;amp;/g, '&amp;');
};
const stamp = (file, re) => {
  const path = join(docs, file);
  const text = readFileSync(path, 'utf8').replace(re, (m, attr, url) => `${attr}="${withV(url.replace(/&amp;/g, '&'))}"`);
  writeFileSync(path, text);
};
stamp('index.html', /(src|href|data-src|data-url)="((?:demo\/)?[\w.-]+\.(?:js|css|html)(?:\?[^"]*)?)"/g);
for (const f of pages) stamp(`demo/${f}`, /(src|href)="([\w.-]+\.(?:js|css)(?:\?[^"]*)?)"/g);
console.log(`synced ${copy.length + pages.length} files into docs/demo/, version ${v}`);
