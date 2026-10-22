// Applies the stored theme before first paint. External file (not inline) so the production CSP
// `script-src 'self'` allows it. Dark is the default; the key is shared with AppShell's toggle.
(() => {
  let theme = 'dark';
  try {
    const stored = window.localStorage.getItem('fm-theme');
    if (stored === 'light' || stored === 'dark') {
      theme = stored;
    }
  } catch {
    // Storage blocked (private mode): keep the default.
  }
  document.documentElement.dataset.theme = theme;
})();
