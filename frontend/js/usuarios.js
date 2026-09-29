// -----------------------------------------------------------------------------
// TourInvestUI.Usuarios — CRUD de usuarios del panel Administrador.
// Complementa a dashboard_administrador.js: aqui vive la logica de la tabla
// (crear/editar/eliminar) para que ese archivo se mantenga centrado en la
// navegacion y las demas vistas.
//
// NOTA: la tabla se renderiza en dashboard_administrador.js (renderUsuarios),
// que invoca las funciones de aqui mediante onclick.
// -----------------------------------------------------------------------------
window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Usuarios = (function () {
  const API_USUARIOS = "http://www.tourinvest.com:8080/admin/usuarios";

  function token() {
    return sessionStorage.getItem("tourinvest_token") || "";
  }

  async function api(ruta, opciones = {}) {
    const respuesta = await fetch(`${API_USUARIOS}${ruta}`, {
      ...opciones,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token()}`,
        ...(opciones.headers || {}),
      },
    });
    if (!respuesta.ok) {
      const datos = await respuesta.json().catch(() => ({}));
      const errores = datos.errores ? Object.values(datos.errores).join(" ") : "";
      const error = new Error(errores || datos.mensaje || "No fue posible completar la operacion.");
      error.datos = datos;
      throw error;
    }
    return respuesta.json();
  }

  function mensaje(texto, tipo) {
    const el = document.getElementById("mensaje-usuario");
    if (!el) return;
    el.textContent = texto;
    el.className = `mensaje-global mensaje-global--visible mensaje-global--${tipo}`;
  }

  function abrir(usuario) {
    const modal = document.getElementById("modal-usuario");
    const f = document.getElementById("form-usuario");
    if (!modal || !f) return;

    f.reset();
    const titulo = document.getElementById("modal-usuario-titulo");
    const mensajeEl = document.getElementById("mensaje-usuario");
    if (mensajeEl) mensajeEl.textContent = "";

    if (usuario) {
      // Modo edicion: se reparte el nombre completo en nombre y apellido.
      titulo.textContent = "Editar usuario";
      const partes = (usuario.nombreCompleto || "").trim().split(/\s+/);
      f.nombre1.value = partes[0] || "";
      f.apellido1.value = partes.slice(1).join(" ");
      f.correo.value = usuario.correo || "";
      f.rol.value = usuario.rol || "Inversionista";
      // Cedula y contrasena se dejan vacias: si no se mandan, no cambian.
      f.cedula.value = "";
      f.contrasena.value = "";
      // El id se guarda en el formulario: es lo que distingue alta de edicion
      // (y permite que el submit sepa si debe hacer PUT o POST).
      f.dataset.idUsuario = usuario.idUsuario;
    } else {
      titulo.textContent = "Crear usuario";
      delete f.dataset.idUsuario;
    }
    modal.classList.add("modal--open");
  }

  function cerrar() {
    const modal = document.getElementById("modal-usuario");
    if (modal) modal.classList.remove("modal--open");
  }

  async function guardar(evento) {
    evento.preventDefault();
    const f = evento.target;
    const boton = f.querySelector("button[type='submit']");
    const idUsuario = f.dataset.idUsuario;

    const cuerpo = {
      nombre1: f.nombre1.value.trim(),
      apellido1: f.apellido1.value.trim(),
      cedula: f.cedula.value.trim(),
      fechaNacimiento: f.fechaNacimiento.value,
      correo: f.correo.value.trim(),
      contrasena: f.contrasena.value,
      rol: f.rol.value,
    };

    // Al editar, los campos opcionales vacios se omiten para no borrarlos.
    if (idUsuario) {
      if (!cuerpo.cedula) delete cuerpo.cedula;
      if (!cuerpo.fechaNacimiento) delete cuerpo.fechaNacimiento;
      if (!cuerpo.contrasena) delete cuerpo.contrasena;
    }

    boton.disabled = true;
    boton.textContent = "Guardando...";
    try {
      const datos = await api(idUsuario ? `/${idUsuario}` : "", {
        method: idUsuario ? "PUT" : "POST",
        body: JSON.stringify(cuerpo),
      });
      mensaje(idUsuario ? "Usuario actualizado correctamente." : "Usuario creado correctamente.", "exito");
      if (typeof cargarUsuarios === "function") await cargarUsuarios();
      setTimeout(cerrar, 800);
    } catch (error) {
      mensaje(error.message, "error");
    } finally {
      boton.disabled = false;
      boton.textContent = "Guardar";
    }
  }

  async function eliminar(idUsuario, nombre) {
    if (!confirm(`¿Eliminar a ${nombre}?\n\nEsta acción no se puede deshacer. Si el usuario tiene portafolios, alertas o reportes, conviene suspendarlo en lugar de eliminarlo.`)) return;
    try {
      await api(`/${idUsuario}`, { method: "DELETE" });
      if (typeof cargarUsuarios === "function") await cargarUsuarios();
    } catch (error) {
      alert(error.message);
    }
  }

  return { abrir, cerrar, guardar, eliminar };
})();
