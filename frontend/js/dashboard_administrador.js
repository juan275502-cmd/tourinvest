// URL canonica de la API: www.tourinvest.com (mapeado a 127.0.0.1 en el
// archivo de hosts del sistema). Ver STARTUP.md -> "Convencion de hostname".
const API_BASE_URL = "http://www.tourinvest.com:8080";

function obtenerToken() {
  const token = sessionStorage.getItem("tourinvest_token");
  if (!token) {
    window.location.href = "login.html";
    return null;
  }
  return token;
}

async function llamarApi(ruta, opciones = {}) {
  const token = obtenerToken();
  if (!token) return null;

  const respuesta = await fetch(`${API_BASE_URL}${ruta}`, {
    ...opciones,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...(opciones.headers || {}),
    },
  });

  if (respuesta.status === 401) {
    sessionStorage.clear();
    window.location.href = "login.html";
    return null;
  }

  return respuesta;
}

// ---------- Navegación entre vistas ----------

function inicializarNavegacion() {
  const enlaces = document.querySelectorAll(".sidebar__link[data-vista]");
  const vistas = document.querySelectorAll(".vista[data-vista-panel]");

  enlaces.forEach((enlace) => {
    enlace.addEventListener("click", () => {
      const destino = enlace.dataset.vista;
      enlaces.forEach((e) => e.classList.toggle("sidebar__link--activo", e === enlace));
      vistas.forEach((v) => v.classList.toggle("vista--activa", v.dataset.vistaPanel === destino));

      if (destino === "empresas") cargarEmpresasAdmin();
    });
  });
}

// ---------- Vista: Usuarios ----------

let usuariosCache = [];

async function cargarUsuarios() {
  const cuerpoTabla = document.getElementById("tabla-usuarios-cuerpo");
  const respuesta = await llamarApi("/admin/usuarios");
  if (!respuesta) return;

  if (!respuesta.ok) {
    cuerpoTabla.innerHTML = `<tr><td colspan="5" class="estado-vacio">No fue posible cargar los usuarios.</td></tr>`;
    return;
  }

  usuariosCache = await respuesta.json();
  renderUsuarios();
}

