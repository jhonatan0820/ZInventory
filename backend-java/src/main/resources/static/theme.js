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

  const iconSun  = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"/></svg>';
  const iconMoon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>';

  // Colores reales del design system (deben coincidir con --color-bg de styles.css).
  const BG = { dark: '#212121', light: '#ffffff' };

  // === Animación: WIPE DIAGONAL ===============================================
  //
  // Mecánica:
  //   1. Se inyecta un overlay `position: fixed` con un cuadrado grande (200vmax)
  //      centrado y rotado 45deg — un diamante que, al estar centrado, cubre
  //      todo el viewport (su semi-diagonal ≈141vmax > semi-diagonal del viewport).
  //   2. La keyframe `__theme-wipe` traslada el diamante a lo largo de su eje X
  //      LOCAL (ya rotado): de -250vmax → 0 → +250vmax. En coordenadas de
  //      pantalla eso significa entrar desde la esquina superior-izquierda,
  //      cubrir por completo al 50%, y salir por la inferior-derecha.
  //   3. Como solo animamos `transform` (rotate + translateX), el compositor
  //      del navegador la ejecuta en GPU → 60fps limpios.
  //   4. En el momento en que el overlay cubre por completo la pantalla (50%
  //      del keyframe, ≈350 ms), conmutamos `data-theme` — el usuario nunca ve
  //      el instante del switch.
  //   5. Al terminar, retiramos el overlay del DOM.
  //
  // Por qué diagonal y no otra: se siente premium, no cliché, y evita el
  // problema de alinear un origen con el botón (que fue el problema del
  // clip-path circle previo).
  // ============================================================================
  const STYLE_ID = '__inventorio-theme-anim';
  const DURATION = 700;
  if (!document.getElementById(STYLE_ID)) {
    const styleEl = document.createElement('style');
    styleEl.id = STYLE_ID;
    styleEl.textContent = `
      .__theme-wipe-overlay {
        position: fixed;
        top: 50%;
        left: 50%;
        width: 200vmax;
        height: 200vmax;
        z-index: 999999;
        pointer-events: none;
        will-change: transform;
        transform: translate(-50%, -50%) rotate(45deg) translateX(-250vmax);
        animation: __theme-wipe ${DURATION}ms cubic-bezier(0.77, 0, 0.175, 1) forwards;
        box-shadow: 0 0 60px 0 rgba(0, 0, 0, 0.18);
      }
      @keyframes __theme-wipe {
        0%   { transform: translate(-50%, -50%) rotate(45deg) translateX(-250vmax); }
        50%  { transform: translate(-50%, -50%) rotate(45deg) translateX(0);        }
        100% { transform: translate(-50%, -50%) rotate(45deg) translateX(250vmax);  }
      }
      /* Micro feedback en el ícono del botón: se encoge y rota mientras dura
         el wipe. Sincronizado con la duración total. */
      [data-theme-toggle].__theme-toggle-active > svg {
        animation: __theme-icon-pop ${DURATION}ms cubic-bezier(0.34, 1.56, 0.64, 1);
      }
      @keyframes __theme-icon-pop {
        0%   { transform: scale(1)   rotate(0deg);   }
        50%  { transform: scale(0.55) rotate(180deg); }
        100% { transform: scale(1)   rotate(360deg); }
      }
      @media (prefers-reduced-motion: reduce) {
        .__theme-wipe-overlay { animation: none !important; display: none !important; }
        [data-theme-toggle].__theme-toggle-active > svg { animation: none !important; }
      }
    `;
    document.head.appendChild(styleEl);
  }

  const paint = () => {
    const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
    buttons.forEach((btn) => {
      btn.innerHTML = isDark ? iconSun : iconMoon;
      btn.setAttribute('aria-label', isDark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro');
      btn.setAttribute('title', isDark ? 'Tema claro' : 'Tema oscuro');
      btn.setAttribute('aria-pressed', String(isDark));
    });
  };

  const applyTheme = (next) => {
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem(KEY, next);
    paint();
  };

  const prefersReduced = () =>
    window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  let animating = false;

  const toggle = (event) => {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';

    if (prefersReduced()) {
      applyTheme(next);
      return;
    }

    if (animating) return;
    animating = true;

    const btn = event.currentTarget;
    btn.classList.add('__theme-toggle-active');

    // Overlay pintado con el color de fondo del NUEVO tema.
    const overlay = document.createElement('div');
    overlay.className = '__theme-wipe-overlay';
    overlay.style.background = BG[next];
    document.body.appendChild(overlay);

    // Al 50% del wipe, el diamante cubre todo el viewport → cambiamos tema.
    const midpoint = Math.round(DURATION / 2);
    window.setTimeout(() => applyTheme(next), midpoint);

    // Limpieza al terminar.
    const cleanup = () => {
      if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
      btn.classList.remove('__theme-toggle-active');
      animating = false;
    };
    overlay.addEventListener('animationend', cleanup, { once: true });
    // Fallback por si el evento no dispara (tab en background, etc.).
    window.setTimeout(cleanup, DURATION + 200);
  };

  buttons.forEach((btn) => btn.addEventListener('click', toggle));
  paint();
});
