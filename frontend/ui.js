/* =====================================================
   ui.js · Componentes UI reutilizables
   - showToast(msg, type, timeout)
   - showConfirm(msg, opts) -> Promise<boolean>
   - showPrompt(msg, opts) -> Promise<string|null>
   Compatible con alerts.js (mantiene API showToast).
===================================================== */
(function () {
  'use strict';

  const ICONS = {
    success:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>',
    error:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
    info:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>',
    warning:
      '<svg class="toast-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>',
  };

  function getToastContainer() {
    let c = document.getElementById('toastContainer');
    if (!c) {
      c = document.createElement('div');
      c.id = 'toastContainer';
      c.className = 'toast-container';
      c.setAttribute('aria-live', 'polite');
      c.setAttribute('aria-atomic', 'true');
      document.body.appendChild(c);
    }
    return c;
  }

  function showToast(message, type = 'info', timeout = 3000, placement = 'default') {
    if (!message) return;
    const container = getToastContainer();
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    if (placement === 'center') toast.classList.add('toast-centered');
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
    toast.innerHTML =
      (ICONS[type] || ICONS.info) +
      `<div class="toast-message"></div>`;
    toast.querySelector('.toast-message').textContent = message;
    container.appendChild(toast);

    requestAnimationFrame(() => toast.classList.add('is-visible'));

    let dismissed = false;
    const remove = () => {
      if (dismissed) return;
      dismissed = true;
      clearTimeout(toast._timer);
      document.removeEventListener('click', dismissOnOutsideClick);
      toast.classList.remove('is-visible');
      setTimeout(() => toast.remove(), 220);
    };
    const dismissOnOutsideClick = (event) => {
      if (!toast.contains(event.target)) remove();
    };
    toast._timer = setTimeout(remove, timeout);
    toast.addEventListener('click', () => {
      remove();
    });
    if (placement === 'center') {
      document.addEventListener('click', dismissOnOutsideClick);
    }
  }

  /* ============ CONFIRM ============ */
  function showConfirm(message, opts = {}) {
    const {
      title = 'Confirmación',
      okText = 'Aceptar',
      cancelText = 'Cancelar',
      danger = false,
    } = opts;

    return new Promise((resolve) => {
      const overlay = document.createElement('div');
      overlay.className = 'confirm-overlay';
      overlay.setAttribute('role', 'dialog');
      overlay.setAttribute('aria-modal', 'true');
      overlay.innerHTML = `
        <div class="confirm-box" role="document">
          <h3></h3>
          <p></p>
          <div class="confirm-actions">
            <button type="button" class="remove" data-role="cancel"></button>
            <button type="button" class="${danger ? 'danger' : 'add'}" data-role="ok"></button>
          </div>
        </div>
      `;
      overlay.querySelector('h3').textContent = title;
      overlay.querySelector('p').textContent = message;
      overlay.querySelector('[data-role=cancel]').textContent = cancelText;
      overlay.querySelector('[data-role=ok]').textContent = okText;
      document.body.appendChild(overlay);

      const cleanup = (result) => {
        overlay.classList.remove('active');
        setTimeout(() => overlay.remove(), 220);
        document.removeEventListener('keydown', onKey);
        resolve(result);
      };
      const onKey = (e) => {
        if (e.key === 'Escape') cleanup(false);
        if (e.key === 'Enter') cleanup(true);
      };

      overlay.querySelector('[data-role=cancel]').addEventListener('click', () => cleanup(false));
      overlay.querySelector('[data-role=ok]').addEventListener('click', () => cleanup(true));
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) cleanup(false);
      });
      document.addEventListener('keydown', onKey);

      requestAnimationFrame(() => {
        overlay.classList.add('active');
        overlay.querySelector('[data-role=ok]').focus();
      });
    });
  }

  /* ============ PROMPT ============ */
  function showPrompt(message, opts = {}) {
    const {
      title = 'Ingresar valor',
      okText = 'Aceptar',
      cancelText = 'Cancelar',
      placeholder = '',
      type = 'text',
      defaultValue = '',
      min,
      max,
      validate,
    } = opts;

    return new Promise((resolve) => {
      const overlay = document.createElement('div');
      overlay.className = 'prompt-overlay';
      overlay.setAttribute('role', 'dialog');
      overlay.setAttribute('aria-modal', 'true');
      overlay.innerHTML = `
        <div class="prompt-box" role="document">
          <h3></h3>
          <p></p>
          <input class="input" type="${type}" />
          <div class="prompt-actions">
            <button type="button" class="remove" data-role="cancel"></button>
            <button type="button" class="add" data-role="ok"></button>
          </div>
        </div>
      `;
      overlay.querySelector('h3').textContent = title;
      overlay.querySelector('p').textContent = message;
      const input = overlay.querySelector('input');
      input.placeholder = placeholder;
      input.value = defaultValue;
      if (min !== undefined) input.min = min;
      if (max !== undefined) input.max = max;
      overlay.querySelector('[data-role=cancel]').textContent = cancelText;
      overlay.querySelector('[data-role=ok]').textContent = okText;

      document.body.appendChild(overlay);

      const cleanup = (result) => {
        overlay.classList.remove('active');
        setTimeout(() => overlay.remove(), 220);
        document.removeEventListener('keydown', onKey);
        resolve(result);
      };
      const submit = () => {
        const val = input.value.trim();
        if (typeof validate === 'function') {
          const err = validate(val);
          if (err) {
            showToast(err, 'error');
            input.focus();
            return;
          }
        }
        cleanup(val === '' ? null : val);
      };
      const onKey = (e) => {
        if (e.key === 'Escape') cleanup(null);
        if (e.key === 'Enter') {
          e.preventDefault();
          submit();
        }
      };

      overlay.querySelector('[data-role=cancel]').addEventListener('click', () => cleanup(null));
      overlay.querySelector('[data-role=ok]').addEventListener('click', submit);
      overlay.addEventListener('click', (e) => {
        if (e.target === overlay) cleanup(null);
      });
      document.addEventListener('keydown', onKey);

      requestAnimationFrame(() => {
        overlay.classList.add('active');
        input.focus();
        input.select();
      });
    });
  }

  window.showToast = showToast;
  window.showConfirm = window.showConfirm || showConfirm;
  window.showPrompt = showPrompt;
  window.UI = { showToast, showConfirm, showPrompt };
})();
