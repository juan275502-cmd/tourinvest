// ============================================================
// TourInvest — GA7-220501096-AA3-EV01
// Driver de la vista "Gestión de Usuarios" (usuarios.html).
// Consume el CRUD REST de UsuarioAdminController (/api/usuarios),
// implementado con Spring Boot + Spring Data JPA sobre MySQL.
// ============================================================

const API_BASE_URL = (window.TourInvestConfig || {}).apiUrl || "http://localhost:8080";
const ETIQUETA_ROL = { 1: "Administrador", 2: "Analista", 3: "Inversionista" };

let usuariosCache = [];
let usuarioEditandoId = null;

// ---------- Sesión y transporte HTTP ----------

function obtenerToken() {
  const token = sessionStorage.getItem("tourinvest_token");
  if (!token) {
    window.location.href = "login.html";
    return null;
  }
  return token;
}

// Todas las llamadas llevan el token Bearer; un 401 (sesión vencida)
// limpia la sesión y devuelve al login.
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

// ---------- READ ----------

// Consulta todos los usuarios registrados para mostrarlos en la tabla.
async function cargarUsuarios() {
  const cuerpoTabla = document.getElementById("tabla-usuarios-cuerpo");
  const respuesta = await llamarApi("/api/usuarios");

  if (!respuesta) return;

  if (!respuesta.ok) {
    cuerpoTabla.innerHTML =
      `<tr><td colspan="5" class="estado-vacio">No fue posible cargar los usuarios. ¿Está corriendo el backend en :8080?</td></tr>`;
    return;
  }

  usuariosCache = await respuesta.json();
  renderUsuarios();
}

