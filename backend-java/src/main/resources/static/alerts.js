/* Shim: alerts.js legacy; showToast ahora vive en ui.js */
(function () {
  if (!window.showToast) {
    window.showToast = function () {};
  }
})();
