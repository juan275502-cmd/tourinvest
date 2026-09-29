// URL canonica de la API: www.tourinvest.com (mapeado a 127.0.0.1 en el
// archivo de hosts del sistema). Ver STARTUP.md -> "Convencion de hostname".
const API_BASE_URL = "http://www.tourinvest.com:8080";

// Alias corto al modulo de graficas SVG (js/graficas.js).
const Graficas = window.TourInvestUI.Graficas;

let empresasCache = [];

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

function formatearMoneda(valor) {
  return new Intl.NumberFormat("es-CO", { style: "currency", currency: "USD", minimumFractionDigits: 2 }).format(valor);
}

function formatearPorcentaje(valor) {
  const signo = valor >= 0 ? "+" : "";
  return `${signo}${Number(valor).toFixed(2)}%`;
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

      if (destino === "empresas" && empresasCache.length === 0) cargarEmpresas();
      if (destino === "reportes") cargarReportes();
    });
  });
}

// ---------- Vista: Empresas ----------

async function cargarEmpresas() {
  const cuerpoTabla = document.getElementById("tabla-empresas-cuerpo");
  const selectorEmpresa = document.getElementById("selector-empresa-reporte");

  const respuesta = await llamarApi("/empresas");
  if (!respuesta) return;

  if (!respuesta.ok) {
    cuerpoTabla.innerHTML = `<tr><td colspan="4" class="estado-vacio">No fue posible cargar las empresas.</td></tr>`;
    return;
  }

  empresasCache = await respuesta.json();

  if (empresasCache.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="4" class="estado-vacio">No hay empresas registradas todavía.</td></tr>`;
    return;
  }

  cuerpoTabla.innerHTML = empresasCache
    .map((empresa) => {
      const variacion = empresa.variacion ?? 0;
      const claseVariacion = variacion >= 0 ? "positiva" : "negativa";
      return `
        <tr>
          <td><span class="celda-simbolo">${empresa.simbolo}</span> · ${empresa.nombre}</td>
          <td>${empresa.sector}</td>
          <td class="celda-numero">${empresa.precioActual != null ? formatearMoneda(empresa.precioActual) : "—"}</td>
          <td class="celda-numero etiqueta-variacion etiqueta-variacion--${claseVariacion}">${formatearPorcentaje(variacion)}</td>
        </tr>`;
    })
    .join("");

  // Poblar el selector de empresa del formulario de crear reporte
  selectorEmpresa.innerHTML = empresasCache
    .map((empresa) => `<option value="${empresa.idEmpresa}">${empresa.simbolo} · ${empresa.nombre}</option>`)
    .join("");

  // Gráfica: variación porcentual de cada empresa (verde/rojo según signo).
  Graficas.barras("grafica-variacion-empresas",
    empresasCache.map((empresa) => ({
      etiqueta: empresa.simbolo || empresa.nombre,
      valor: Number(empresa.variacion) || 0,
      valorTexto: formatearPorcentaje(Number(empresa.variacion) || 0),
    })),
    {
      titulo: "Variación de las empresas analizadas",
      colorearPorSigno: true,
      vacio: "No hay empresas para graficar.",
      leyenda: "<span class='positivo'>Sube</span><span class='negativo'>Baja</span>",
    });
}

// ---------- Vista: Indicadores ----------

async function calcularLiquidez(evento) {
  evento.preventDefault();
  const formulario = evento.target;
  const resultadoEl = document.getElementById("resultado-liquidez");

  const activoCorriente = Number(formulario.activoCorriente.value);
  const pasivoCorriente = Number(formulario.pasivoCorriente.value);

  resultadoEl.className = "mensaje-global";

  const respuesta = await llamarApi("/analista/indicadores/liquidez", {
    method: "POST",
    body: JSON.stringify({ activoCorriente, pasivoCorriente }),
  });

  if (!respuesta) return;

  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => ({}));
    resultadoEl.textContent = cuerpo.mensaje || "No fue posible calcular el indicador.";
    resultadoEl.className = "mensaje-global mensaje-global--visible mensaje-global--error";
    return;
  }

  const datos = await respuesta.json();
  resultadoEl.textContent = `Liquidez corriente: ${Number(datos.liquidezCorriente).toFixed(2)}`;
  resultadoEl.className = "mensaje-global mensaje-global--visible mensaje-global--exito";

  // Gráfica: reparto del activo frente al pasivo corriente.
  Graficas.barraApilada("grafica-liquidez", activoCorriente, pasivoCorriente, {
    titulo: "Composición del capital corriente",
  });
}

// ---------- Vista: Reportes ----------

async function cargarReportes() {
  const cuerpoTabla = document.getElementById("tabla-reportes-cuerpo");
  const respuesta = await llamarApi("/analista/reportes");
  if (!respuesta) return;

  if (!respuesta.ok) {
    cuerpoTabla.innerHTML = `<tr><td colspan="4" class="estado-vacio">No fue posible cargar los reportes.</td></tr>`;
    return;
  }

  const reportes = await respuesta.json();

  if (reportes.length === 0) {
    cuerpoTabla.innerHTML = `<tr><td colspan="4" class="estado-vacio">Aún no has creado reportes.</td></tr>`;
    return;
  }

  cuerpoTabla.innerHTML = reportes
    .map(
      (reporte) => `
        <tr>
          <td>${reporte.titulo}</td>
          <td>${reporte.nombreEmpresa}</td>
          <td>${reporte.autor}</td>
          <td class="celda-numero">${new Date(reporte.fechaGeneracion).toLocaleDateString("es-CO")}</td>
        </tr>`
    )
    .join("");
}

async function crearReporte(evento) {
  evento.preventDefault();
  const formulario = evento.target;
  const mensajeEl = document.getElementById("mensaje-crear-reporte");

  const cuerpo = {
    idEmpresa: Number(formulario.idEmpresa.value),
    titulo: formulario.titulo.value.trim(),
    descripcion: formulario.descripcion.value.trim(),
  };

  const respuesta = await llamarApi("/analista/reportes", {
    method: "POST",
    body: JSON.stringify(cuerpo),
  });

  if (!respuesta) return;

  if (!respuesta.ok) {
    const datos = await respuesta.json().catch(() => ({}));
    mensajeEl.textContent = datos.mensaje || "No fue posible crear el reporte. Revisa los campos.";
    mensajeEl.className = "mensaje-global mensaje-global--visible mensaje-global--error";
    return;
  }

  mensajeEl.textContent = "Reporte creado correctamente.";
  mensajeEl.className = "mensaje-global mensaje-global--visible mensaje-global--exito";
  formulario.reset();
  cargarReportes();
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
  cargarEmpresas();
  cargarPerfil();
});