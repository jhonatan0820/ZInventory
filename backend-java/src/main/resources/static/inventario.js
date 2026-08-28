/* =====================================================
   VARIABLES GLOBALES
===================================================== */
let productos = [];
let deleteMode = false;
let creandoProducto = false;
let categorias = [];
let hintTallaTimer = null;
const API_BASE = (typeof window !== 'undefined' && typeof window.API_BASE === 'string')
  ? window.API_BASE
  : '';

function debounce(fn, wait = 150) {
  let t;
  return (...args) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...args), wait);
  };
}

let pendingRequests = 0;

function setLoading(active) {
  const overlay = document.getElementById('loadingOverlay');
  if (!overlay) return;
  overlay.classList.toggle('active', active);
  overlay.setAttribute('aria-hidden', active ? 'false' : 'true');
}

function beginRequest() {
  pendingRequests += 1;
  setLoading(true);
}

function endRequest() {
  pendingRequests = Math.max(0, pendingRequests - 1);
  if (pendingRequests === 0) setLoading(false);
}

function apiFetch(url, options = {}) {
  beginRequest();
  return fetch(url, options).finally(endRequest);
}

function showConfirm(message) {
  const overlay = document.getElementById('confirmOverlay');
  const msgEl = document.getElementById('confirmMessage');
  const btnOk = document.getElementById('confirmOk');
  const btnCancel = document.getElementById('confirmCancel');

  return new Promise(resolve => {
    if (!overlay || !msgEl || !btnOk || !btnCancel) {
      resolve(false);
      return;
    }

    msgEl.textContent = message;
    overlay.classList.add('active');
    overlay.setAttribute('aria-hidden', 'false');

    const cleanup = () => {
      overlay.classList.remove('active');
      overlay.setAttribute('aria-hidden', 'true');
      btnOk.onclick = null;
      btnCancel.onclick = null;
    };

    btnOk.onclick = () => {
      cleanup();
      resolve(true);
    };

    btnCancel.onclick = () => {
      cleanup();
      resolve(false);
    };
  });
}

/* =====================================================
   CONTROL DE PASOS (VISIBLE DESDE HTML)
===================================================== */


window.nextStep = function (n) {
  // Mostrar step
  document.querySelectorAll('.step').forEach(s => s.style.display = 'none');
  document.getElementById('step' + n).style.display = 'block';

  // Actualizar indicadores
  document.querySelectorAll('.step-indicator').forEach((el, i) => {
    el.classList.toggle('active', i === n - 1);
    el.classList.toggle('done', i < n - 1);
  });
};

window.prevStep = function (n) {
  nextStep(n);
};

