// Pruebas de las validaciones de js/auth.js (registro, login y recuperación).
// Se ejecuta el archivo TAL CUAL en un contexto vm con un DOM mínimo, así que
// lo que se prueba es el código que llega al navegador, no una copia.
//
//   node frontend/tests/auth_validation_test.js
//
// Cubre los casos PV-01 a PV-17 del protocolo de pruebas.
const fs = require("fs");
const vm = require("vm");
const assert = require("assert");

// --- DOM mínimo -------------------------------------------------------------
function crearCampo(nombre, valor = "") {
  const mensaje = { textContent: "" };
  const campo = {
    mensaje,
    clase: "",
    value: valor,
    listeners: {},
    closest(selector) {
      assert.strictEqual(selector, ".field", "solo se usa .field como envoltorio");
      return this;
    },
    querySelector(selector) {
      return selector === ".field__mensaje-error" ? mensaje : null;
    },
    classList: {
      add(clase) {
        campo.clase = clase;
      },
      remove(clase) {
        if (campo.clase === clase) campo.clase = "";
      },
      toggle(clase, activo) {
        campo.clase = activo ? clase : "";
      },
    },
    addEventListener(evento, fn) {
      this.listeners[evento] = fn;
    },
  };
  campo.nombre = nombre;
  return campo;
}

function crearFormulario(campos) {
  const formulario = {
    campos,
    querySelector: (selector) =>
      campos[selector.replace(/^\[name='|'\]$/g, "")] || null,
    querySelectorAll(selector) {
      if (selector === ".field--error") {
        return Object.values(campos).filter((c) => c.clase === "field--error");
      }
      if (selector === ".field__mensaje-error") {
        return Object.values(campos).map((c) => c.mensaje);
      }
      return [];
    },
  };
  // En el navegador los controles son alcanzables como propiedades del form
  // (formulario.nombre1), que es exactamente lo que lee auth.js.
  Object.assign(formulario, campos);
  return formulario;
}

const ctx = {
  console,
  Date,
  JSON,
  document: { addEventListener: () => {}, getElementById: () => null },
  fetch: () => {
    throw new Error("las validaciones locales no deben llamar al backend");
  },
};
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname + "/../js/auth.js", "utf8"), ctx);

const validarContrasena = ctx.validarContrasena;
const validarCorreo = ctx.validarCorreo;
const validarNombre = ctx.validarNombre;
const validarCedula = ctx.validarCedula;
const validarFecha = ctx.validarFecha;
const contarCaracteres = ctx.contarCaracteres;

const requisitosContrasena = ctx.requisitosContrasena;
const validarFormularioRegistro = ctx.validarFormularioRegistro;
// Se copia con spread: el objeto viene de otro contexto de vm y deepStrictEqual
// compara también el prototipo, que allí es otro distinto.
const REGLAS = { ...ctx.TourInvestAuth.REGLAS_CONTRASENA };

const NOMBRE_VALIDO = {
  vacio: "El nombre es obligatorio.",
  etiqueta: "El nombre",
  maximo: 30,
  formato: "formato",
};
const COMPLETA = "TourInvest2026*!"; // 16 caracteres: cumple la politica
// --- La política es la pedida: 12 / 2 / 2 / 2 / 2 ---------------------------
console.log("Política de contraseña: 12 / 2 mayús / 2 mínús / 2 números / 2 especiales");
assert.deepStrictEqual(REGLAS, {
  longitudMinima: 12,
  longitudMaxima: 72,
  minimoMayusculas: 2,
  minimoMinusculas: 2,
  minimoNumeros: 2,
  minimoEspeciales: 2,
});

// --- PV-10 / PV-11 / PV-12: contraseña ---------------------------------------
console.log("PV-10 / PV-11 / PV-12  contraseña");
assert.strictEqual(validarContrasena("").mensaje, "La contraseña es obligatoria.");
assert.strictEqual(validarContrasena("    ").mensaje, "La contraseña no puede ser solo espacios.");

const corta = validarContrasena("Ab1$"); // 5 caracteres
assert.ok(!corta.valida, "PV-11: menos de 12 se rechaza");
assert.match(corta.mensaje, /12 caracteres/, "el mensaje dice el mínimo");

assert.strictEqual(COMPLETA.length, 16);
assert.ok(validarContrasena(COMPLETA).valida, "PV-12: 16 caracteres válidos");

// Frontera de la longitud: 11 caracteres que cumplen TODO lo demás.
const onceMenos = "AB12$cd34!x"; // 2 mayús, 2 díg., 2 especiales, pero 11 caracteres
const justo = "AB12$cd34!xy"; // lo mismo con 12
assert.strictEqual(onceMenos.length, 11);
assert.strictEqual(justo.length, 12);
assert.ok(!validarContrasena(onceMenos).valida, "11 caracteres se rechazan");
assert.ok(validarContrasena(justo).valida, "con 12 caracteres ya es válida");

