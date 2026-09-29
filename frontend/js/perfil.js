// -----------------------------------------------------------------------------
// TourInvestUI.Perfil — gestion del PERFIL PROPIO (compartido por los 3 roles).
//
// REGLA DE NEGOCIO: cada usuario edita UNICAMENTE su propio perfil. El backend
// no recibe ningun id en la URL: resuelve el usuario desde el token JWT, asi
// que es imposible consultar o editar el perfil de otra persona.
//
// Se expone en window.TourInvestUI.Perfil para que inversionista.html,
// analista.html y administrador.html reutilicen exactamente el mismo codigo.
// -----------------------------------------------------------------------------
window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Perfil = (function () {
  const API_PERFIL = "http://www.tourinvest.com:8080/perfil";

  function token() {
    return sessionStorage.getItem("tourinvest_token") || "";
  }

  async function api(ruta, opciones = {}) {
    const respuesta = await fetch(`${API_PERFIL}${ruta}`, {
      ...opciones,
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token()}`,
        ...(opciones.headers || {}),
      },
    });
    if (!respuesta.ok) {
      const datos = await respuesta.json().catch(() => ({}));
      const error = new Error(datos.mensaje || "No fue posible completar la operacion.");
      error.datos = datos;
      throw error;
    }
    return respuesta.json();
  }

  function texto(id, valor) {
    const el = document.getElementById(id);
    if (el) el.textContent = valor || "—";
  }

  // Pinta nombre, rol, correo, cedula y fecha de nacimiento en la vista Perfil.
  function pintar(datos) {
    texto("perfil-nombre", datos.nombreCompleto);
    texto("perfil-rol", datos.rol);
    texto("perfil-correo", datos.correo);
    texto("perfil-cedula", datos.cedula);
    texto("perfil-nacimiento", datos.fechaNacimiento);
    texto("perfil-registro", datos.fechaRegistro);

    // Refresca tambien el topbar y la session para que el cambio se vea al instante.
    sessionStorage.setItem("tourinvest_nombre", datos.nombreCompleto);
    sessionStorage.setItem("tourinvest_correo", datos.correo);
    sessionStorage.setItem("tourinvest_rol", datos.rol);
    texto("nombre-usuario-topbar", datos.nombreCompleto);
  }

  async function cargar() {
    try {
      pintar(await api(""));
    } catch (error) {
      // Sin API disponible mantenemos lo que haya en sessionStorage.
      texto("perfil-nombre", sessionStorage.getItem("tourinvest_nombre"));
      texto("perfil-rol", sessionStorage.getItem("tourinvest_rol"));
      texto("perfil-correo", sessionStorage.getItem("tourinvest_correo"));
    }
  }

  function abrirModal() {
    const modal = document.getElementById("modal-perfil");
    if (modal) modal.classList.add("modal--open");
    // Rellena el formulario con los datos actuales: asi el usuario VE sus
    // datos y solo cambia lo que necesita, en vez de reescribirlos a mano.
    rellenarFormulario().catch(() => {
      // Si la API falla, el modal sigue abierto para que pueda escribir.
    });
  }

  function cerrarModal() {
    const modal = document.getElementById("modal-perfil");
    if (modal) modal.classList.remove("modal--open");
  }

  function mensaje(textoMensaje, tipo) {
    const el = document.getElementById("mensaje-perfil");
    if (!el) return;
    el.textContent = textoMensaje;
    el.className = `mensaje-global mensaje-global--visible mensaje-global--${tipo}`;
  }

  /** Rellena el formulario con los datos actuales (para editar sobre ellos). */
  async function rellenarFormulario() {
    const datos = await api("");
    const f = document.getElementById("form-perfil");
    if (!f) return;
    // Se accede por los `id` del HTML. Ojo: los atributos `name` son
    // `nombre1`/`apellido1` (no `nombre`/`apellido`).
    f.querySelector("#perfil-form-nombre").value = datos.nombre1 || "";
    f.querySelector("#perfil-form-apellido").value = datos.apellido1 || "";
    f.querySelector("#perfil-form-cedula").value = datos.cedula || "";
    f.querySelector("#perfil-form-nacimiento").value = datos.fechaNacimiento || "";
    f.querySelector("#perfil-form-correo").value = datos.correo || "";
  }

  // Guarda los datos del formulario. El boton SIEMPRE se rehabilita.
  async function guardar(evento) {
    evento.preventDefault();
    const formulario = evento.target;
    const boton = formulario.querySelector("button[type='submit']");

    const cuerpo = {
      nombre1: formulario.querySelector("#perfil-form-nombre").value.trim(),
      apellido1: formulario.querySelector("#perfil-form-apellido").value.trim(),
      cedula: formulario.querySelector("#perfil-form-cedula").value.trim(),
      fechaNacimiento: formulario.querySelector("#perfil-form-nacimiento").value,
      correo: formulario.querySelector("#perfil-form-correo").value.trim(),
    };

    boton.disabled = true;
    boton.textContent = "Guardando...";
    try {
      pintar(await api("", { method: "PUT", body: JSON.stringify(cuerpo) }));
      mensaje("Perfil actualizado correctamente.", "exito");
    } catch (error) {
      const errores = error.datos && error.datos.errores;
      mensaje(errores ? Object.values(errores).join(" ") : error.message, "error");
    } finally {
      boton.disabled = false;
      boton.textContent = "Guardar cambios";
    }
  }

  async function cambiarContrasena(evento) {
    evento.preventDefault();
    const formulario = evento.target;
    const boton = formulario.querySelector("button[type='submit']");

    boton.disabled = true;
    boton.textContent = "Cambiando...";
    try {
      const respuesta = await api("/contrasena", {
        method: "PATCH",
        body: JSON.stringify({
          contrasenaActual: formulario.contrasenaActual.value,
          contrasenaNueva: formulario.contrasenaNueva.value,
        }),
      });
      formulario.reset();
      mensaje(respuesta.mensaje || "Contraseña actualizada.", "exito");
    } catch (error) {
      const errores = error.datos && error.datos.errores;
      mensaje(errores ? Object.values(errores).join(" ") : error.message, "error");
    } finally {
      boton.disabled = false;
      boton.textContent = "Cambiar contraseña";
    }
  }

  return { cargar, abrirModal, cerrarModal, rellenarFormulario, guardar, cambiarContrasena };
})();
