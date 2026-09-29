// -----------------------------------------------------------------------------
// URL canonica de la API.
//
// El proyecto usa EXCLUSIVAMENTE `www.tourinvest.com` como nombre publico y
// `127.0.0.1` como direccion real. El dominio debe estar mapeado a 127.0.0.1
// en el archivo de hosts del sistema (ver STARTUP.md y `setup.sh check`).
//
// Si prefieres trabajar sobre la IP cruda, cambia esta constante por
// "http://127.0.0.1:8080" (y agrega ese origen a CORS_ALLOWED_ORIGINS).
// -----------------------------------------------------------------------------
const API_BASE_URL = "http://www.tourinvest.com:8080";

// El dashboard de destino según el rol que devuelve el backend en el login.
const RUTA_DASHBOARD = {
  Administrador: "administrador.html",
  Analista: "analista.html",
  Inversionista: "inversionista.html",
};

// Fallback correcto: antes apuntaba a "dashboard-inversionista.html", un archivo
// que no existe, asi que un rol desconocido llevaba a un 404 en blanco.
const DASHBOARD_POR_DEFECTO = "inversionista.html";

function mostrarMensajeGlobal(elemento, texto, tipo) {
  // Si el elemento no existe (pagina sin el contenedor), la UI se romperia y
  // el boton del formulario podria quedar bloqueado en "Verificando...".
  if (!elemento) {
    console.warn("[TourInvest] Falta el contenedor de mensajes:", texto);
    return;
  }
  elemento.textContent = texto;
  elemento.className = `mensaje-global mensaje-global--visible mensaje-global--${tipo}`;
}

function ocultarMensajeGlobal(elemento) {
  elemento.className = "mensaje-global";
}

function marcarCampoConError(campoWrapper, mensaje) {
  campoWrapper.classList.add("field--error");
  const mensajeEl = campoWrapper.querySelector(".field__mensaje-error");
  if (mensajeEl) mensajeEl.textContent = mensaje;
}

function limpiarErroresDeCampos(formulario) {
  formulario.querySelectorAll(".field--error").forEach((campo) => {
    campo.classList.remove("field--error");
  });
}

async function iniciarSesion(evento) {
  evento.preventDefault();

  const formulario = evento.target;
  const mensajeGlobal = document.getElementById("mensaje-global");
  const boton = formulario.querySelector("button[type='submit']");

  ocultarMensajeGlobal(mensajeGlobal);
  limpiarErroresDeCampos(formulario);

  const datos = {
    correo: formulario.correo.value.trim(),
    contrasena: formulario.contrasena.value,
  };

  boton.disabled = true;
  boton.textContent = "Verificando...";

  try {
    const respuesta = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(datos),
    });

    const cuerpo = await respuesta.json();

    if (!respuesta.ok) {
      mostrarMensajeGlobal(mensajeGlobal, cuerpo.mensaje || "Correo o contraseña incorrectos.", "error");
      return;
    }

    // Guardamos el token para que las siguientes pantallas (dashboards) lo usen en sus peticiones.
    sessionStorage.setItem("tourinvest_token", cuerpo.token);
    sessionStorage.setItem("tourinvest_nombre", cuerpo.nombre1);
    sessionStorage.setItem("tourinvest_correo", cuerpo.correo);
    sessionStorage.setItem("tourinvest_rol", cuerpo.rol);

    const destino = RUTA_DASHBOARD[cuerpo.rol] || DASHBOARD_POR_DEFECTO;
    window.location.href = destino;
  } catch (error) {
    mostrarMensajeGlobal(
      mensajeGlobal,
      "No fue posible conectar con el servidor. Verifica que el backend esté corriendo en www.tourinvest.com:8080.",
      "error"
    );
  } finally {
    // El boton SIEMPRE vuelve a su estado original. Sin este bloque el
    // formulario se quedaba en "Verificando..." para siempre cuando la
    // peticion fallaba, dejando al usuario sin poder reintentar.
    boton.disabled = false;
    boton.textContent = "Iniciar sesión";
  }
}

