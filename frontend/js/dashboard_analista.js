// URL canonica de la API: www.tourinvest.com (mapeado a 127.0.0.1 en el
// archivo de hosts del sistema). Ver STARTUP.md -> "Convencion de hostname".
const { obtenerToken, llamar: llamarApi, moneda: formatearMoneda,
  porcentaje: formatearPorcentaje, esc } = window.TourInvestUI.Api;

// Alias corto al modulo de graficas SVG (js/graficas.js).
const Graficas = window.TourInvestUI.Graficas;

let empresasCache = [];

// obtenerToken(), llamarApi(), formatearMoneda() y formatearPorcentaje() NO se
// redefinen aquí: ya vienen de window.TourInvestUI.Api (js/api.js) mediante el
// destructuring de arriba. Redeclar 'obtenerToken'/'llamarApi' con el mismo
// nombre era un SyntaxError que impedía cargar TODO el archivo (panel en blanco,
// sin usuarios y sin poder cerrar sesión).

// ---------- Navegación entre vistas ----------

function inicializarNavegacion() {
  // Persistencia del panel activo (hash + localStorage por rol): al refrescar
  // se restaura el mismo panel; `mostrar` dispara las cargas diferidas.
  window.TourInvestUI.Navegacion.inicializar((destino) => {
    if (destino === "empresas" && empresasCache.length === 0) cargarEmpresas();
    if (destino === "reportes") cargarReportes();
    if (destino === "colombia") cargarVistaColombia();
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
          <td><span class="celda-simbolo">${esc(empresa.simbolo)}</span> · ${esc(empresa.nombre)}</td>
          <td>${esc(empresa.sector)}</td>
          <td class="celda-numero">${empresa.precioActual != null ? formatearMoneda(empresa.precioActual) : "—"}</td>
          <td class="celda-numero etiqueta-variacion etiqueta-variacion--${claseVariacion}">${formatearPorcentaje(variacion)}</td>
        </tr>`;
    })
    .join("");

  // Poblar el selector de empresa del formulario de crear reporte
  selectorEmpresa.innerHTML = empresasCache
    .map((empresa) => `<option value="${empresa.idEmpresa}">${esc(empresa.simbolo)} · ${esc(empresa.nombre)}</option>`)
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
          <td>${esc(reporte.titulo)}</td>
          <td>${esc(reporte.nombreEmpresa)}</td>
          <td>${esc(reporte.autor)}</td>
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

// ---------- KPIs del panel (tarjetas superiores) ----------

// Los valores NO están fijos en el HTML: se calculan desde la API para que el
// panel refleje datos reales. Si algo falla, se muestra "—".
async function cargarKpis() {
  const elEmpresas = document.getElementById("kpi-empresas");
  const elReportes = document.getElementById("kpi-reportes");
  const elActivos = document.getElementById("kpi-activos");
  if (!elEmpresas) return;

  const [respEmpresas, respReportes] = await Promise.all([
    llamarApi("/empresas"),
    llamarApi("/analista/reportes"),
  ]);

  if (respEmpresas && respEmpresas.ok) {
    const empresas = await respEmpresas.json();
    elEmpresas.textContent = empresas.length;
  }

  if (respReportes && respReportes.ok) {
    const reportes = await respReportes.json();
    elReportes.textContent = reportes.length;

    // "Análisis en curso": reportes generados en los últimos 30 días.
    const umbral = Date.now() - 30 * 24 * 60 * 60 * 1000;
    const recientes = reportes.filter((r) => {
      const t = new Date(r.fechaGeneracion).getTime();
      return !Number.isNaN(t) && t >= umbral;
    }).length;
    elActivos.textContent = recientes;
  }
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
  cargarKpis();
  cargarPerfil();
});