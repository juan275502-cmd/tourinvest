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
  // Sin esto el texto del error anterior se quedaba pegado bajo el campo
  // aunque el usuario ya lo hubiera corregido.
  formulario.querySelectorAll(".field__mensaje-error").forEach((mensaje) => {
    mensaje.textContent = "";
  });
}

// =============================================================================
// VALIDACIÓN DE CAMPOS
// -----------------------------------------------------------------------------
// Espejo en JavaScript de las reglas del backend
// (com.tourinvest.backend.validation: PasswordPolicy y Patrones). El backend
// sigue siendo la fuente de verdad —estas reglas evitan un viaje inútil al
// servidor y son las que pintan el mensaje bajo cada campo—, pero ambos lados
// aplican exactamente los mismos criterios.
// =============================================================================

const REGLAS_CONTRASENA = {
  longitudMinima: 12,
  longitudMaxima: 72, // límite de BCrypt
  minimoMayusculas: 2,
  minimoMinusculas: 2,
  minimoNumeros: 2,
  minimoEspeciales: 2,
};

// El type="email" del navegador acepta "vale.lor@tourinvest" (dominio sin
// punto), por eso aquí además se exige una extensión de dominio (PV-07).
const PATRON_CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
const PATRON_NOMBRE = /^[A-Za-zÁÉÍÓÚÜÖÑáéíóúüöñ]+(?:[ '\-]+[A-Za-zÁÉÍÓÚÜÖÑáéíóúüöñ]+)*$/;
const PATRON_CEDULA = /^[0-9]{6,20}$/;
const PATRON_FECHA = /^\d{4}-\d{2}-\d{2}$/;

const MSJ_NOMBRE =
  "Solo se permiten letras, espacios, guiones y apóstrofos (ej. María José).";
const MSJ_APELLIDO =
  "Solo se permiten letras, espacios, guiones y apóstrofos (ej. Pérez Gómez).";
const MSJ_CEDULA = "La cédula debe tener entre 6 y 20 dígitos numéricos.";
const MSJ_CORREO = "El correo no tiene un formato válido (ej. usuario@dominio.com).";

/**
 * Cuenta mayúsculas, minúsculas, números y especiales de una contraseña.
 * Se recorre con for...of y no por índice para no partir en dos los emojis o
 * los caracteres de otros alfabetos. Un espacio en blanco NUNCA cuenta como
 * especial.
 */
function contarCaracteres(valor) {
  const conteo = { mayusculas: 0, minusculas: 0, numeros: 0, especiales: 0 };

  for (const caracter of valor || "") {
    const enMayuscula = caracter.toUpperCase();
    const enMinuscula = caracter.toLowerCase();

    if (enMayuscula !== enMinuscula) {
      // Es una letra: vemos si viene en mayúscula o en minúscula.
      if (enMayuscula === caracter) {
        conteo.mayusculas++;
      } else {
        conteo.minusculas++;
      }
    } else if (caracter >= "0" && caracter <= "9") {
      conteo.numeros++;
    } else if (!/\s/.test(caracter)) {
      conteo.especiales++;
    }
  }

  return conteo;
}

/**
 * Requisitos de la contraseña con su estado. Alimenta la lista desplegable de
 * registro.html, que va marcando cada regla mientras el usuario escribe.
 */
function requisitosContrasena(valor) {
  const conteo = contarCaracteres(valor);
  const longitud = (valor || "").length;

  return [
    {
      id: "longitud",
      cumplido: longitud >= REGLAS_CONTRASENA.longitudMinima,
      texto: `Mínimo ${REGLAS_CONTRASENA.longitudMinima} caracteres`,
    },
    {
      id: "mayusculas",
      cumplido: conteo.mayusculas >= REGLAS_CONTRASENA.minimoMayusculas,
      texto: `Al menos ${REGLAS_CONTRASENA.minimoMayusculas} letras mayúsculas`,
    },
    {
      id: "minusculas",
      cumplido: conteo.minusculas >= REGLAS_CONTRASENA.minimoMinusculas,
      texto: `Al menos ${REGLAS_CONTRASENA.minimoMinusculas} letras minúsculas`,
    },
    {
      id: "numeros",
      cumplido: conteo.numeros >= REGLAS_CONTRASENA.minimoNumeros,
      texto: `Al menos ${REGLAS_CONTRASENA.minimoNumeros} números`,
    },
    {
      id: "especiales",
      cumplido: conteo.especiales >= REGLAS_CONTRASENA.minimoEspeciales,
      texto: `Al menos ${REGLAS_CONTRASENA.minimoEspeciales} caracteres especiales (!@#$%&*)`,
    },
  ];
}

/** Marca en el <ul> de reglas cuáles se van cumpliendo (feedback en vivo). */
function actualizarReglasContrasena(valor, lista) {
  if (!lista) return;

  requisitosContrasena(valor).forEach((regla) => {
    const item = lista.querySelector(`[data-regla="${regla.id}"]`);
    if (item) item.classList.toggle("regla-ok", regla.cumplido);
  });
}

/**
 * Valida la contraseña contra la política completa y devuelve TODOS los
 * requisitos incumplidos en un solo mensaje ("La contraseña debe cumplir:
 * mínimo 12 caracteres; al menos 2 números."), igual que el backend.
 */
function validarContrasena(valor) {
  const texto = valor || "";

  if (!texto) {
    return { valida: false, mensaje: "La contraseña es obligatoria." };
  }
  if (!texto.trim()) {
    return { valida: false, mensaje: "La contraseña no puede ser solo espacios." };
  }

  const pendientes = requisitosContrasena(texto)
    .filter((regla) => !regla.cumplido)
    .map((regla) => regla.texto.toLowerCase());

  if (texto.length > REGLAS_CONTRASENA.longitudMaxima) {
    pendientes.push(`máximo ${REGLAS_CONTRASENA.longitudMaxima} caracteres`);
  }

  if (pendientes.length === 0) {
    return { valida: true, mensaje: "" };
  }
  return { valida: false, mensaje: `La contraseña debe cumplir: ${pendientes.join("; ")}.` };
}

/**
 * Nombre o apellido: obligatorio, con tope de longitud, y solo con letras del
 * español separadas por espacios, guiones o apóstrofos. Acepta "María José"
 * (PV-03 y PV-04) y rechaza "Juan123" o "Juan@" (PV-05).
 */
function validarNombre(valor, opciones) {
  const texto = (valor || "").trim();

  if (!texto) {
    return { valida: false, mensaje: opciones.vacio };
  }
  if (texto.length > opciones.maximo) {
    return {
      valida: false,
      mensaje: `${opciones.etiqueta} no puede superar ${opciones.maximo} caracteres.`,
    };
  }
  if (!PATRON_NOMBRE.test(texto)) {
    return { valida: false, mensaje: opciones.formato };
  }
  return { valida: true, mensaje: "" };
}

/** Cédula: solo dígitos, entre 6 y 20 (rechaza letras, PV-14). */
function validarCedula(valor) {
  const texto = (valor || "").trim();

  if (!texto) {
    return { valida: false, mensaje: "La cédula es obligatoria." };
  }
  if (!PATRON_CEDULA.test(texto)) {
    return { valida: false, mensaje: MSJ_CEDULA };
  }
  return { valida: true, mensaje: "" };
}

/** Correo: exige "@" (PV-08) y extensión de dominio (PV-06 y PV-07). */
function validarCorreo(valor) {
  const texto = (valor || "").trim();

  if (!texto) {
    return { valida: false, mensaje: "El correo es obligatorio." };
  }
  if (!texto.includes("@")) {
    return {
      valida: false,
      mensaje: 'Incluye un signo "@" en la dirección de correo electrónico.',
    };
  }
  if (!PATRON_CORREO.test(texto)) {
    return { valida: false, mensaje: MSJ_CORREO };
  }
  return { valida: true, mensaje: "" };
}

/**
 * Fecha: formato AAAA-MM-DD (PV-16), fecha real del calendario y anterior a hoy
 * (PV-17). El campo date del navegador ya impide teclear otro formato, pero la
 * validación se mantiene aquí y en el backend porque la API es pública.
 */
function validarFecha(valor) {
  const texto = (valor || "").trim();

  if (!texto) {
    return { valida: false, mensaje: "La fecha de nacimiento es obligatoria." };
  }
  if (!PATRON_FECHA.test(texto)) {
    return { valida: false, mensaje: "La fecha debe tener el formato AAAA-MM-DD." };
  }

  const [anio, mes, dia] = texto.split("-").map(Number);
  const fecha = new Date(anio, mes - 1, dia);

  // new Date(2023, 1, 31) "roda" al 3 de marzo, así que se comparan las partes
  // para detectar fechas inexistentes como el 30 de febrero.
  if (
    fecha.getFullYear() !== anio ||
    fecha.getMonth() + 1 !== mes ||
    fecha.getDate() !== dia
  ) {
    return { valida: false, mensaje: "La fecha no existe en el calendario." };
  }
  if (fecha.getTime() >= Date.now()) {
    return {
      valida: false,
      mensaje: "La fecha de nacimiento debe ser anterior a hoy.",
    };
  }
  return { valida: true, mensaje: "" };
}

/**
 * Valida el formulario de REGISTRO completo. Devuelve `true` si hay algún
 * error: en ese caso el envío se corta aquí y no se llama a la API.
 */
function validarFormularioRegistro(formulario) {
  let hayErrores = false;

  const revisar = (campo, resultado) => {
    if (resultado.valida) return;
    if (campo) marcarCampoConError(campo.closest(".field"), resultado.mensaje);
    hayErrores = true;
  };

  revisar(
    formulario.nombre1,
    validarNombre(formulario.nombre1.value, {
      vacio: "El nombre es obligatorio.",
      etiqueta: "El nombre",
      maximo: 30,
      formato: MSJ_NOMBRE,
    })
  );
  revisar(
    formulario.apellido1,
    validarNombre(formulario.apellido1.value, {
      vacio: "El apellido es obligatorio.",
      etiqueta: "El apellido",
      maximo: 100,
      formato: MSJ_APELLIDO,
    })
  );
  revisar(formulario.cedula, validarCedula(formulario.cedula.value));
  revisar(
    formulario.fechaNacimiento,
    validarFecha(formulario.fechaNacimiento.value)
  );
  revisar(formulario.correo, validarCorreo(formulario.correo.value));

  const contrasena = formulario.contrasena.value;
  const confirmacion = formulario.confirmarContrasena.value;
  revisar(formulario.contrasena, validarContrasena(contrasena));
  revisar(formulario.confirmarContrasena, validarContrasena(confirmacion));

  if (contrasena !== confirmacion) {
    marcarCampoConError(
      formulario.confirmarContrasena.closest(".field"),
      "Las contraseñas no coinciden."
    );
    hayErrores = true;
  }

  return hayErrores;
}

/**
 * Valida login y recuperación: el correo con formato estricto y la contraseña
 * solo como obligatoria. La complejidad se exige al CREAR la contraseña, no
 * al usarla, para que las cuentas anteriores a esta regla puedan entrar.
 */
function validarFormularioAcceso(formulario) {
  const resultadoCorreo = validarCorreo(formulario.correo.value);

  if (!resultadoCorreo.valida) {
    marcarCampoConError(
      formulario.correo.closest(".field"),
      resultadoCorreo.mensaje
    );
    return true;
  }

  if (formulario.contrasena && !formulario.contrasena.value) {
    marcarCampoConError(
      formulario.contrasena.closest(".field"),
      "La contraseña es obligatoria."
    );
    return true;
  }

  return false;
}

/**
 * Enlaza el checklist de reglas de contraseña (registro.html) para que se vaya
 * marcando mientras se escribe, y avisa en vivo si las dos contraseñas
 * difieren. Es solo presentación: el bloqueo real ocurre al enviar.
 */
function activarAyudaContrasena() {
  const formulario = document.getElementById("registroForm");
  if (!formulario) return;

  const lista = document.getElementById("reglas-contrasena");
  const contrasena = formulario.querySelector("[name='contrasena']");
  const confirmacion = formulario.querySelector("[name='confirmarContrasena']");
  if (!contrasena) return;

  const refrescar = () => actualizarReglasContrasena(contrasena.value, lista);

  const revisarConfirmacion = () => {
    if (!confirmacion || !confirmacion.value) return;

    const campo = confirmacion.closest(".field");
    if (contrasena.value !== confirmacion.value) {
      marcarCampoConError(campo, "Las contraseñas no coinciden.");
    } else {
      campo.classList.remove("field--error");
      const mensaje = campo.querySelector(".field__mensaje-error");
      if (mensaje) mensaje.textContent = "";
    }
  };

  contrasena.addEventListener("input", refrescar);
  if (confirmacion) confirmacion.addEventListener("input", revisarConfirmacion);
  // Tras un registro correcto el formulario se vacía: el checklist vuelve a
  // su estado inicial para no mostrar reglas "cumplidas" sin contraseña.
  formulario.addEventListener("reset", () => actualizarReglasContrasena("", lista));
  refrescar();
}

// El script se carga con defer, así que el DOM ya está listo al ejecutarlo.
if (typeof document !== "undefined" && document.addEventListener) {
  document.addEventListener("DOMContentLoaded", activarAyudaContrasena);
}

// Se expone el módulo de validación en window (igual que js/graficas.js expone
// TourInvestUI) para que frontend/tests/auth_validation_test.js pueda ejecutar
// estas MISMAS funciones en Node y comprobar los casos PV-01 a PV-17.
if (typeof window !== "undefined") {
  window.TourInvestAuth = {
    REGLAS_CONTRASENA,
    contarCaracteres,
    requisitosContrasena,
    actualizarReglasContrasena,
    validarContrasena,
    validarNombre,
    validarCedula,
    validarCorreo,
    validarFecha,
    validarFormularioRegistro,
    validarFormularioAcceso,
  };
}

async function iniciarSesion(evento) {
  evento.preventDefault();

  const formulario = evento.target;
  const mensajeGlobal = document.getElementById("mensaje-global");
  const boton = formulario.querySelector("button[type='submit']");

  ocultarMensajeGlobal(mensajeGlobal);
  limpiarErroresDeCampos(formulario);

  // Validación en el navegador: si algo falla se marca el campo y NO se envía.
  if (validarFormularioAcceso(formulario)) {
    mostrarMensajeGlobal(mensajeGlobal, "Revisa los campos marcados.", "error");
    return;
  }

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

  // Solo se llama al backend si el correo tiene formato válido.
  if (validarFormularioAcceso(formulario)) {
    mostrarMensajeGlobal(mensajeGlobal, "Revisa el campo marcado.", "error");
    return;
  }

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
  const confirmacion = formulario.confirmarContrasena.value;
  if (contrasena !== confirmacion) {
    marcarCampoConError(
      formulario.confirmarContrasena.closest(".field"),
      "Las contraseñas no coinciden."
    );
  }

  // Validación completa en el navegador (PV-01 a PV-17): si algún campo falla
  // se marca con su mensaje y el formulario NO se envía.
  if (validarFormularioRegistro(formulario)) {
    mostrarMensajeGlobal(mensajeGlobal, "Revisa los campos marcados.", "error");
    return;
  }

  const datos = {
    nombre1: formulario.nombre1.value.trim(),
    apellido1: formulario.apellido1.value.trim(),
    cedula: formulario.cedula.value.trim(),
    fechaNacimiento: formulario.fechaNacimiento.value,
    correo: formulario.correo.value.trim(),
    contrasena,
    confirmarContrasena: confirmacion,
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