// Cada regla por separado, comprobando el mensaje exacto.
const soloMinusculas = "abcdefghijkl";
assert.match(validarContrasena(soloMinusculas).mensaje, /2 letras mayúsculas/);
assert.match(validarContrasena(soloMinusculas).mensaje, /2 números/);
assert.match(validarContrasena(soloMinusculas).mensaje, /2 caracteres especiales/);
assert.strictEqual(
  validarContrasena(soloMinusculas).mensaje.split(";").length,
  3,
  "el mensaje enumera los 3 requisitos incumplidos"
);

assert.match(validarContrasena("ABCDEFGHIJ12").mensaje, /2 letras minúsculas/);
assert.match(validarContrasena("abcdefghij12").mensaje, /2 letras mayúsculas/);
assert.match(validarContrasena("Abcdefghijk!").mensaje, /2 números/);
assert.match(validarContrasena("Abcdefghij12").mensaje, /2 caracteres especiales/);

assert.ok(
  validarContrasena(justo).valida,
  "los mínimos son 2: 2 mayúsculas, 2 números y 2 especiales bastan"
);

// PV-13: más de 72 caracteres (límite de BCrypt).
assert.match(
  validarContrasena(COMPLETA + "a".repeat(70)).mensaje,
  /72 caracteres/
);

// Un espacio en blanco NO cuenta como carácter especial, y las tildes son
// letras (no especiales).
assert.ok(!validarContrasena("Ab cd efgh12").valida, "el espacio no es especial");
assert.strictEqual(contarCaracteres("  Aa1!  ").especiales, 1);
assert.strictEqual(contarCaracteres("ÁaÑñ").mayusculas, 2, "Á y Ñ son mayúsculas");
assert.strictEqual(contarCaracteres("ÁaÑñ").minusculas, 2);
assert.strictEqual(contarCaracteres("ÁaÑñ").especiales, 0, "las tildes no son especiales");

// Checklist en vivo: los ids deben coincidir con los data-regla de registro.html.
assert.strictEqual(
  requisitosContrasena(COMPLETA)
    .map((r) => r.id)
    .join(","),
  "longitud,mayusculas,minusculas,numeros,especiales"
);
assert.ok(requisitosContrasena(COMPLETA).every((r) => r.cumplido));

// --- PV-06 / PV-07 / PV-08: correo -------------------------------------------
console.log("PV-06 / PV-07 / PV-08  correo");
assert.ok(validarCorreo("vale.lor@tourinvest.com").valida, "PV-06: formato válido");
assert.ok(
  !validarCorreo("vale.lor@tourinvest").valida,
  "PV-07: sin extensión de dominio se rechaza"
);
assert.ok(!validarCorreo("vale.lor").valida, "PV-08: sin @ se rechaza");
// --- PV-03 / PV-04 / PV-05 / PV-13: nombre y apellido ------------------------
console.log("PV-03 / PV-04 / PV-05  nombre");
assert.ok(validarNombre("Juan", NOMBRE_VALIDO).valida);
assert.ok(
  validarNombre("María José", NOMBRE_VALIDO).valida,
  "PV-03: espacios entre palabras"
);
assert.ok(validarNombre("Núñez", NOMBRE_VALIDO).valida, "PV-04: eñe");
assert.ok(validarNombre("Ángel", NOMBRE_VALIDO).valida, "PV-04: A con tilde");
assert.ok(validarNombre("O'Brien-Smith", NOMBRE_VALIDO).valida, "apóstrofo y guion");
assert.ok(!validarNombre("Juan123", NOMBRE_VALIDO).valida, "PV-05: dígitos fuera");
assert.ok(!validarNombre("Juan@", NOMBRE_VALIDO).valida, "PV-05: símbolo fuera");
assert.ok(!validarNombre("<script>", NOMBRE_VALIDO).valida, "PV-05: no se admiten etiquetas");
assert.strictEqual(
  validarNombre("   ", NOMBRE_VALIDO).mensaje,
  "El nombre es obligatorio.",
  "PV-02"
);
assert.ok(
  validarNombre("EsteNombreEsDemasiadoLargoParaElCampo", NOMBRE_VALIDO).mensaje.includes("30"),
  "PV-13: avisa del tope de 30 caracteres"
);

