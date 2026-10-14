(function () {
  // Helpers de UI compartidos entre dashboards (vanilla JS).

  window.TourInvestUI = window.TourInvestUI || {};

  // Tabs: despliega contenido usando [data-tab-id] y [data-tab-target]
  // dentro del mismo contenedor.
  window.TourInvestUI.initTabs = function initTabs(rootSelector) {
    var root = document.querySelector(rootSelector);
    if (!root) return;

    var buttons = root.querySelectorAll('[data-tab-id]');
    var panes = root.querySelectorAll('[data-tab-target]');
    if (!buttons.length || !panes.length) return;

    function setActive(id) {
      buttons.forEach(function (btn) {
        btn.classList.toggle('tab-btn--active', btn.getAttribute('data-tab-id') === id);
      });
      panes.forEach(function (pane) {
        pane.style.display = (pane.getAttribute('data-tab-target') === id) ? '' : 'none';
      });
    }

    buttons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-tab-id');
        if (!id) return;
        setActive(id);
      });
    });

    // Arranque: primer tab activo o primero disponible.
    var activeBtn = root.querySelector('[data-tab-id].tab-btn--active') || buttons[0];
    if (activeBtn) setActive(activeBtn.getAttribute('data-tab-id'));
  };

  // Modales
  window.TourInvestUI.initModals = function initModals() {
    document.querySelectorAll('[data-open-modal]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var sel = btn.getAttribute('data-open-modal');
        if (!sel) return;
        var modal = document.querySelector(sel);
        if (!modal) return;
        modal.classList.add('modal--open');
        modal.setAttribute('aria-hidden', 'false');
      });
    });

    document.querySelectorAll('[data-close-modal]').forEach(function (x) {
      x.addEventListener('click', function () {
        var modal = x.closest('.modal');
        if (!modal) return;
        modal.classList.remove('modal--open');
        modal.setAttribute('aria-hidden', 'true');
      });
    });

    // Backdrop click
    document.querySelectorAll('.modal .modal__backdrop').forEach(function (bd) {
      bd.addEventListener('click', function () {
        var modal = bd.closest('.modal');
        if (!modal) return;
        modal.classList.remove('modal--open');
        modal.setAttribute('aria-hidden', 'true');
      });
    });

    // ESC
    document.addEventListener('keydown', function (e) {
      if (e.key !== 'Escape') return;
      document.querySelectorAll('.modal.modal--open').forEach(function (m) {
        m.classList.remove('modal--open');
        m.setAttribute('aria-hidden', 'true');
      });
    });
  };

  // Toast
  window.TourInvestUI.toast = function toast(msg, variant) {
    variant = variant || 'success';
    var root = document.getElementById('toast-root');
    if (!root) {
      root = document.createElement('div');
      root.id = 'toast-root';
      document.body.appendChild(root);
    }

    var t = document.createElement('div');
    t.className = 'toast ' + (variant === 'error' ? 'toast--error' : 'toast--success');
    t.textContent = msg;
    root.appendChild(t);

    setTimeout(function () {
      t.classList.add('toast--hide');
      setTimeout(function () {
        if (t.parentNode) t.remove();
      }, 250);
    }, 2600);
  };

  // Expose legacy names if some HTML uses them
  window.showToast = function (msg) { window.TourInvestUI.toast(msg, 'success'); };

  window.setModalUser = function () { /* set by each page if needed */ };
})();