// Render con filtro de búsqueda (mockup 6.8: "Buscar")
function renderUsuarios() {
  const cuerpoTabla = document.getElementById("tabla-usuarios-cuerpo");
  const entrada = document.getElementById("buscar-usuarios");
  const filtro = (entrada && entrada.value ? entrada.value : "").trim().toLowerCase();

  if (usuariosCache.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="5" class="estado-vacio">No hay usuarios registrados.</td></tr>`;
    return;
  }

  const visibles = filtro
    ? usuariosCache.filter(
        (u) =>
          (u.nombreCompleto || "").toLowerCase().includes(filtro) ||
          (u.correo || "").toLowerCase().includes(filtro)
      )
    : usuariosCache;

  if (visibles.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="5" class="estado-vacio">Sin resultados para tu búsqueda.</td></tr>`;
    return;
  }

  const correoPropio = sessionStorage.getItem("tourinvest_correo");

  cuerpoTabla.innerHTML = visibles
    .map((usuario) => {
      const esActivo = usuario.estado === "Activo";
      const esUsuarioPropio = usuario.correo === correoPropio;
      // Escapa comillas para poder inyectar el nombre en el onclick sin romperlo.
      const nombreSeguro = (usuario.nombreCompleto || "").replace(/'/g, "\\'");
      // Acciones disponibles segun el estado del usuario.
      const acciones = [
        `<button class="boton-fila" onclick="abrirModalEditarUsuario(${usuario.idUsuario})">Editar</button>`,
        esUsuarioPropio
          ? `<span style="color: var(--texto-secundario); font-size: 0.8rem;">Tu cuenta</span>`
          : esActivo
          ? `<button class="boton-fila boton-fila--cancelar" onclick="suspenderUsuario(${usuario.idUsuario})">Suspender</button>`
          : `<button class="boton-fila" onclick="activarUsuario(${usuario.idUsuario})">Activar</button>`,
        esUsuarioPropio
          ? ""
          : `<button class="boton-fila boton-fila--cancelar" onclick="eliminarUsuario(${usuario.idUsuario}, '${nombreSeguro}')">Eliminar</button>`,
      ].filter(Boolean).join(" ");

      return `
        <tr>
          <td>${usuario.nombreCompleto}</td>
          <td>${usuario.correo}</td>
          <td>${usuario.rol}</td>
          <td><span class="estado-pill estado-pill--${esActivo ? "activa" : "cancelada"}">${usuario.estado}</span></td>
          <td style="white-space: nowrap;">${acciones}</td>
        </tr>`;
    })
    .join("");
}

async function suspenderUsuario(idUsuario) {
  const respuesta = await llamarApi(`/admin/usuarios/${idUsuario}/suspender`, { method: "PATCH" });
  if (respuesta && respuesta.ok) cargarUsuarios();
  else if (respuesta) {
    const datos = await respuesta.json().catch(() => ({}));
    alert(datos.mensaje || "No fue posible suspender el usuario.");
  }
}

async function activarUsuario(idUsuario) {
  const respuesta = await llamarApi(`/admin/usuarios/${idUsuario}/activar`, { method: "PATCH" });
  if (respuesta && respuesta.ok) cargarUsuarios();
}

// ---------- CRUD de usuarios ----------
// La logica vive en js/usuarios.js (modulo compartido). Aqui solo se delegan
// los onclick de la tabla para mantener este archivo centrado en la navegacion.

const U = () => window.TourInvestUI.Usuarios;

function abrirModalCrearUsuario() {
  U().abrir(null);
}

function abrirModalEditarUsuario(idUsuario) {
  U().abrir(usuariosCache.find((u) => u.idUsuario === idUsuario));
}

function cerrarModalUsuario() {
  U().cerrar();
}

function guardarUsuario(evento) {
  // El id viaja en data-id-usuario para saber si es alta o edicion.
  return U().guardar(evento);
}

function eliminarUsuario(idUsuario, nombre) {
  return U().eliminar(idUsuario, nombre);
}

// ---------- Vista: Empresas (Gestión de Empresas — CRUD, mockup 6.9) ----------

let empresasAdminCache = [];
let empresaEditandoId = null;

function formatearMoneda(valor) {
  return new Intl.NumberFormat("es-CO", { style: "currency", currency: "USD", minimumFractionDigits: 2 }).format(valor);
}

function formatearPorcentaje(valor) {
  const signo = valor >= 0 ? "+" : "";
  return `${signo}${Number(valor).toFixed(2)}%`;
}

async function cargarEmpresasAdmin() {
  const cuerpoTabla = document.getElementById("tabla-empresas-admin-cuerpo");
  const respuesta = await llamarApi("/empresas");
  if (!respuesta) return;

  if (!respuesta.ok) {
    cuerpoTabla.innerHTML = `<tr><td colspan="7" class="estado-vacio">No fue posible cargar las empresas.</td></tr>`;
    return;
  }

  empresasAdminCache = await respuesta.json();

  if (empresasAdminCache.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="7" class="estado-vacio">No hay empresas registradas. Crea la primera con el botón «+ Crear empresa».</td></tr>`;
    return;
  }

  cuerpoTabla.innerHTML = empresasAdminCache
    .map((empresa) => {
      const variacion = empresa.variacion ?? 0;
      const claseVariacion = variacion >= 0 ? "positiva" : "negativa";
      const nombreSeguro = (empresa.nombre || "").replace(/'/g, "\\'");
      return `
        <tr>
          <td><span class="celda-simbolo">${empresa.simbolo}</span></td>
          <td>${empresa.nombre}</td>
          <td>${empresa.sector}</td>
          <td>${empresa.pais ?? "—"}</td>
          <td class="celda-numero">${empresa.precioActual != null ? formatearMoneda(empresa.precioActual) : "—"}</td>
          <td class="celda-numero etiqueta-variacion etiqueta-variacion--${claseVariacion}">${formatearPorcentaje(variacion)}</td>
          <td style="white-space: nowrap;">
            <button class="boton-fila" onclick="abrirModalEditarEmpresa(${empresa.idEmpresa})">Editar</button>
            <button class="boton-fila boton-fila--cancelar" onclick="eliminarEmpresa(${empresa.idEmpresa}, '${nombreSeguro}')">Eliminar</button>
          </td>
        </tr>`;
    })
    .join("");
}

function abrirModalCrearEmpresa() {
  empresaEditandoId = null;
  const formulario = document.getElementById("form-empresa");
  formulario.reset();
  document.getElementById("modal-empresa-titulo").textContent = "Crear empresa";
  ocultarMensajeEmpresa();
  abrirModalEmpresaAdmin();
}

async function abrirModalEditarEmpresa(idEmpresa) {
  const respuesta = await llamarApi(`/empresas/${idEmpresa}`);
  if (!respuesta || !respuesta.ok) return;

  const detalle = await respuesta.json();
  empresaEditandoId = idEmpresa;

  const formulario = document.getElementById("form-empresa");
  formulario.nombre.value = detalle.nombre;
  formulario.simbolo.value = detalle.simbolo;
  formulario.sector.value = detalle.sector;
  formulario.pais.value = detalle.pais;
  formulario.precio.value = detalle.precio;

  document.getElementById("modal-empresa-titulo").textContent = `Editar empresa: ${detalle.nombre}`;
  ocultarMensajeEmpresa();
  abrirModalEmpresaAdmin();
}

function abrirModalEmpresaAdmin() {
  const modal = document.getElementById("modal-empresa-admin");
  modal.classList.add("modal--open");
  modal.setAttribute("aria-hidden", "false");
}

function cerrarModalEmpresaAdmin() {
  const modal = document.getElementById("modal-empresa-admin");
  modal.classList.remove("modal--open");
  modal.setAttribute("aria-hidden", "true");
}

function mostrarMensajeEmpresa(texto, tipo) {
  const elemento = document.getElementById("mensaje-empresa");
  elemento.textContent = texto;
  elemento.className = `mensaje-global mensaje-global--visible mensaje-global--${tipo}`;
}

function ocultarMensajeEmpresa() {
  const elemento = document.getElementById("mensaje-empresa");
  elemento.className = "mensaje-global";
}

async function guardarEmpresa(evento) {
  evento.preventDefault();
  const formulario = evento.target;

  const cuerpo = {
    nombre: formulario.nombre.value.trim(),
    simbolo: formulario.simbolo.value.trim().toUpperCase(),
    sector: formulario.sector.value.trim(),
    pais: formulario.pais.value.trim(),
    precio: Number(formulario.precio.value),
  };

  const esEdicion = empresaEditandoId != null;
  const respuesta = await llamarApi(esEdicion ? `/empresas/${empresaEditandoId}` : "/empresas", {
    method: esEdicion ? "PUT" : "POST",
    body: JSON.stringify(cuerpo),
  });

  if (!respuesta) return;

  if (!respuesta.ok) {
    const datos = await respuesta.json().catch(() => ({}));
    const mensajeError = datos.mensaje
      || (datos.errores ? Object.values(datos.errores)[0] : "")
      || "No fue posible guardar la empresa.";
    mostrarMensajeEmpresa(mensajeError, "error");
    return;
  }

  cerrarModalEmpresaAdmin();
  window.TourInvestUI.toast(esEdicion ? "Empresa actualizada correctamente." : "Empresa creada correctamente.", "success");
  cargarEmpresasAdmin();
}

async function eliminarEmpresa(idEmpresa, nombre) {
  if (!confirm(`¿Eliminar la empresa "${nombre}"? Esta acción no se puede deshacer.`)) return;

  const respuesta = await llamarApi(`/empresas/${idEmpresa}`, { method: "DELETE" });
  if (!respuesta) return;

  if (!respuesta.ok) {
    const datos = await respuesta.json().catch(() => ({}));
    alert(datos.mensaje || "No fue posible eliminar la empresa.");
    return;
  }

  window.TourInvestUI.toast("Empresa eliminada correctamente.", "success");
  cargarEmpresasAdmin();
}

// ---------- Perfil / cierre de sesión ----------

// El perfil ahora se pide a la API (GET /perfil) en vez de solo leer sessionStorage:
// asi los datos editados se reflejan siempre, y cada rol ve SU propio perfil.
function cargarPerfil() {
  if (window.TourInvestUI && window.TourInvestUI.Perfil) {
    window.TourInvestUI.Perfil.cargar();
  }
}

function cerrarSesion() {
  sessionStorage.clear();
  window.location.href = "login.html";
}

// ---------- Arranque ----------

document.addEventListener("DOMContentLoaded", () => {
  if (!obtenerToken()) return;

  document.getElementById("nombre-usuario-topbar").textContent = sessionStorage.getItem("tourinvest_nombre") || "";
  document.getElementById("rol-usuario-topbar").textContent = sessionStorage.getItem("tourinvest_rol") || "";

  inicializarNavegacion();
  cargarUsuarios();
  cargarPerfil();

  // Buscador de usuarios (mockup 6.8)
  document.getElementById("buscar-usuarios").addEventListener("input", renderUsuarios);

  // Modal de empresas: cierre por backdrop/ESC unificado (.modal--open)
  if (window.TourInvestUI && window.TourInvestUI.initModals) {
    window.TourInvestUI.initModals();
  }
});