// --- PV-14: cédula exclusivamente numérica -----------------------------------
console.log("PV-14  cédula");
// --- PV-16 / PV-17: fecha ----------------------------------------------------
console.log("PV-16 / PV-17  fecha");
assert.ok(validarFecha("1999-03-20").valida);
assert.ok(!validarFecha("20/03/1999").valida, "PV-16: formato incorrecto");
assert.ok(!validarFecha("1999-13-01").valida, "PV-17: mes inexistente");
assert.ok(!validarFecha("2023-02-30").valida, "PV-17: el 30 de febrero no existe");
assert.ok(!validarFecha("2999-01-01").valida, "PV-17: fecha futura");
assert.strictEqual(validarFecha("").mensaje, "La fecha de nacimiento es obligatoria.");
assert.match(validarCorreo("vale.lor").mensaje, /@/, "el mensaje pide el @");
assert.strictEqual(validarCorreo("   ").mensaje, "El correo es obligatorio.", "PV-02");
assert.ok(validarCorreo("nombre.apellido+tag@sub.dominio.co").valida);
assert.ok(!validarCorreo("espacio dentro@dominio.com").valida);
assert.ok(!validarCorreo("a@b").valida, "una sola letra no es una extensión válida");
// --- PV-01 / PV-02 / PV-03: formulario de registro completo ------------------
console.log("PV-01 / PV-02  formulario de registro");
function formularioRegistro(valores) {
  return crearFormulario({
    nombre1: crearCampo("nombre1", valores.nombre1),
    apellido1: crearCampo("apellido1", valores.apellido1),
    cedula: crearCampo("cedula", valores.cedula),
    fechaNacimiento: crearCampo("fechaNacimiento", valores.fechaNacimiento),
    correo: crearCampo("correo", valores.correo),
    contrasena: crearCampo("contrasena", valores.contrasena),
    confirmarContrasena: crearCampo("confirmarContrasena", valores.confirmarContrasena),
  });
}

const VALIDO = {
  nombre1: "María José",
  apellido1: "Núñez Pérez",
  cedula: "1009876543",
  fechaNacimiento: "1999-03-20",
  correo: "vale.lor@tourinvest.com",
  contrasena: COMPLETA,
  confirmarContrasena: COMPLETA,
};
assert.strictEqual(
  validarFormularioRegistro(formularioRegistro(VALIDO)),
  false,
  "PF-02: un registro válido pasa la validación"
);

// PV-01: todo vacío -> error en los 7 campos.
const vacio = formularioRegistro({});
assert.strictEqual(validarFormularioRegistro(vacio), true, "PV-01: todo vacío se rechaza");
for (const nombre of Object.keys(VALIDO)) {
  assert.ok(
    vacio.campos[nombre].mensaje.textContent.length > 0,
    `PV-01: ${nombre} debe mostrar su mensaje`
  );
  assert.strictEqual(vacio.campos[nombre].clase, "field--error", `PV-01: ${nombre} marcado`);
}

// PV-02: solo espacios.
const soloEspacios = formularioRegistro({ ...VALIDO, nombre1: "   ", correo: "   ", cedula: "  " });
assert.strictEqual(validarFormularioRegistro(soloEspacios), true, "PV-02: espacios rechazados");
assert.strictEqual(soloEspacios.campos.nombre1.mensaje.textContent, "El nombre es obligatorio.");
assert.strictEqual(soloEspacios.campos.correo.mensaje.textContent, "El correo es obligatorio.");

// PV-07 en el formulario completo.
const correoSinDominio = formularioRegistro({ ...VALIDO, correo: "vale.lor@tourinvest" });
assert.strictEqual(validarFormularioRegistro(correoSinDominio), true, "PV-07");
assert.match(correoSinDominio.campos.correo.mensaje.textContent, /@/);

// PV-11 en el formulario completo.
const claveCorta = formularioRegistro({ ...VALIDO, contrasena: "Ab1$", confirmarContrasena: "Ab1$" });
assert.strictEqual(validarFormularioRegistro(claveCorta), true, "PV-11");
assert.match(claveCorta.campos.contrasena.mensaje.textContent, /12 caracteres/);

// Confirmación distinta.
const noCoinciden = formularioRegistro({ ...VALIDO, confirmarContrasena: "OtraClave2026*" });
assert.strictEqual(validarFormularioRegistro(noCoinciden), true);
assert.strictEqual(
  noCoinciden.campos.confirmarContrasena.mensaje.textContent,
  "Las contraseñas no coinciden."
);

// PV-14 en el formulario completo.
const cedulaConLetras = formularioRegistro({ ...VALIDO, cedula: "1009a876543" });
assert.strictEqual(validarFormularioRegistro(cedulaConLetras), true, "PV-14");
assert.strictEqual(cedulaConLetras.campos.cedula.clase, "field--error");

// PV-16 / PV-17 en el formulario completo.
const fechaFutura = formularioRegistro({ ...VALIDO, fechaNacimiento: "2999-01-01" });
assert.strictEqual(validarFormularioRegistro(fechaFutura), true, "PV-17");

console.log("\nOK - todas las validaciones de auth.js se comportan como se espera");
console.log(`  política = ${JSON.stringify(REGLAS)}`);
assert.ok(validarCedula("1009876543").valida);
assert.ok(!validarCedula("1009a87654").valida, "PV-14: letras rechazadas");
assert.ok(!validarCedula("ABCDEF").valida);
assert.ok(!validarCedula("12345").valida, "menos de 6 dígitos");
assert.ok(!validarCedula("123456789012345678901").valida, "más de 20 dígitos");
assert.ok(validarCedula("1009876543").valida, "10 dígitos numéricos");
assert.strictEqual(validarCedula("  ").mensaje, "La cédula es obligatoria.", "PV-02");