// Render de la tabla con filtro de búsqueda en cliente.
function renderUsuarios() {
  const cuerpoTabla = document.getElementById("tabla-usuarios-cuerpo");
  const entrada = document.getElementById("buscar-usuarios");
  const filtro = (entrada && entrada.value ? entrada.value : "").trim().toLowerCase();

  if (usuariosCache.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="5" class="estado-vacio">No hay usuarios registrados. Crea el primero con «+ Crear usuario».</td></tr>`;
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

  cuerpoTabla.innerHTML = visibles
    .map((usuario) => {
      const esActivo = usuario.estado === "Activo";
      return `
        <tr>
          <td>${usuario.nombreCompleto}</td>
          <td>${usuario.correo}</td>
          <td>${usuario.rol || "—"}</td>
          <td><span class="estado-pill estado-pill--${esActivo ? "activa" : "cancelada"}">${usuario.estado || "—"}</span></td>
          <td style="white-space: nowrap;">
            <button class="boton-fila" onclick="abrirModalEditar(${usuario.idUsuario})">Editar</button>
            ${esActivo
              ? `<button class="boton-fila boton-fila--cancelar" onclick="suspenderUsuario(${usuario.idUsuario})">Suspender</button>`
              : `<button class="boton-fila" onclick="activarUsuario(${usuario.idUsuario})">Activar</button>`}
            <button class="boton-fila boton-fila--cancelar" onclick="eliminarUsuario(${usuario.idUsuario}, '${(usuario.nombreCompleto || "").replace(/'/g, "\\'")}')">Eliminar</button>
          </td>
        </tr>`;
    })
    .join("");
}

// ---------- CREATE / UPDATE (modal) ----------

// Abre el formulario vacío para registrar un usuario nuevo.
function abrirModalCrear() {
  usuarioEditandoId = null;
  const formulario = document.getElementById("form-usuario");
  formulario.reset();
  formulario.idRol.value = "3"; // por defecto Inversionista
  // El estado no aplica en creación: el backend siempre registra Activo.
  formulario.estado.value = "";
  formulario.estado.disabled = true;
  document.getElementById("modal-usuario-titulo").textContent = "Crear usuario";
  ocultarMensajeModal();
  abrirModalUsuario();
}

// Consulta el usuario (GET /{id}) y llena el formulario para editarlo.
async function abrirModalEditar(idUsuario) {
  const respuesta = await llamarApi(`/api/usuarios/${idUsuario}`);
  if (!respuesta || !respuesta.ok) {
    window.TourInvestUI.toast("No fue posible consultar el usuario.", "error");
    return;
  }

  const usuario = await respuesta.json();
  usuarioEditandoId = idUsuario;

  const formulario = document.getElementById("form-usuario");
  formulario.nombre1.value = (usuario.nombreCompleto || "").split(" ")[0] || "";
  formulario.apellido1.value = (usuario.nombreCompleto || "").split(" ").slice(1).join(" ");
  formulario.correo.value = usuario.correo || "";
  formulario.contrasena.value = ""; // vacío = conservar la contraseña actual
  formulario.estado.value = usuario.estado || "";
  formulario.estado.disabled = false; // el estado sí es editable en edición
  const parRol = Object.entries(ETIQUETA_ROL).find(([, nombre]) => nombre === usuario.rol);
  formulario.idRol.value = parRol ? parRol[0] : "3";

  document.getElementById("modal-usuario-titulo").textContent =
    `Editar usuario: ${usuario.nombreCompleto}`;
  ocultarMensajeModal();
  abrirModalUsuario();
}

function abrirModalUsuario() {
  const modal = document.getElementById("modal-usuario");
  modal.classList.add("modal--open");
  modal.setAttribute("aria-hidden", "false");
}

function cerrarModalUsuario() {
  const modal = document.getElementById("modal-usuario");
  modal.classList.remove("modal--open");
  modal.setAttribute("aria-hidden", "true");
}

// Envía POST (crear) o PUT (editar) según el contexto del modal.
async function guardarUsuario(evento) {
  evento.preventDefault();
  const formulario = evento.target;
  ocultarMensajeModal();

  // Validación cliente: en creación la contraseña es obligatoria; en edición
  // puede quedar vacía para conservar la actual (lo indica el placeholder).
  if (usuarioEditandoId == null && formulario.contrasena.value.length < 6) {
    mostrarMensajeModal("La contraseña es obligatoria (mínimo 6 caracteres) al crear un usuario.", "error");
    return false;
  }

  const cuerpo = {
    nombre1: formulario.nombre1.value.trim(),
    apellido1: formulario.apellido1.value.trim(),
    cedula: formulario.cedula.value.trim(),
    fechaNacimiento: formulario.fechaNacimiento.value,
    correo: formulario.correo.value.trim(),
    contrasena: formulario.contrasena.value,
    idRol: Number(formulario.idRol.value),
  };
  // El estado solo se envía en edición (en creación el backend fija Activo).
  if (usuarioEditandoId != null) {
    cuerpo.estado = formulario.estado.value;
  }

  const esEdicion = usuarioEditandoId != null;
  const respuesta = await llamarApi(
    esEdicion ? `/api/usuarios/${usuarioEditandoId}` : "/api/usuarios",
    { method: esEdicion ? "PUT" : "POST", body: JSON.stringify(cuerpo) }
  );

  if (!respuesta) return false;

  const datos = await respuesta.json().catch(() => ({}));

  if (!respuesta.ok) {
    mostrarMensajeModal(extraerMensajeError(datos, respuesta.status), "error");
    return false;
  }

  cerrarModalUsuario();
  window.TourInvestUI.toast(datos.mensaje || "Usuario guardado.", "success");
  cargarUsuarios();
  return false;
}

// Traduce los códigos HTTP del backend a mensajes comprensibles.
function extraerMensajeError(datos, codigoEstado) {
  if (datos && datos.errores) {
    return "Revisa el formulario: " + Object.values(datos.errores).join(" ");
  }
  if (datos && datos.mensaje) {
    return datos.mensaje;
  }
  if (codigoEstado === 409) {
    return "Conflicto: el correo/cédula ya existe o hay registros asociados.";
  }
  if (codigoEstado === 400) {
    return "Datos inválidos. Revisa el formulario.";
  }
  return "No fue posible guardar el usuario.";
}

// ---------- DELETE ----------

// Elimina el usuario tras confirmación; el backend bloquea auto-eliminación
// y borrados con registros asociados (409).
async function eliminarUsuario(idUsuario, nombre) {
  if (!confirm(`¿Eliminar el usuario "${nombre}"? Esta acción no se puede deshacer.`)) return;

  const respuesta = await llamarApi(`/api/usuarios/${idUsuario}`, { method: "DELETE" });
  if (!respuesta) return;

  const datos = await respuesta.json().catch(() => ({}));
  if (!respuesta.ok) {
    window.TourInvestUI.toast(datos.mensaje || "No fue posible eliminar el usuario.", "error");
    return;
  }

  window.TourInvestUI.toast(datos.mensaje || "Usuario eliminado.", "success");
  cargarUsuarios();
}

// ---------- Estado (suspender / activar) ----------

async function suspenderUsuario(idUsuario) {
  const respuesta = await llamarApi(`/api/usuarios/${idUsuario}/suspender`, { method: "PATCH" });
  if (!respuesta) return;
  const datos = await respuesta.json().catch(() => ({}));
  if (respuesta.ok) {
    window.TourInvestUI.toast(datos.mensaje || "Usuario suspendido.", "success");
    cargarUsuarios();
  } else {
    window.TourInvestUI.toast(datos.mensaje || "No fue posible suspender.", "error");
  }
}

async function activarUsuario(idUsuario) {
  const respuesta = await llamarApi(`/api/usuarios/${idUsuario}/activar`, { method: "PATCH" });
  if (!respuesta) return;
  const datos = await respuesta.json().catch(() => ({}));
  if (respuesta.ok) {
    window.TourInvestUI.toast(datos.mensaje || "Usuario activado.", "success");
    cargarUsuarios();
  } else {
    window.TourInvestUI.toast(datos.mensaje || "No fue posible activar.", "error");
  }
}

// ---------- Mensajes y arranque ----------

function mostrarMensajeModal(texto, tipo) {
  const elemento = document.getElementById("mensaje-modal-usuario");
  elemento.textContent = texto;
  elemento.className = `mensaje-global mensaje-global--visible mensaje-global--${tipo}`;
}

function ocultarMensajeModal() {
  const elemento = document.getElementById("mensaje-modal-usuario");
  elemento.className = "mensaje-global";
}

function cerrarSesion() {
  sessionStorage.clear();
  window.location.href = "login.html";
}

document.addEventListener("DOMContentLoaded", () => {
  if (!obtenerToken()) return;

  document.getElementById("nombre-usuario-topbar").textContent =
    sessionStorage.getItem("tourinvest_nombre") || "";
  document.getElementById("rol-usuario-topbar").textContent =
    sessionStorage.getItem("tourinvest_rol") || "";

  cargarUsuarios();

  // El buscador filtra en cliente sin volver a llamar al backend.
  document.getElementById("buscar-usuarios").addEventListener("input", renderUsuarios);

  if (window.TourInvestUI && window.TourInvestUI.initModals) {
    window.TourInvestUI.initModals();
  }
});
