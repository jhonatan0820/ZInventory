/**
 * Configuracion global del frontend.
 *
 * Como el frontend ahora se sirve desde el mismo Spring Boot que la API,
 * usamos ruta relativa (same-origin). No hace falta CORS local.
 *
 * Si deseas apuntar a un backend en otro host (por ejemplo el legacy Python),
 * cambia el valor de window.API_BASE a la URL absoluta.
 */
(function () {
  // Same-origin. Cadena vacia porque las rutas del backend empiezan con "/".
  window.API_BASE = "";
})();