/* =====================================================
   DOM READY
===================================================== */
document.addEventListener('DOMContentLoaded', () => {

  /* =====================================================
    Header compacto al scrollear (con histéresis anti-tembleque)
  ===================================================== */
  const scroller = document.querySelector('.main-content');
  const pageHeader = document.querySelector('.page-header');
  if (scroller && pageHeader) {
    let ticking = false;
    let isCompact = false;
    const THRESHOLD_DOWN = 60;
    const THRESHOLD_UP = 10;
    scroller.addEventListener('scroll', () => {
      if (ticking) return;
      ticking = true;
      requestAnimationFrame(() => {
        const y = scroller.scrollTop;
        if (!isCompact && y > THRESHOLD_DOWN) {
          isCompact = true;
          pageHeader.classList.add('is-compact');
        } else if (isCompact && y < THRESHOLD_UP) {
          isCompact = false;
          pageHeader.classList.remove('is-compact');
        }
        ticking = false;
      });
    }, { passive: true });
  }
  /* ===================================================== 
  sesion
===================================================== */
  apiFetch(`${API_BASE}/CheckSession`, {
    credentials: 'include'
  })
  .then(r => {
    if (r.status === 401) {
      window.location.href = './index.html';
    }
  });


  /* ===================================================== 
  ELEMENTOS
===================================================== */
  const grid         = document.getElementById('productosGrid');
  const filtroProducto = document.getElementById('filtroProducto');
  const filtroCategoria = document.getElementById('filtroCategoria');
  const filtroTalla  = document.getElementById('filtroTalla');
  const filtroTallaContainer = document.querySelector('.filtro-talla');
  const hintTalla = document.getElementById('hintTallaInventario');
  const btnOpenFiltros = document.getElementById('btnOpenFiltros');
  const filtrosResumen = document.getElementById('filtrosResumen');
  const filtrosMobileModal = document.getElementById('filtrosMobileModal');
  const btnCloseFiltrosMobile = document.getElementById('btnCloseFiltrosMobile');
  const btnAplicarFiltrosMobile = document.getElementById('btnAplicarFiltrosMobile');
  const filtroProductoMobile = document.getElementById('filtroProductoMobile');
  const filtroCategoriaMobile = document.getElementById('filtroCategoriaMobile');
  const filtroTallaMobile = document.getElementById('filtroTallaMobile');
  const filtroTallaMobileContainer = document.querySelector('.filtro-talla-mobile');
  const hintTallaMobile = document.getElementById('hintTallaInventarioMobile');
  const searchInput  = document.getElementById('searchInput');
  const btnNew    = document.getElementById('btnNew');
  const btnDelete = document.getElementById('btnDelete');
  const btnCancelDelete = document.getElementById('btnCancelDelete');
  const modal     = document.getElementById('modalNew');
  const closeBtn  = document.getElementById('closeModal');

  const nombreProducto = document.getElementById('nombreProducto');
  const marcaInput    = document.getElementById('marcaInput');
  const estiloInput   = document.getElementById('estiloInput');

  const colorSelect   = document.getElementById('colorSelect');
  const nuevoColor    = document.getElementById('nuevoColor');

  const tallasOpciones = document.getElementById('tallasOpciones');
  const tablaVariantes = document.getElementById('tablaVariantes');

  /* =====================================================
     CARGA INICIAL
  ===================================================== */
  cargarProductos();
  cargarCategorias();
  cargarColores();
  cargarFiltroProductos();
  cargarFiltroCategorias();
  cargarFiltroTallas(filtroCategoria.value);
  cargarGeneros();

  if (modal) {
    modal.style.display = 'none';
  }

  document.querySelectorAll('.step').forEach(s => {
    s.style.display = 'none';
  });

  const step1 = document.getElementById('step1');
  if (step1) step1.style.display = 'block';

  function mostrarHintTalla(texto, tipo = 'waiting', ocultarEnMs = 3000) {
    if (!hintTalla) return;
    hintTalla.textContent = texto;
    hintTalla.classList.remove('is-hidden', 'is-ready', 'is-waiting');
    hintTalla.classList.add(tipo === 'ready' ? 'is-ready' : 'is-waiting');

    if (hintTallaTimer) clearTimeout(hintTallaTimer);
    if (ocultarEnMs > 0) {
      hintTallaTimer = setTimeout(() => {
        hintTalla.classList.add('is-hidden');
      }, ocultarEnMs);
    }
  }

  if (filtroTallaContainer && filtroTalla) {
    filtroTallaContainer.addEventListener('click', () => {
      if (!filtroTalla.disabled) return;
      mostrarHintTalla('Seleccione una categoría para buscar las tallas disponibles.', 'waiting', 3000);
    });
  }

  function isMobileLayout() {
    return window.matchMedia('(max-width: 768px)').matches;
  }

  function copiarOpciones(origen, destino) {
    if (!origen || !destino) return;
    destino.innerHTML = origen.innerHTML;
  }

  function actualizarResumenFiltros() {
    if (!filtrosResumen) return;

    const chips = [];
    const categoriaText = filtroCategoria.options[filtroCategoria.selectedIndex]?.textContent || '';
    const productoText = filtroProducto.options[filtroProducto.selectedIndex]?.textContent || '';
    const tallaText = filtroTalla.options[filtroTalla.selectedIndex]?.textContent || '';

    if (filtroCategoria.value) chips.push({ key: 'categoria', text: `Categoria: ${categoriaText}` });
    if (filtroProducto.value) chips.push({ key: 'producto', text: `Producto: ${productoText}` });
    if (filtroTalla.value) chips.push({ key: 'talla', text: `Talla: ${tallaText}` });

    if (!chips.length) {
      filtrosResumen.innerHTML = '<span class="filtro-chip filtro-chip-empty">Sin filtros aplicados</span>';
      return;
    }

    filtrosResumen.innerHTML = chips
      .map(chip => `
        <span class="filtro-chip">
          <span class="chip-text">${chip.text}</span>
          <button type="button" class="chip-remove" data-filter="${chip.key}" aria-label="Quitar ${chip.key}">x</button>
        </span>
      `)
      .join('');
  }

  function setSelectValueIfExists(selectEl, value) {
    if (!selectEl) return;
    if (!value) {
      selectEl.value = '';
      return;
    }
    const exists = [...selectEl.options].some(opt => opt.value === value);
    selectEl.value = exists ? value : '';
  }

  function sincronizarFiltrosMobile() {
    if (!filtroProductoMobile || !filtroCategoriaMobile || !filtroTallaMobile) return;

    copiarOpciones(filtroProducto, filtroProductoMobile);
    copiarOpciones(filtroCategoria, filtroCategoriaMobile);
    copiarOpciones(filtroTalla, filtroTallaMobile);

    filtroProductoMobile.value = filtroProducto.value;
    filtroCategoriaMobile.value = filtroCategoria.value;
    filtroTallaMobile.value = filtroTalla.value;

    filtroTallaMobile.disabled = filtroTalla.disabled;
    if (hintTallaMobile) hintTallaMobile.classList.add('is-hidden');
    actualizarResumenFiltros();
  }

  function cargarFiltroProductosMobile(idCategoria) {
    if (!filtroProductoMobile) return;

    const categoriaId = Number(idCategoria);
    const categoria = categorias.find(c => c.id_categoria === categoriaId);
    const categoriaClave = categoria ? normalizarClave(categoria.nombre) : '';

    const lista = categoriaClave
      ? productos.filter(p => normalizarClave(p.categoria) === categoriaClave)
      : productos;

    const usados = new Set();
    filtroProductoMobile.innerHTML = '<option value="">Todos los productos</option>';

    lista.forEach(p => {
      const clave = normalizarClave(p.nomproducto);
      if (usados.has(clave)) return;
      usados.add(clave);
      const nombre = p.nomproducto.replace(/\b\w/g, l => l.toUpperCase());
      filtroProductoMobile.innerHTML += `<option value="${clave}">${nombre}</option>`;
    });
  }

  function cargarFiltroTallasMobile(idCategoria) {
    if (!filtroTallaMobile) return;

    if (!idCategoria) {
      filtroTallaMobile.innerHTML = '<option value="">Selecciona categoria</option>';
      filtroTallaMobile.disabled = true;
      if (hintTallaMobile) hintTallaMobile.classList.add('is-hidden');
      return;
    }

    filtroTallaMobile.disabled = false;
    apiFetch(`${API_BASE}/GetTallasPorCategoria?id_categoria=${idCategoria}`)
      .then(r => r.json())
      .then(data => {
        filtroTallaMobile.innerHTML = '<option value="">Todas las tallas</option>';
        data.forEach(t => {
          filtroTallaMobile.innerHTML += `<option value="${t.talla}">${t.talla}</option>`;
        });
      })
      .catch(() => {
        filtroTallaMobile.innerHTML = '<option value="">Todas las tallas</option>';
      });
  }

  function abrirFiltrosMobile() {
    if (!filtrosMobileModal || !isMobileLayout()) return;
    sincronizarFiltrosMobile();
    filtrosMobileModal.classList.add('active');
    filtrosMobileModal.setAttribute('aria-hidden', 'false');
    setTimeout(() => {
      if (filtroCategoriaMobile) filtroCategoriaMobile.focus();
    }, 0);
  }

  function cerrarFiltrosMobile() {
    if (!filtrosMobileModal) return;
    const activeEl = document.activeElement;
    if (activeEl && filtrosMobileModal.contains(activeEl)) {
      activeEl.blur();
    }
    filtrosMobileModal.classList.remove('active');
    filtrosMobileModal.setAttribute('aria-hidden', 'true');
    if (hintTallaMobile) hintTallaMobile.classList.add('is-hidden');
    if (btnOpenFiltros) {
      setTimeout(() => btnOpenFiltros.focus(), 0);
    }
  }

  function mostrarHintTallaMobile(texto, ocultarEnMs = 3000) {
    if (!hintTallaMobile) return;
    hintTallaMobile.textContent = texto;
    hintTallaMobile.classList.remove('is-hidden');
    if (hintTallaTimer) clearTimeout(hintTallaTimer);
    if (ocultarEnMs > 0) {
      hintTallaTimer = setTimeout(() => {
        hintTallaMobile.classList.add('is-hidden');
      }, ocultarEnMs);
    }
  }

  if (btnOpenFiltros) {
    btnOpenFiltros.addEventListener('click', abrirFiltrosMobile);
  }

  if (btnCloseFiltrosMobile) {
    btnCloseFiltrosMobile.addEventListener('click', cerrarFiltrosMobile);
  }

  if (filtrosMobileModal) {
    filtrosMobileModal.addEventListener('click', (e) => {
      if (e.target.matches('[data-close-filtros]')) {
        cerrarFiltrosMobile();
      }
    });
  }

  if (filtroTallaMobileContainer && filtroTallaMobile) {
    filtroTallaMobileContainer.addEventListener('click', () => {
      if (!filtroTallaMobile.disabled) return;
      mostrarHintTallaMobile('Seleccione una categoría para buscar las tallas disponibles.', 3000);
    });
  }

  if (filtroCategoriaMobile) {
    filtroCategoriaMobile.addEventListener('change', () => {
      const idCategoria = filtroCategoriaMobile.value;
      if (filtroProductoMobile) filtroProductoMobile.value = '';
      if (filtroTallaMobile) filtroTallaMobile.value = '';
      cargarFiltroProductosMobile(idCategoria);
      cargarFiltroTallasMobile(idCategoria);
    });
  }

  if (btnAplicarFiltrosMobile) {
    btnAplicarFiltrosMobile.addEventListener('click', () => {
      const categoriaValue = filtroCategoriaMobile ? filtroCategoriaMobile.value : '';
      const productoValue = filtroProductoMobile ? filtroProductoMobile.value : '';
      const tallaValue = filtroTallaMobile ? filtroTallaMobile.value : '';
      const categoriaCambio = filtroCategoria.value !== categoriaValue;

      filtroCategoria.value = categoriaValue;
      cargarFiltroProductos(categoriaValue);

      Promise.resolve(cargarFiltroTallas(categoriaValue))
        .finally(() => {
          setSelectValueIfExists(filtroProducto, productoValue);
          setSelectValueIfExists(filtroTalla, tallaValue);
          render();
          sincronizarFiltrosMobile();
          actualizarResumenFiltros();
          cerrarFiltrosMobile();
          if (categoriaCambio) {
            mostrarHintTalla('Ya puedes filtrar por talla.', 'ready', 1600);
          }
        });
    });
  }

  if (filtrosResumen) {
    filtrosResumen.addEventListener('click', (e) => {
      const btn = e.target.closest('.chip-remove');
      if (!btn) return;
      const key = btn.dataset.filter;

      if (key === 'categoria') {
        filtroCategoria.value = '';
        filtroProducto.value = '';
        filtroTalla.value = '';
        cargarFiltroProductos('');
        cargarFiltroTallas('');
      } else if (key === 'producto') {
        filtroProducto.value = '';
      } else if (key === 'talla') {
        filtroTalla.value = '';
      }

      render();
      sincronizarFiltrosMobile();
      actualizarResumenFiltros();
    });
  }

  window.addEventListener('resize', () => {
    if (!isMobileLayout()) {
      cerrarFiltrosMobile();
    }
    sincronizarFiltrosMobile();
  });
  


  /* =====================================================
     EVENTOS GENERALES
  ===================================================== */



  window.logout = async function logout() {
  const ok = await showConfirm('¿Deseas cerrar sesión?');
  if (!ok) return;

  apiFetch(`${API_BASE}/Logout`, {
    method: 'POST',
    credentials: 'include'
  })
  .then(r => {
    if (!r.ok) throw new Error('Error logout');
    return r.json();
  })
  .then(() => {
      window.location.href = './index.html';
  })
  .catch(err => {
    console.error(err);
    showToast('Error cerrando sesión', 'error');
  });
}

function cargarGeneros() {
  apiFetch(`${API_BASE}/GetGeneros`)
    .then(r => r.json())
    .then(data => renderGeneros(data));
}

  grid.addEventListener('click', async (e) => {
  const cardClick = e.target.closest('.card');
  const clickEnCheckbox = e.target.classList.contains('select-product');
  const clickEnAccionStock =
    e.target.classList.contains('btn-add') ||
    e.target.classList.contains('btn-rest');

  // En modo eliminar: tocar la card también selecciona/deselecciona
  if (deleteMode && cardClick && !clickEnCheckbox && !clickEnAccionStock) {
    const check = cardClick.querySelector('.select-product');
    if (check) {
      check.checked = !check.checked;
      cardClick.classList.toggle('active', check.checked);
    }
  }

  /* ==========================
     BOTÓN SUMAR (ENTRADA)
  ========================== */
  if (e.target.classList.contains('btn-add')) {
    const id = e.target.dataset.id;
    const stockDiv = document.getElementById(`stock-${id}`);
    let stock = Number(stockDiv.innerText);

    const cantidadStr = await showPrompt('¿Cuántas unidades va a agregar?', {
      title: 'Entrada de stock',
      okText: 'Agregar',
      type: 'number',
      placeholder: 'Cantidad',
      min: 1,
      validate: (v) => (!v || Number(v) <= 0 ? 'Ingresa una cantidad válida' : null),
    });
    if (cantidadStr === null) return;
    const cantidad = Number(cantidadStr);
    if (!cantidad || cantidad <= 0) return;

    stock += cantidad;
    stockDiv.innerText = stock;
    actualizarStockUI(id, stock);

    // 👉 backend (entrada sin precio)
    actualizarEntrada(id, cantidad);
    recalcularKPIs();
  }

  /* ==========================
     BOTÓN RESTAR (VENTA)
  ========================== */
  if (e.target.classList.contains('btn-rest')) {
    const id = e.target.dataset.id;
    const stockDiv = document.getElementById(`stock-${id}`);
    let stock = Number(stockDiv.innerText);

    if (stock <= 0) {
      showToast('No hay stock disponible', 'error');
      return;
    }

    const cantidadStr = await showPrompt('¿Cuántas unidades va a vender?', {
      title: 'Salida de stock',
      okText: 'Continuar',
      type: 'number',
      placeholder: 'Cantidad',
      min: 1,
      max: stock,
      validate: (v) => {
        const n = Number(v);
        if (!n || n <= 0) return 'Ingresa una cantidad válida';
        if (n > stock) return 'No hay suficiente stock';
        return null;
      },
    });
    if (cantidadStr === null) return;
    const cantidad = Number(cantidadStr);
    if (!cantidad || cantidad <= 0) return;
    if (cantidad > stock) {
      showToast('No hay suficiente stock', 'error');
      return;
    }

    const precioStr = await showPrompt('Ingrese el precio de venta', {
      title: 'Precio de venta',
      okText: 'Registrar venta',
      type: 'number',
      placeholder: 'Precio unitario',
      min: 1,
      validate: (v) => (!v || Number(v) <= 0 ? 'Ingresa un precio válido' : null),
    });
    if (precioStr === null) return;
    const precioVenta = Number(precioStr);
    if (!precioVenta || precioVenta <= 0) return;

    actualizarStock(id, cantidad, precioVenta);

    stock -= cantidad;
    stockDiv.innerText = stock;
    actualizarStockUI(id, stock);
    recalcularKPIs();
  }

});

  grid.addEventListener('change', (e) => {
    if (!e.target.classList.contains('select-product')) return;
    const card = e.target.closest('.card');
    if (card) {
      card.classList.toggle('active', e.target.checked);
    }
  });


  btnNew.onclick = () => {
  resetModalProducto();
  modal.style.display = 'flex';
  nextStep(1);
};

  closeBtn.onclick = () => modal.style.display = 'none';

  const debouncedRender = debounce(render, 150);

  filtroCategoria.onchange = () => {
    cargarFiltroTallas(filtroCategoria.value);
    filtroTalla.value = '';
    cargarFiltroProductos(filtroCategoria.value);
    filtroProducto.value = '';
    debouncedRender();
    sincronizarFiltrosMobile();
    actualizarResumenFiltros();
  };
  filtroProducto.onchange = () => {
    debouncedRender();
    sincronizarFiltrosMobile();
    actualizarResumenFiltros();
  };
  filtroTalla.onchange  = () => {
    debouncedRender();
    sincronizarFiltrosMobile();
    actualizarResumenFiltros();
  };
  searchInput.oninput   = debouncedRender;

  /* =====================================================
     ESTILO Y MARCA OPCIONALES
  ===================================================== */

  document.querySelectorAll('.na-toggle').forEach(btn => {
  btn.onclick = () => {
    const input = document.getElementById(btn.dataset.target);
    const isActive = btn.classList.toggle('active');

    input.disabled = isActive;

    if (isActive) {
      input.value = '';
    }
  };
});


  function setDeleteMode(enabled) {
    deleteMode = enabled;
    render();
    if (btnCancelDelete) {
      btnCancelDelete.style.display = enabled ? 'inline-flex' : 'none';
    }
    if (btnDelete) {
      btnDelete.textContent = enabled ? 'Eliminar seleccionados' : '🗑 Eliminar productos';
    }
  }

  /* =====================================================
     ELIMINAR PRODUCTOS (FUNCIONA)
  ===================================================== */
  btnDelete.onclick = async () => {

    // Paso 1: activar selección
    if (!deleteMode) {
      setDeleteMode(true);
      return;
    }

    // Paso 2: recoger IDs
    const ids = [];
    document.querySelectorAll('.select-product:checked').forEach(cb => {
      ids.push(cb.closest('.card').dataset.id);
    });

    if (!ids.length) {
      showToast('Selecciona al menos un producto o cancelar', 'info');
      return;
    }

    const ok = await showConfirm('¿Eliminar productos definitivamente?');
    if (!ok) return;

    apiFetch(`${API_BASE}/DeleteProductos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids })
    })
    .then(r => r.json())
    .then(() => {
      productos = productos.filter(p => !ids.includes(String(p.id_variante)));
      setDeleteMode(false);
      recalcularKPIs();
      showToast('Productos eliminados', 'success');
    })
    .catch(() => showToast('Error eliminando', 'error'));
  };

  if (btnCancelDelete) {
    btnCancelDelete.onclick = () => setDeleteMode(false);
  }

  /* =====================================================
     CARGAR PRODUCTOS
  ===================================================== */
  function cargarProductos() {
    apiFetch(`${API_BASE}/GetProductos`)
      .then(r => r.json())
      .then(data => {
        productos = data;
        cargarFiltroProductos(filtroCategoria.value);
        cargarFiltroCategorias();
        cargarFiltroTallas(filtroCategoria.value);
        render();
        sincronizarFiltrosMobile();
        actualizarResumenFiltros();
        recalcularKPIs();
      });
  }

  /* =====================================================
     RENDER
  ===================================================== */
  function render() {
  let lista = [...productos];

  const texto = searchInput.value.toLowerCase().trim();
  if (texto) {
    lista = lista.filter(p =>
      `${p.nomproducto} ${p.marca} ${p.estilo} ${p.color} ${p.talla}`
        .toLowerCase()
        .includes(texto)
    );
  }

  if (filtroCategoria.value) {
    const categoriaId = Number(filtroCategoria.value);
    const categoria = categorias.find(c => c.id_categoria === categoriaId);
    const categoriaClave = categoria ? normalizarClave(categoria.nombre) : '';

    if (categoriaClave) {
      lista = lista.filter(
        p => normalizarClave(p.categoria) === categoriaClave
      );
    }
  }

  if (filtroProducto.value) {
    const nombre = normalizarClave(filtroProducto.value);
    lista = lista.filter(
      p => normalizarClave(p.nomproducto) === nombre
    );
  }

  if (filtroTalla.value) {
    lista = lista.filter(
      p => String(p.talla) === filtroTalla.value
    );
  }

  pintar(lista);
}


  /* =====================================================
     PINTAR
  ===================================================== */
  function pintar(lista) {
    grid.innerHTML = '';

    if (!lista.length) {
      grid.innerHTML = `
        <div class="empty-state" style="grid-column:1/-1">
          <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 7 12 3 4 7v10l8 4 8-4V7z"/><path d="M4 7l8 4 8-4"/><path d="M12 11v10"/></svg>
          <h3>No hay productos</h3>
          <p>Ajusta los filtros o crea un nuevo producto para empezar.</p>
          <button type="button" class="btn btn-primary btn-sm" onclick="document.getElementById('btnNew').click()">+ Nuevo producto</button>
        </div>
      `;
      return;
    }

    lista.forEach(p => {
      const card = document.createElement('div');
      card.className = `card ${tieneDetalle(p) ? 'con-detalle' : 'sin-detalle'}`;
      card.dataset.id = p.id_variante;

      const stockNum = Number(p.stock) || 0;
      const badgeClass = stockNum < 5 ? 'badge-danger'
        : stockNum < 10 ? 'badge-warning'
        : 'badge-success';
      const badgeLabel = stockNum < 5 ? 'Stock bajo'
        : stockNum < 10 ? 'Por reponer'
        : 'En stock';

      card.innerHTML = `
        <input type="checkbox" class="select-product"
          style="display:${deleteMode ? 'block' : 'none'}"
          aria-label="Seleccionar producto">

        <div class="card-content">
          <span class="tag">
            ${escapeHtml(unirTexto(p.nomproducto, p.marca, p.estilo))}
          </span>

          ${tieneDetalle(p) ? `
            <h2 class="subtitle">
              ${escapeHtml(unirTexto(p.color, textoTalla(p.talla)))}
            </h2>
          ` : ''}

          <p class="info">
            Precio: $${Number(p.precio).toLocaleString('es-CO')}
          </p>
          <span class="badge ${badgeClass}" id="badge-${p.id_variante}">
            ${badgeLabel}
          </span>
        </div>

        <div class="controls">
          <button class="btn-rest" data-id="${p.id_variante}" aria-label="Registrar salida">−</button>
          <div class="stock" id="stock-${p.id_variante}" aria-label="Stock actual">${stockNum}</div>
          <button class="btn-add" data-id="${p.id_variante}" aria-label="Registrar entrada">+</button>
        </div>
      `;


      grid.appendChild(card);
    });
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function actualizarStockUI(id, stock) {
    const badge = document.getElementById(`badge-${id}`);
    if (!badge) return;
    badge.classList.remove('badge-danger','badge-warning','badge-success');
    if (stock < 5) { badge.classList.add('badge-danger'); badge.textContent = 'Stock bajo'; }
    else if (stock < 10) { badge.classList.add('badge-warning'); badge.textContent = 'Por reponer'; }
    else { badge.classList.add('badge-success'); badge.textContent = 'En stock'; }
    // Actualiza también el arreglo global para KPIs consistentes
    const item = productos.find(p => String(p.id_variante) === String(id));
    if (item) item.stock = stock;
  }

  function recalcularKPIs() {
    const kpiProd = document.getElementById('kpiProductos');
    const kpiVar  = document.getElementById('kpiVariantes');
    const kpiStock = document.getElementById('kpiStock');
    const kpiCat  = document.getElementById('kpiCategorias');
    if (!kpiProd) return;

    const productosSet = new Set();
    const categoriasSet = new Set();
    let stockTotal = 0;
    productos.forEach(p => {
      productosSet.add(normalizarClave(p.nomproducto));
      if (p.categoria) categoriasSet.add(normalizarClave(p.categoria));
      stockTotal += Number(p.stock) || 0;
    });
    kpiProd.textContent = productosSet.size.toLocaleString('es-CO');
    kpiVar.textContent  = productos.length.toLocaleString('es-CO');
    kpiStock.textContent = stockTotal.toLocaleString('es-CO');
    kpiCat.textContent  = categoriasSet.size.toLocaleString('es-CO');
  }
  window.recalcularKPIs = recalcularKPIs;

  /* =====================================================
      UTILIDADES
  ===================================================== */

function tieneDetalle(p) {
  return Boolean(
    (p.color && String(p.color).trim()) ||
    (p.talla && String(p.talla).trim())
  );
}

function unirTexto(...valores) {
  return valores
    .filter(v => v !== null && v !== undefined && String(v).trim() !== '')
    .join(' · ');
}

function textoTalla(talla) {
  if (talla === null || talla === undefined || String(talla).trim() === '') {
    return null;
  }
  return `Talla ${talla}`;
}

function normalizarTexto(texto) {
  if (!texto) return '';

  return texto
    .trim()                    // quita espacios inicio y al fin
    .replace(/\s+/g, ' ')      // un solo espacio entre palabras
    .toLowerCase()             // todo en minuscula
    .replace(/^\w/, c => c.toUpperCase()); // primera letra mayuscula
}

function normalizarClave(texto) {
  if (!texto) return '';
  return String(texto).trim().toLowerCase();
}


window.validarStep2 =   function validarStep2() {
  const nombre = nombreProducto.value;
  const marca = marcaInput.value;
  const estilo = estiloInput.value;

  const marcaNA = document
    .querySelector('[data-target="marcaInput"]')
    .classList.contains('active');

  const estiloNA = document
    .querySelector('[data-target="estiloInput"]')
    .classList.contains('active');

  // 🔴 Nombre obligatorio
  if (!nombre.trim()) {
    showToast('Debes ingresar el nombre del producto', 'error');
    nombreProducto.focus();
    return false;
  }

  // 🔴 Marca obligatoria si NO es NA
  if (!marcaNA && !marca.trim()) {
    showToast('Debe ingresar la marca o seleccionar NA', 'error');
    marcaInput.focus();
    return false;
  }

  // 🔴 Estilo obligatorio si NO es NA
  if (!estiloNA && !estilo.trim()) {
    showToast('Debe ingresar el estilo o seleccionar NA', 'error');
    estiloInput.focus();
    return false;
  }

  // ✅ Normalizar textos
  nombreProducto.value = normalizarTexto(nombre);
  if (!marcaNA) marcaInput.value = normalizarTexto(marca);
  if (!estiloNA) estiloInput.value = normalizarTexto(estilo);
  return true;
}


  /* =====================================================
     FILTROS
  ===================================================== */

  
function cargarFiltroTallas(idCategoria) {
  if (!idCategoria) {
    filtroTalla.innerHTML = '<option value="">Selecciona categoría</option>';
    filtroTalla.disabled = true;
    if (hintTalla) hintTalla.classList.add('is-hidden');
    sincronizarFiltrosMobile();
    return Promise.resolve();
  }

  filtroTalla.disabled = false;
  return apiFetch(`${API_BASE}/GetTallasPorCategoria?id_categoria=${idCategoria}`)
    .then(r => r.json())
    .then(data => {
      filtroTalla.innerHTML = '<option value="">Todas las tallas</option>';

      data.forEach(t => {
        filtroTalla.innerHTML += `
          <option value="${t.talla}">
            ${t.talla}
          </option>
        `;
      });
      mostrarHintTalla('Ya puedes filtrar por talla.', 'ready', 2500);
      sincronizarFiltrosMobile();
    })
    .catch(err => {
      console.error('Error cargando tallas:', err);
      mostrarHintTalla('No se pudieron cargar las tallas.', 'waiting', 3000);
      sincronizarFiltrosMobile();
    });
}


function cargarFiltroCategorias() {
  apiFetch(`${API_BASE}/GetCategorias`)
    .then(r => r.json())
    .then(data => {
      categorias = data || [];
      filtroCategoria.innerHTML = '<option value="">Todas las categorías</option>';

      categorias.forEach(c => {
        const nombre = c.nombre
          .replace(/\b\w/g, l => l.toUpperCase()); // capitalizar bonito

        filtroCategoria.innerHTML += `
          <option value="${c.id_categoria}">
            ${nombre}
          </option>
        `;
      });
      sincronizarFiltrosMobile();
    });
}

function cargarFiltroProductos(idCategoria) {
  // Si ya tenemos productos cargados, filtrar desde memoria
  if (productos && productos.length) {
    const categoriaId = Number(idCategoria);
    const categoria = categorias.find(c => c.id_categoria === categoriaId);
    const categoriaClave = categoria ? normalizarClave(categoria.nombre) : '';

    const lista = categoriaClave
      ? productos.filter(p => normalizarClave(p.categoria) === categoriaClave)
      : productos;

    const usados = new Set();
    filtroProducto.innerHTML = '<option value="">Todos los productos</option>';

    lista.forEach(p => {
      const clave = normalizarClave(p.nomproducto);
      if (usados.has(clave)) return;
      usados.add(clave);

      const nombre = p.nomproducto
        .replace(/\b\w/g, l => l.toUpperCase());

      filtroProducto.innerHTML += `
        <option value="${clave}">
          ${nombre}
        </option>
      `;
    });
    sincronizarFiltrosMobile();
    return;
  }

  // Fallback si aún no hay productos en memoria
  apiFetch(`${API_BASE}/GetNombresProductos`)
    .then(r => r.json())
    .then(data => {
      filtroProducto.innerHTML = '<option value="">Todos los productos</option>';

      data.forEach(p => {
        const nombre = p.nombre
          .replace(/\b\w/g, l => l.toUpperCase());

        filtroProducto.innerHTML += `
          <option value="${normalizarClave(p.nombre)}">
            ${nombre}
          </option>
        `;
      });
      sincronizarFiltrosMobile();
    });
}


  /* =====================================================
     TALLAS
  ===================================================== */

window.seleccionarTodasTallas = function seleccionarTodasTallas() {
  const botones = document.querySelectorAll('.talla-btn');
  if (!botones.length) return;

  const todasSeleccionadas = [...botones].every(
    btn => btn.classList.contains('active')
  );

  botones.forEach(btn => {
    btn.classList.toggle('active', !todasSeleccionadas);
  });

  // actualizar texto del botón
  const btnToggle = document.getElementById('btnSelectAllTallas');
  btnToggle.textContent = todasSeleccionadas
    ? 'Seleccionar todas'
    : 'Deseleccionar todas';

  generarTablaStock();
}


window.cargarTallas = function cargarTallas() {
  apiFetch(`${API_BASE}/GetTallasPorCategoriaGenero?id_categoria=${categoriaSeleccionada}&id_genero=${generoSeleccionado}`)
    .then(r => r.json())
    .then(data => {
      const wrapper  = document.getElementById('tallasWrapper');
      const section  = document.getElementById('tallasSection');
      const mensaje  = document.getElementById('tallasMensaje');
      const btnAll   = document.getElementById('btnSelectAllTallas');

      // 🔄 RESET TOTAL (CLAVE)
      tallasOpciones.innerHTML = '';
      variantesGrid.innerHTML = '';
      btnAll.style.display = 'none';
      mensaje.style.display = 'none';
      section.style.display = 'none';   // 🔑 ESTO FALTABA
      wrapper.classList.remove('hidden');

      // ❌ NO HAY TALLAS
      if (!data.length) {
        mensaje.style.display = 'block';
        return;
      }

      // ✅ HAY TALLAS
      section.style.display = 'block';
      btnAll.style.display = 'block';
      btnAll.textContent = 'Seleccionar todas';

      data.forEach(t => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'talla-btn';
        btn.textContent = t.valor;

        btn.onclick = () => {
          btn.classList.toggle('active');
          actualizarBotonSeleccionTodas();
          generarTablaStock();
        };

        tallasOpciones.appendChild(btn);
      });
    });
};





function actualizarBotonSeleccionTodas() {
  const botones = document.querySelectorAll('.talla-btn');
  const btnToggle = document.getElementById('btnSelectAllTallas');
  if (!botones.length) return;

  const todasSeleccionadas = [...botones].every(
    btn => btn.classList.contains('active')
  );

  btnToggle.textContent = todasSeleccionadas
    ? 'Deseleccionar todas'
    : 'Seleccionar todas';
}




  /* =====================================================
     VARIANTES
  ===================================================== */
  window.generarVariantes = function () {
    tablaVariantes.innerHTML = '';

    const tallas = [...tallasOpciones.querySelectorAll('input:checked')]
      .map(cb => cb.value);

    if (!tallas.length) {
      showToast('Selecciona tallas', 'info');
      return false;
    }

    tallas.forEach(t => {
      tablaVariantes.innerHTML += `
        <tr>
          <td>${t}</td>
          <td><input type="number" class="precio"></td>
          <td><input type="number" class="stock"></td>
        </tr>
      `;
    });

    return true;
  };


  /* =====================================================
     CATEGORIAS
  ===================================================== */

  function cargarCategorias() {
  apiFetch(`${API_BASE}/GetCategorias`)
    .then(r => r.json())
    .then(data => renderCategorias(data));
}

  window.mostrarCrearCategoria = function () {
  const panel = document.getElementById('crearCategoriaPanel');
  if (!panel) return;

  panel.style.display = 'block';
  panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
};

  window.cerrarCrearCategoria = () =>
    document.getElementById('crearCategoriaPanel').style.display = 'none';

  window.guardarCategoria = () => {
  const nuevaCategoria = document.getElementById('nuevaCategoria');
  let nombre = nuevaCategoria.value.trim();

  if (!nombre) {
    showToast('Escribe un nombre para la categoría', 'info');
    return;
  }

  if (!/^[a-zA-Z\s]+$/.test(nombre)) {
    showToast('La categoría solo puede tener letras', 'error');
    return;
  }

  nombre = nombre.charAt(0).toUpperCase() + nombre.slice(1).toLowerCase();

  apiFetch(`${API_BASE}/AddCategoria`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nombre })
  })
  .then(r => r.json())
  .then(res => {
    if (!res.ok) {
      showToast('Error creando categoría', 'error');
      return;
    }

    nuevaCategoria.value = '';
    cerrarCrearCategoria();
    cargarCategorias();
    showToast('Categoría creada. Ahora selecciónala.', 'success');
  })
  .catch(() => showToast('Error de conexión', 'error'));
};

  function renderCategorias(data) {
  const grid = document.getElementById('categoriaGrid');
  grid.innerHTML = '';

  data.forEach(c => {
    const btn = document.createElement('button');
    btn.className = 'category-btn';
    btn.textContent = c.nombre;

    btn.onclick = () => {
      categoriaSeleccionada = c.id_categoria;

      // quitar activo a todos
      document
        .querySelectorAll('.category-btn')
        .forEach(b => b.classList.remove('active'));

      // activar actual
      btn.classList.add('active');

      setTimeout(() => nextStep(2), 150);
    };

    grid.appendChild(btn);
  });
}

function renderGeneros(data) {
  const grid = document.getElementById('generoGrid');
  grid.innerHTML = '';

  data.forEach(g => {
    const btn = document.createElement('button');
    btn.className = 'gender-btn';
    btn.textContent = g.nombre;

    btn.onclick = () => {
      generoSeleccionado = g.id_genero;

      document
        .querySelectorAll('.gender-btn')
        .forEach(b => b.classList.remove('active'));

      btn.classList.add('active');
      document.getElementById('tallasWrapper').classList.remove('hidden');

      cargarTallas();
    };


    grid.appendChild(btn);
  });
}






  /* =====================================================
     COLORES
  ===================================================== */
  function cargarColores() {
    apiFetch(`${API_BASE}/GetColores`)
      .then(r => r.json())
      .then(data => {
        colorSelect.innerHTML = '';
        data.forEach(c =>
          colorSelect.innerHTML += `<option value="${c.id_color}">${c.nombre}</option>`
        );
      });
  }

  window.mostrarCrearColor = () =>
    document.getElementById('crearColorPanel').style.display = 'block';

  window.cerrarCrearColor = () =>
    document.getElementById('crearColorPanel').style.display = 'none';

  window.guardarColor = () => {
    let nombre = nuevoColor.value.trim();
    if (!/^[a-zA-Z]+$/.test(nombre)) {
      showToast('Solo letras', 'error');
      return;
    }

    nombre = nombre.charAt(0).toUpperCase() + nombre.slice(1).toLowerCase();

    apiFetch(`${API_BASE}/AddColor`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nombre })
    }).then(() => {
      cargarColores();
      cerrarCrearColor();
    });
  };

  /* =====================================================
     CREAR PRODUCTO
  ===================================================== */
 window.crearProducto = () => {
  if (creandoProducto) return;

  const btnCrear = document.querySelector('#step5 button[onclick="crearProducto()"]');
  creandoProducto = true;
  if (btnCrear) {
    btnCrear.disabled = true;
    btnCrear.textContent = 'Creando...';
  }

  const variantes = [];
  const filas = document.querySelectorAll('.variante-row');

  if (!filas.length) {
    showToast('No hay variantes para crear', 'error');
    creandoProducto = false;
    if (btnCrear) {
      btnCrear.disabled = false;
      btnCrear.textContent = 'Crear producto';
    }
    return;
  }

  const precioGeneral = Number(
    document.getElementById('precioGeneral')?.value || 0
  );

  if (!precioGeneral || precioGeneral <= 0) {
    showToast('Debes ingresar un precio general válido', 'error');
    creandoProducto = false;
    if (btnCrear) {
      btnCrear.disabled = false;
      btnCrear.textContent = 'Crear producto';
    }
    return;
  }

  for (const row of filas) {
    const talla = row.dataset.talla;

    const inputPrecio = row.querySelector('.precio-talla');
    const inputStock = row.querySelector('.stock');
    const btnPrecio = row.querySelector('.btn-precio');

    const precioPersonalizado = btnPrecio.classList.contains('active');
    const precio = precioPersonalizado
      ? Number(inputPrecio.value)
      : precioGeneral;

    const stock = Number(inputStock.value);

    if (stock < 0 || isNaN(stock)) {
      showToast(`Stock inválido para la talla ${talla}`, 'error');
      creandoProducto = false;
      if (btnCrear) {
        btnCrear.disabled = false;
        btnCrear.textContent = 'Crear producto';
      }
      return;
    }

    if (!precio || precio <= 0) {
      showToast(`Precio inválido para la talla ${talla}`, 'error');
      creandoProducto = false;
      if (btnCrear) {
        btnCrear.disabled = false;
        btnCrear.textContent = 'Crear producto';
      }
      return;
    }

    variantes.push({
      talla,
      precio,
      stock
    });
  }

  const payload = {
    id_genero: window.generoSeleccionado,
    id_categoria: window.categoriaSeleccionada,
    nombre: normalizarTexto(nombreProducto.value),
    marca: marcaInput.disabled ? '' : normalizarTexto(marcaInput.value),
    estilo: estiloInput.disabled ? '' : normalizarTexto(estiloInput.value),
    id_color: Number(colorSelect.value),
    variantes
  };

  console.log('Payload final:', payload);

  apiFetch(`${API_BASE}/AddProducto`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
    .then(r => r.json())
    .then(res => {
      if (!res.ok) {
        showToast('Error creando producto', 'error');
        console.error(res.error);
        return;
      }

      showToast('Producto creado correctamente', 'success');
      resetModalProducto();
      modal.style.display = 'none';
      cargarProductos();
    })
    .catch(err => {
      console.error(err);
      showToast('Error de conexión', 'error');
    })
    .finally(() => {
      creandoProducto = false;
      if (btnCrear) {
        btnCrear.disabled = false;
        btnCrear.textContent = 'Crear producto';
      }
    });
};

});

/* =====================================================
    STOCK
===================================================== */
/* TABLA STOCK */
function generarTablaStock() {
  const grid = document.getElementById('variantesGrid');
  const precioGeneral =
    document.getElementById('precioGeneral')?.value || '';

  grid.innerHTML = '';

  document
    .querySelectorAll('.talla-btn.active')
    .forEach(btn => {
      const talla = btn.textContent;

      grid.innerHTML += `
        <div class="variante-row" data-talla="${talla}">
          <div class="variante-talla">${talla}</div>

          <div class="precio-cell">
            <span class="precio-label">Precio general</span>
            <input
              type="number"
              class="precio-talla hidden"
              value="${precioGeneral}"
            >
          </div>

          <input type="number" class="stock" placeholder="Stock">

          <button
            type="button"
            class="btn-precio"
            onclick="togglePrecioTalla(this)"
          >
            Precio distinto
          </button>
        </div>
      `;
    });
}

function togglePrecioTalla(btn) {
  const row = btn.closest('.variante-row');
  const label = row.querySelector('.precio-label');
  const input = row.querySelector('.precio-talla');

  const activo = btn.classList.toggle('active');

  if (activo) {
    label.classList.add('hidden');
    input.classList.remove('hidden');
    input.focus();
  } else {
    input.classList.add('hidden');
    label.classList.remove('hidden');
    input.value =
      document.getElementById('precioGeneral')?.value || '';
  }
}




const precioGeneralInput = document.getElementById('precioGeneral');

if (precioGeneralInput) {
  precioGeneralInput.addEventListener('input', e => {
    const valor = e.target.value;

    document
      .querySelectorAll('.precio-talla.hidden')
      .forEach(input => {
        input.value = valor;
      });
  });
}


function resetModalProducto() {
  // 🔹 Reset pasos
  document.querySelectorAll('.step').forEach(s => {
    s.style.display = 'none';
  });
  document.getElementById('step1').style.display = 'block';

  document.querySelectorAll('.step-indicator').forEach((el, i) => {
    el.classList.toggle('active', i === 0);
    el.classList.remove('done');
  });

  // 🔹 Reset variables de selección
  window.categoriaSeleccionada = null;
  window.generoSeleccionado = null;

  // 🔹 Inputs texto
  nombreProducto.value = '';
  marcaInput.value = '';
  estiloInput.value = '';
  marcaInput.disabled = false;
  estiloInput.disabled = false;

  // 🔹 Botones NA
  document.querySelectorAll('.na-toggle').forEach(btn => {
    btn.classList.remove('active');
  });

  // 🔹 Precio general
  const precioGeneral = document.getElementById('precioGeneral');
  if (precioGeneral) precioGeneral.value = '';

  // 🔹 Tallas
  const tallasWrapper = document.getElementById('tallasWrapper');
  if (tallasWrapper) tallasWrapper.classList.add('hidden');

  const tallasOpciones = document.getElementById('tallasOpciones');
  if (tallasOpciones) tallasOpciones.innerHTML = '';

  // 🔹 Variantes (step 4)
  const variantesGrid = document.getElementById('variantesGrid');
  if (variantesGrid) variantesGrid.innerHTML = '';

  // 🔹 Categorías activas
  document
    .querySelectorAll('.category-btn.active')
    .forEach(b => b.classList.remove('active'));

  // 🔹 Géneros activos
  document
    .querySelectorAll('.gender-btn.active')
    .forEach(b => b.classList.remove('active'));
}


function actualizarStock(id_variante, cantidad, precio_venta) {
  apiFetch(`${API_BASE}/ActualizarStock`, {
    method: 'POST',
    credentials: "include",
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      id_variante,
      cantidad,
      precio_venta
    })
  })
  .then(r => r.json())
  .then(res => {
    if (!res.ok) {
      showToast('Error actualizando stock', 'error');
      console.error(res.error);
    }
  })
  .catch(err => {
    console.error(err);
    showToast('Error de conexión', 'error');
  });
}

function actualizarEntrada(id_variante, cantidad) {
  apiFetch(`${API_BASE}/EntradaStock`, {
    method: 'POST',
    credentials: "include",
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id_variante, cantidad })
  });
}
