// Aplica el tema antes del render para evitar parpadeo (FOUC).
(function () {
  const KEY = 'inventorio.theme';
  const stored = localStorage.getItem(KEY);
  const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  const theme = stored || (prefersDark ? 'dark' : 'light');
  document.documentElement.setAttribute('data-theme', theme);
  window.__inventorioTheme = { get: () => document.documentElement.getAttribute('data-theme') };
})();

document.addEventListener('DOMContentLoaded', () => {
  const KEY = 'inventorio.theme';
  const buttons = document.querySelectorAll('[data-theme-toggle]');
  if (!buttons.length) return;

  const iconSun = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>';
  const iconMoon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';

  const paint = () => {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    buttons.forEach((button) => {
      button.innerHTML = isDark ? iconSun : iconMoon;
      button.setAttribute('aria-label', isDark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');
      button.setAttribute('title', isDark ? 'Tema claro' : 'Tema oscuro');
      button.setAttribute('aria-pressed', String(isDark));
    });
  };

  const applyTheme = (next) => {
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem(KEY, next);
    paint();
  };

  const prefersReducedMotion = () =>
    window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  let animating = false;
  const toggle = () => {
    if (animating) return;

    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    const startTransition = document.startViewTransition;

    if (prefersReducedMotion() || typeof startTransition !== 'function') {
      applyTheme(next);
      return;
    }

    animating = true;
    buttons.forEach((button) => button.classList.add('__theme-toggle-active'));

    const transition = startTransition.call(document, () => applyTheme(next));
    const cleanup = () => {
      buttons.forEach((button) => button.classList.remove('__theme-toggle-active'));
      animating = false;
    };
    transition.finished.then(cleanup, cleanup);
  };

  buttons.forEach((button) => button.addEventListener('click', toggle));
  paint();
});