async function solicitarRecuperacion(evento) {
  evento.preventDefault();

  const formulario = evento.target;
  const mensajeGlobal = document.getElementById("mensaje-global");
  const boton = formulario.querySelector("button[type='submit']");

  ocultarMensajeGlobal(mensajeGlobal);
  limpiarErroresDeCampos(formulario);

  boton.disabled = true;
  boton.textContent = "Enviando...";

  try {
    const respuesta = await fetch(`${API_BASE_URL}/auth/recuperar`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ correo: formulario.correo.value.trim() }),
    });

    const cuerpo = await respuesta.json();

    if (!respuesta.ok) {
      mostrarMensajeGlobal(mensajeGlobal, cuerpo.mensaje || "No fue posible procesar la solicitud.", "error");
      return;
    }

    mostrarMensajeGlobal(mensajeGlobal, cuerpo.mensaje, "exito");
    formulario.reset();
  } catch (error) {
    mostrarMensajeGlobal(
      mensajeGlobal,
      "No fue posible conectar con el servidor. Verifica que el backend esté corriendo en www.tourinvest.com:8080.",
      "error"
    );
  } finally {
    boton.disabled = false;
    boton.textContent = "Enviar enlace";
  }
}

/**
 * Registrar usuario (rol público → siempre Inversionista).
 * Ver AuthController.registrar y RegistroRequest en el backend Java.
 */
async function registrarUsuario(evento) {
  evento.preventDefault();

  const formulario = evento.target;
  const mensajeGlobal = document.getElementById("mensaje-global");
  const boton = formulario.querySelector("button[type='submit']");

  ocultarMensajeGlobal(mensajeGlobal);
  limpiarErroresDeCampos(formulario);

  const contrasena = formulario.contrasena.value;
  const confirmarContrasena = formulario.confirmarContrasena.value;

  if (contrasena !== confirmarContrasena) {
    marcarCampoConError(
      formulario.confirmarContrasena.closest(".field"),
      "Las contraseñas no coinciden."
    );
    return;
  }

  const datos = {
    nombre1: formulario.nombre1.value.trim(),
    apellido1: formulario.apellido1.value.trim(),
    cedula: formulario.cedula.value.trim(),
    fechaNacimiento: formulario.fechaNacimiento.value,
    correo: formulario.correo.value.trim(),
    contrasena,
    confirmarContrasena,
  };

  boton.disabled = true;
  boton.textContent = "Creando cuenta...";

  try {
    const respuesta = await fetch(`${API_BASE_URL}/auth/registro`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(datos),
    });

    if (!respuesta.ok) {
      const cuerpo = await respuesta.json().catch(() => ({}));

      if (cuerpo.errores) {
        Object.entries(cuerpo.errores).forEach(([campo, mensaje]) => {
          const input = formulario.querySelector(`[name="${campo}"]`);
          if (input) marcarCampoConError(input.closest(".field"), mensaje);
        });
        mostrarMensajeGlobal(mensajeGlobal, "Revisa los campos marcados.", "error");
      } else {
        mostrarMensajeGlobal(mensajeGlobal, cuerpo.mensaje || "No fue posible completar el registro.", "error");
      }
      return;
    }

    mostrarMensajeGlobal(mensajeGlobal, "Cuenta creada correctamente. Ya puedes iniciar sesión.", "exito");
    formulario.reset();
    setTimeout(() => {
      window.location.href = "login.html";
    }, 1500);
  } catch (error) {
    mostrarMensajeGlobal(
      mensajeGlobal,
      "No fue posible conectar con el servidor. Verifica que el backend esté corriendo en www.tourinvest.com:8080.",
      "error"
    );
  } finally {
        boton.disabled = false;
    boton.textContent = "Registrar";
  }

  return false;
}
