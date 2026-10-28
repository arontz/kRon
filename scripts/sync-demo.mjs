// Copies kRon's real interface files from src/ into docs/demo/ for the website's live demo,
// adding one script tag that points the app's `window.__TAURI__` at the demo hub (docs/demo/shim.js).
// Run after changing anything in src/:  node scripts/sync-demo.mjs
// To preview the site, serve docs/ (the demo frames need http, not file://):  python3 -m http.server -d docs 8000
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
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
console.log(`synced ${copy.length + pages.length} files into docs/demo/`);
