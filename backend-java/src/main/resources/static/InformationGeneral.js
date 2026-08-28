  /* =====================================================
    VARIABLES GLOBALES
  ===================================================== */
  const ENDPOINT =
    '/InformationGeneral';

  let categoriaSeleccionada = null;
  let generoSeleccionado = null;
  let hintTallaTimer = null;

  let columnas = [];
  let filas = [];
  let filasFiltradas = [];

  /* =====================================================
    DOM READY
  ===================================================== */
  document.addEventListener('DOMContentLoaded', () => {
    cargarFiltroGenero();
    cargarFiltrosBase();
    configurarHeaderMovil();
    configurarAvisoTallaMovil();

    document.getElementById('btnGenerar')
      .addEventListener('click', generarReporte);

    document.getElementById('filtroCategoria')
      .addEventListener('change', e => {
        categoriaSeleccionada = e.target.value || null;
        intentarCargarTallas();
      });

    document.getElementById('filtroGenero')
      .addEventListener('change', e => {
        generoSeleccionado = e.target.value || null;
        intentarCargarTallas();
      });

    document.getElementById('btnDescargar')
      .addEventListener('click', descargarCSV);
  });

  function configurarAvisoTallaMovil() {
    const tallaSelect = document.getElementById('filtroTalla');
    const tallaContainer = document.querySelector('.filtro-talla');
    const hint = document.getElementById('hintTalla');
    if (!tallaSelect || !tallaContainer || !hint) return;

    tallaContainer.addEventListener('click', () => {
      if (!tallaSelect.disabled) return;

      hint.textContent = 'Primero elige categoría y género para habilitar este filtro.';
      hint.classList.remove('is-ready', 'is-hidden');
      hint.classList.add('is-waiting');

      if (hintTallaTimer) clearTimeout(hintTallaTimer);
      hintTallaTimer = setTimeout(() => {
        hint.classList.add('is-hidden');
      }, 3000);
    });
  }

  function configurarHeaderMovil() {
    const header = document.querySelector('header');
    if (!header) return;
    header.classList.remove('header-hidden');

    const mobileQuery = window.matchMedia('(max-width: 768px)');
    let lastY = window.scrollY || 0;
    const showNearTop = 96;
    const hideAfter = 140;

    const onScroll = () => {
      if (!mobileQuery.matches) {
        header.classList.remove('header-hidden');
        lastY = window.scrollY || 0;
        return;
      }

      const currentY = window.scrollY || 0;
      const delta = currentY - lastY;

      if (currentY <= showNearTop) {
        header.classList.remove('header-hidden');
      } else if (delta > 6 && currentY > hideAfter) {
        header.classList.add('header-hidden');
      }

      lastY = currentY;
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    mobileQuery.addEventListener('change', onScroll);
    onScroll();
  }

  /* =====================================================
    FILTROS BASE
  ===================================================== */
  async function cargarFiltrosBase() {
    cargarSelect(
      '/GetCategorias',
      'filtroCategoria',
      'id_categoria',
      'nombre'
    );

    cargarFiltroProductos();
    cargarFiltroEstilos();
  }

  async function cargarFiltroGenero() {
    try {
      const res = await fetch(
        '/GetGeneros',
        { credentials: 'include' }
      );
      const data = await res.json();

      const select = document.getElementById('filtroGenero');
      select.innerHTML = '<option value="">Ninguno</option>';

      data.forEach(g => {
        const opt = document.createElement('option');
        opt.value = g.id_genero;
        opt.textContent = g.nombre;
        select.appendChild(opt);
      });
    } catch (e) {
      console.error('Error cargando géneros', e);
    }
  }

  async function cargarFiltroProductos() {
    fetch('/GetProductos', {
      credentials: 'include'
    })
      .then(r => r.json())
      .then(data => {
        const select = document.getElementById('filtroProducto');
        select.innerHTML = '<option value="">Todos</option>';

        const usados = new Set();

        data.forEach(p => {
          if (usados.has(p.id_producto)) return;
          usados.add(p.id_producto);

          const opt = document.createElement('option');
          opt.value = p.id_producto;
          opt.textContent = p.nomproducto;
          select.appendChild(opt);
        });
      });
  }
  async function cargarFiltroProductos() {
    fetch('/GetProductos', {
      credentials: 'include'
    })
      .then(r => r.json())
      .then(data => {
        const select = document.getElementById('filtroProducto');
        select.innerHTML = '<option value="">Todos</option>';

        const nombresUsados = new Set();

        data.forEach(p => {
          // Normalizamos el nombre (quitar espacios y pasar a minúsculas) para comparar mejor
          const nombreNormalizado = p.nomproducto.trim().toLowerCase();

          if (nombresUsados.has(nombreNormalizado)) return;
          
          nombresUsados.add(nombreNormalizado);

          const opt = document.createElement('option');
          // Usamos el nombre como valor para que el procedimiento filtre por coincidencia de texto
          opt.value = p.nomproducto; 
          opt.textContent = p.nomproducto;
          select.appendChild(opt);
        });
      })
      .catch(err => console.error("Error al cargar productos:", err));
  }


  async function cargarFiltroEstilos() {
    const res = await fetch(
      '/GetEstilosUnicos',
      { credentials: 'include' }
    );

    const data = await res.json();
    const select = document.getElementById('filtroEstilo');

    select.innerHTML = '<option value="">Todos</option>';

    data.forEach(e => {
      const opt = document.createElement('option');
      opt.value = e.nombre; // texto
      opt.textContent = capitalizar(e.nombre);
      select.appendChild(opt);
    });
  }

  /* =====================================================
    TALLAS DEPENDIENTES
  ===================================================== */
  function intentarCargarTallas() {
    const select = document.getElementById('filtroTalla');
    const hint = document.getElementById('hintTalla');
    select.disabled = true;
    select.innerHTML =
      '<option value="">Talla</option>';

    if (!categoriaSeleccionada || !generoSeleccionado) {
      if (hint) {
        hint.textContent = 'Primero elige categoría y género para habilitar este filtro.';
        hint.classList.remove('is-ready');
        hint.classList.add('is-waiting');
        hint.classList.add('is-hidden');
      }
      return;
    }

    if (hint) {
      hint.textContent = 'Cargando tallas disponibles...';
      hint.classList.remove('is-ready');
      hint.classList.add('is-waiting');
      hint.classList.remove('is-hidden');
    }

    cargarTallasPorCategoriaGenero(
      categoriaSeleccionada,
      generoSeleccionado
    );
  }

  async function cargarTallasPorCategoriaGenero(idCategoria, idGenero) {
    const select = document.getElementById('filtroTalla');
    const hint = document.getElementById('hintTalla');
    select.innerHTML = '<option value="">Cargando tallas…</option>';

    try {
      const res = await fetch(
        `/GetTallasPorCategoriaGenero?id_categoria=${idCategoria}&id_genero=${idGenero}`,
        { credentials: 'include' }
      );

      const data = await res.json();
      select.innerHTML = '';

      if (!data.length) {
        select.innerHTML =
          '<option value="">Talla</option>';
        if (hint) {
          hint.textContent = 'No hay tallas para la categoría y género seleccionados.';
          hint.classList.remove('is-ready');
          hint.classList.add('is-waiting');
          hint.classList.remove('is-hidden');
        }
        return;
      }

      select.innerHTML = '<option value="">Talla</option>';

      data.forEach(t => {
        const opt = document.createElement('option');
        opt.value = t.id_talla;
        opt.textContent = t.valor;
        select.appendChild(opt);
      });
      if (hint) {
        hint.textContent = 'Ya puedes filtrar por talla.';
        hint.classList.remove('is-waiting');
        hint.classList.add('is-ready');
        hint.classList.remove('is-hidden');
        if (hintTallaTimer) clearTimeout(hintTallaTimer);
        hintTallaTimer = setTimeout(() => {
          hint.classList.add('is-hidden');
        }, 3000);
      }
    } catch (e) {
      select.innerHTML =
        '<option value="">Error cargando tallas</option>';
      if (hint) {
        hint.textContent = 'Error al cargar tallas. Intenta de nuevo.';
        hint.classList.remove('is-ready');
        hint.classList.add('is-waiting');
        hint.classList.remove('is-hidden');
      }
    } finally {
      select.disabled = false;
    }
  }

  /* =====================================================
    GENERAR REPORTE
  ===================================================== */

  async function generarReporte() {
    const params = new URLSearchParams();

    // Captura de valores
    const categoria = document.getElementById('filtroCategoria').value;
    const genero    = document.getElementById('filtroGenero').value;
    const producto  = document.getElementById('filtroProducto').value || '';
    const talla     = document.getElementById('filtroTalla').value;
    const estilo    = document.getElementById('filtroEstilo').value;

    // Construcción de URL
    if (categoria) params.append('categoria', categoria);
    if (genero)    params.append('genero', genero);
    if (producto)  params.append('producto', producto);
    if (talla)     params.append('talla', talla);
    if (estilo)    params.append('estilo', estilo);

    // UI - Estado de carga
    const progressBar = document.getElementById('progressBar');
    const btnGenerar = document.getElementById('btnGenerar');
    const errorDiv = document.getElementById('error');
    
    progressBar.style.display = 'flex'; 
    btnGenerar.disabled = true;
    errorDiv.textContent = '';
    console.log("Generando reporte con parámetros:", params.toString());

    try {
      const res = await fetch(`${ENDPOINT}?${params.toString()}`, { credentials: 'include' });

      if (!res.ok) {
        const errorData = await res.json();
        throw new Error(errorData.error || 'Error en el servidor');
      }

      const json = await res.json();

      // Validación de datos recibidos
      if (!json.columns || !json.rows) {
        throw new Error('El servidor no devolvió el formato esperado');
      }

      columnas = json.columns;
      filas = json.rows;
      filasFiltradas = [...json.rows];

      // Renderizado
      renderTabla(columnas, filas);
      calcularTotales(filas);

      document.getElementById('tableWrapper').style.display = 'block';
      document.getElementById('btnDescargar').disabled = false;

    } catch (e) {
      console.error("Error capturado:", e);
      errorDiv.textContent = e.message || 'No se pudo generar el reporte';
      document.getElementById('tableWrapper').style.display = 'none';
    } finally {
      progressBar.style.display = 'none';
      btnGenerar.disabled = false;
    }
  }


  /* =====================================================
    TABLA
  ===================================================== */
  function renderTabla(columnas, filas) {
    const thead = document.getElementById('thead');
    const tbody = document.getElementById('tbody');

    // 1. Renderizar Encabezados
    thead.innerHTML = '';
    columnas.forEach(col => {
      const th = document.createElement('th');
      th.textContent = col;
      thead.appendChild(th);
    });

    // 2. Renderizar Filas
    tbody.innerHTML = '';
    filas.forEach(row => {
      const tr = document.createElement('tr');
      
      // IMPORTANTE: En lugar de row.forEach, recorremos las columnas 
      // y buscamos ese valor dentro del objeto 'row'
      columnas.forEach(col => {
        const td = document.createElement('td');
        // Accedemos a la propiedad del objeto usando el nombre de la columna
        td.textContent = row[col] !== null ? row[col] : 'N/A';
        tr.appendChild(td);
      });

      tbody.appendChild(tr);
    });
  }


  /* =====================================================
    TOTALES
  ===================================================== */
  function calcularTotales(filas) {
    let totalStock = 0;
    let totalVenta = 0;

    filas.forEach(row => {
      const cantidad = parseInt(row["Cantidad"]) || 0;
      const totalFila = parseFloat(row["Total"].replace(/\./g, '')) || 0;

      totalStock += cantidad;
      totalVenta += totalFila;
    });

    document.getElementById('totalCantidad').textContent = totalStock.toLocaleString('de-DE');
    document.getElementById('totalValor').textContent = '$ ' + totalVenta.toLocaleString('de-DE');
  }


  /* =====================================================
    CSV
  ===================================================== */
  function descargarCSV() {
    if (!filasFiltradas.length) return;

    const csv = [
      columnas.join(','),
      ...filasFiltradas.map(row =>
        columnas.map(col => {
          const value = row[col] ?? '';
          return `"${String(value).replace(/"/g, '""')}"`;
        }).join(',')
      )
    ].join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');
    a.href = url;
    a.download = 'informacion_general.csv';
    a.click();

    URL.revokeObjectURL(url);
  }

  /* =====================================================
    UTILIDADES
  ===================================================== */
  async function cargarSelect(url, selectId, valueKey, labelKey) {
    try {
      const res = await fetch(url, { credentials: 'include' });
      const data = await res.json();
      const select = document.getElementById(selectId);

      data.forEach(item => {
        const opt = document.createElement('option');
        opt.value = item[valueKey];
        opt.textContent = item[labelKey];
        select.appendChild(opt);
      });
    } catch (e) {
      console.error(`Error cargando ${selectId}`);
    }
  }

  function capitalizar(txt) {
    return txt
      .trim()
      .replace(/\s+/g, ' ')
      .replace(/\b\w/g, l => l.toUpperCase());
  }