// =============================================================================
// Chrome compartido de los 3 paneles: sidebar plegable + tema + salir al inicio.
// Carga al DOMContentLoaded; si un elemento no existe (p. ej. la landing), no
// hace nada, para que app.js pueda usarse sin romper otras páginas.
// -----------------------------------------------------------------------------
(function () {
  var UI = (window.TourInvestUI = window.TourInvestUI || {});
  var CLAVE_TEMA = 'ti_tema';      // 'diurno' | 'nocturno' | 'sistema'
  var CLAVE_SIDEBAR = 'ti_sidebar'; // 'oculto' | 'visible'

  // ---- Tema (diurno / nocturno / sistema) -----------------------------------
  // Resuelve qué tema aplicar. 'sistema' sigue la preferencia del SO.
  function resolverTema(preferencia) {
    if (preferencia === 'diurno' || preferencia === 'nocturno') return preferencia;
    // sistema
    var oscuroSO = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    return oscuroSO ? 'nocturno' : 'diurno';
  }

  function aplicarTema() {
    var preferencia = localStorage.getItem(CLAVE_TEMA) || 'sistema';
    var tema = resolverTema(preferencia);
    document.documentElement.setAttribute('data-theme', tema);
    // Marcar el botón activo del selector, si existe en la vista Configuración.
    document.querySelectorAll('[data-tema-btn]').forEach(function (btn) {
      btn.classList.toggle('tema-btn--activo', btn.getAttribute('data-tema-btn') === preferencia);
    });
  }

  function definirTema(preferencia) {
    localStorage.setItem(CLAVE_TEMA, preferencia);
    aplicarTema();
  }

  // Si el usuario usa 'sistema' y cambia el tema del SO en caliente, lo seguimos.
  if (window.matchMedia) {
    var mq = window.matchMedia('(prefers-color-scheme: dark)');
    var onCambioSO = function () {
      if ((localStorage.getItem(CLAVE_TEMA) || 'sistema') === 'sistema') aplicarTema();
    };
    if (mq.addEventListener) mq.addEventListener('change', onCambioSO);
    else if (mq.addListener) mq.addListener(onCambioSO);
  }

  // ---- Sidebar plegable -----------------------------------------------------
  // HAY UN SOLO BOTÓN: #btn-toggle-sidebar, dentro del .brand del aside.
  // - Escritorio: alterna entre desplegado (250px) y plegado (76px: logo
  //   arriba, ☰, círculos de iniciales). Persiste en localStorage.
  // - Móvil (<=900px): el sidebar es una BARRA SUPERIOR fija (solo logo +
  //   nombre + ☰) y el mismo botón despliega el menú hacia abajo; al elegir
  //   un enlace se cierra solo. No usa overlay: empuja el contenido.
  var MOVIL_MQ = "(max-width: 900px)";

  function esMovil() {
    return window.matchMedia && window.matchMedia(MOVIL_MQ).matches;
  }

  function overlayMovil(visible) {
    // En móvil NO hay overlay: el menú empuja el contenido. Si quedó uno
    // de una versión anterior, se elimina.
    var fondo = document.querySelector('.sidebar-overlay');
    if (fondo) fondo.remove();
  }

  function drawerMovil(abrir) {
    var sidebar = document.querySelector('.sidebar');
    if (!sidebar) return;
    sidebar.classList.toggle('sidebar--movil-abierto', abrir);
    overlayMovil(abrir);
  }

  function aplicarSidebar() {
    var colapsado = localStorage.getItem(CLAVE_SIDEBAR) === 'oculto';
    var sidebar = document.querySelector('.sidebar');
    if (sidebar) sidebar.classList.toggle('sidebar--colapsado', colapsado);
    var layout = document.querySelector('.app-layout');
    if (layout) layout.classList.toggle('app-layout--sin-sidebar', colapsado);
    // En móvil el drawer arranca cerrado; en escritorio, sin overlay.
    if (esMovil()) drawerMovil(false);
    else overlayMovil(false);
  }

  function alternarSidebar() {
    // En móvil el mismo botón abre/cierra el drawer (full menú, no círculos).
    if (esMovil()) {
      var sidebar = document.querySelector('.sidebar');
      var abierto = sidebar && sidebar.classList.contains('sidebar--movil-abierto');
      drawerMovil(!abierto);
      return;
    }
    var oculto = localStorage.getItem(CLAVE_SIDEBAR) === 'oculto';
    localStorage.setItem(CLAVE_SIDEBAR, oculto ? 'visible' : 'oculto');
    aplicarSidebar();
  }

  // ---- Salir al inicio (botón del logo en el topbar) ------------------------
  // Antes navegaba al index SIN cerrar sesión (incoherente con "Cerrar sesión").
  // Ahora pregunta, limpia la sesión y recién entonces sale al landing.
  function salirAlInicio() {
    var confirmado = window.confirm(
      '¿Deseas volver al inicio? Se cerrará tu sesión y tendrás que volver a acceder.'
    );
    if (!confirmado) return;
    sessionStorage.clear();
    window.location.href = '../../index.html';
  }

  UI.Tema = { aplicar: aplicarTema, definir: definirTema };
  UI.Sidebar = { alternar: alternarSidebar, aplicar: aplicarSidebar };
  UI.SalirAlInicio = salirAlInicio;

  // ---- Navegación entre vistas con persistencia --------------------------------
  // Al refrescar (F5) se restaura el MISMO panel donde estaba el usuario, en
  // vez de volver al primero. Funciona en los 3 roles:
  // - Prioridad: hash de la URL (#empresas) > localStorage por página.
  //   El hash permite enlaces directos y que atrás/adelante funcionen.
  // - `alMostrar(destino)` es el gancho de cada dashboard para sus cargas
  //   diferidas (empresas, alertas, reportes, colombia...); se llama tanto al
  //   hacer clic como al restaurar tras refrescar.
  var CLAVE_VISTA = 'ti_vista';

  function claveVista() {
    // Una clave por página (inversionista/analista/administrador) para que
    // cada rol recuerde su propio último panel.
    var pagina = 'comun';
    try {
      var ruta = window.location && window.location.pathname || '';
      var base = ruta.split('/').pop().split('?')[0].split('#')[0] || 'comun';
      pagina = base.replace(/\.html?$/i, '') || 'comun';
    } catch (e) { /* sin location (tests): clave genérica */ }
    return CLAVE_VISTA + ':' + pagina;
  }

  function hashVista() {
    try {
      var h = (window.location.hash || '').replace(/^#/, '').trim();
      return h;
    } catch (e) { return ''; }
  }

  function enlacesNav() {
    return Array.prototype.slice.call(
      document.querySelectorAll('.sidebar__link[data-vista]'));
  }

  function vistasNav() {
    return Array.prototype.slice.call(
      document.querySelectorAll('.vista[data-vista-panel]'));
  }

  function existeVista(nombre, enlaces) {
    return enlaces.some(function (a) { return a.getAttribute('data-vista') === nombre; });
  }

  function activarVista(destino, enlaces, vistas) {
    enlaces = enlaces || enlacesNav();
    vistas = vistas || vistasNav();
    if (!enlaces.length || !existeVista(destino, enlaces)) return false;
    enlaces.forEach(function (e) {
      e.classList.toggle('sidebar__link--activo', e.getAttribute('data-vista') === destino);
    });
    vistas.forEach(function (v) {
      v.classList.toggle('vista--activa', v.getAttribute('data-vista-panel') === destino);
    });
    try { localStorage.setItem(claveVista(), destino); } catch (e) { /* privado */ }
    return true;
  }

  function vistaGuardada(enlaces) {
    var h = hashVista();
    if (h && existeVista(h, enlaces)) return h;
    var g = null;
    try { g = localStorage.getItem(claveVista()); } catch (e) { g = null; }
    if (g && existeVista(g, enlaces)) return g;
    return null;
  }

  // Cablea clics + hashchange y restaura el panel guardado. Devuelve la
  // función `mostrar(destino)` para que cada dashboard enganche sus cargas.
  UI.Navegacion = {
    inicializar: function (alMostrar) {
      var enlaces = enlacesNav();
      var vistas = vistasNav();
      if (!enlaces.length) return function () { return false; };

      function mostrar(destino, actualizarHash) {
        if (!activarVista(destino, enlaces, vistas)) return false;
        if (actualizarHash !== false) {
          try {
            if (hashVista() !== destino) window.location.hash = destino;
          } catch (e) { /* tests sin location */ }
        }
        if (typeof alMostrar === 'function') alMostrar(destino);
        return true;
      }

      enlaces.forEach(function (enlace) {
        enlace.addEventListener('click', function () {
          mostrar(enlace.getAttribute('data-vista'));
        });
      });

      window.addEventListener('hashchange', function () {
        var h = hashVista();
        if (h) mostrar(h, false);
      });

      // Restauración al cargar/refrescar: hash > guardada > primera.
      var inicial = vistaGuardada(enlaces) || enlaces[0].getAttribute('data-vista');
      mostrar(inicial);
      return mostrar;
    }
  };

  // ---- Iniciales del sidebar plegado ----------------------------------------
  // En estado colapsado cada enlace se vuelve un círculo con su inicial.
  // Se asigna data-inicial (1 letra; 2 si la inicial está repetida, p. ej.
  // en admin "Colombia" -> "Co") y title con el nombre para el tooltip.
  function asignarIniciales() {
    var enlaces = document.querySelectorAll(".sidebar nav a.sidebar__link");
    if (!enlaces.length) return;
    var usadas = {};
    enlaces.forEach(function (a) {
      var nombre = (a.textContent || "").trim();
      if (!nombre) return;
      a.setAttribute("title", nombre);
      var base = nombre.charAt(0).toUpperCase();
      var ini = usadas[base] ? (nombre.slice(0, 2).toUpperCase()) : base;
      if (usadas[ini]) ini = nombre.slice(0, 2).toUpperCase();
      var i = 2;
      while (usadas[ini] && i < nombre.length) { i += 1; ini = nombre.slice(0, i).toUpperCase(); }
      usadas[ini] = true;
      a.setAttribute("data-inicial", ini);
    });
  }

  // ---- Arranque -------------------------------------------------------------
  function montar() {
    aplicarTema();
    aplicarSidebar();
    asignarIniciales();

    var btnSidebar = document.getElementById('btn-toggle-sidebar');
    if (btnSidebar) btnSidebar.addEventListener('click', alternarSidebar);

    // En móvil, al elegir un enlace del menú el drawer se cierra solo.
    document.querySelectorAll('.sidebar nav a').forEach(function (a) {
      a.addEventListener('click', function () {
        if (esMovil()) drawerMovil(false);
      });
    });

    var btnSalir = document.getElementById('btn-salir-inicio');
    if (btnSalir) btnSalir.addEventListener('click', salirAlInicio);

    // Selector de tema dentro de la vista Configuración (si existe).
    document.querySelectorAll('[data-tema-btn]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        definirTema(btn.getAttribute('data-tema-btn'));
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', montar);
  } else {
    montar();
  }
})();


