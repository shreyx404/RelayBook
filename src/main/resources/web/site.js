(() => {
  const toggle = document.getElementById('theme-toggle');
  const label = document.getElementById('theme-label');
  if (!toggle) return;

  function applyTheme(theme, remember) {
    const dark = theme === 'dark';
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    toggle.setAttribute('aria-pressed', String(dark));
    toggle.setAttribute('aria-label', dark ? 'Switch to light colors' : 'Switch to dark colors');
    if (label) label.textContent = dark ? 'Light mode' : 'Dark mode';
    const color = document.querySelector('meta[name="theme-color"]');
    if (color) color.setAttribute('content', dark ? '#232a24' : '#f4f1e9');
    if (remember) {
      try { localStorage.setItem('relaybook-theme', dark ? 'dark' : 'light'); }
      catch (error) { /* The toggle still works if browser storage is disabled. */ }
    }
  }

  let theme = 'light';
  try { theme = localStorage.getItem('relaybook-theme') === 'dark' ? 'dark' : 'light'; }
  catch (error) { /* Keep the paper theme by default. */ }
  applyTheme(theme, false);
  toggle.addEventListener('click', () => {
    applyTheme(document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark', true);
  });
})();
