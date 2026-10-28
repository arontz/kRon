// Stands in for Tauri inside the website's demo frames: the app's own code calls window.__TAURI__,
// and this hands those calls to the demo hub (hub.js) that the page created, which plays the part of the app's Rust side.
(function () {
  const id = new URLSearchParams(location.search).get('hub') || 'main';
  const demo = (window.parent && window.parent.KronDemo) || window.KronDemo;
  if (!demo) { console.error('kRon demo hub not found'); return; }
  const hub = demo.hub(id);
  window.__TAURI__ = {
    core: { invoke: (cmd, args) => Promise.resolve(hub.invoke(cmd, args || {}, window)) },
    event: { listen: (ev, cb) => Promise.resolve(hub.listen(ev, cb)), emit: () => Promise.resolve() },
  };
})();
