// frontend/js/colombia.js
//
// Integración con la API pública de Colombia (https://api-colombia.com).
// Tiene CORS abierto (Access-Control-Allow-Origin: *), así que el navegador la
// consume DIRECTO, sin pasar por el backend Java ni por un proxy.
//
// Qué aporta a TourInvest: cada empresa de turismo se asocia a un DEPARTAMENTO
// real de Colombia y, en el modal de detalle, se muestra su contexto
// (región, población, superficie, municipios) y sus ATRACCIONES turísticas
// reales. Así la ficha de inversión deja de ser solo números.
//
// Es autocontenido (no depende de api.js) para poder probarlo con node igual
// que se hace con auth.js / graficas.js.

window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Colombia = (function () {
  const BASE = "https://api-colombia.com/api/v1";

  // Empresa real de hotelería/turismo en Colombia -> id del departamento donde
  // tiene su principal sede o destino (ids reales de la API de Colombia:
  // Antioquia 2, Bogotá 5, Bolívar 6, Magdalena 20, Meta 21, Quindío 25,
  // Valle del Cauca 31). Debe coincidir con los nombres de `query.sql`
  // (backend Java) y `backend-python/core/database.py` (réplica). Es una
  // asociación de demostración configurable: basta ajustar una entrada aquí.
  const MAPEO = [
    { claves: ["ghl"], departamentoId: 5 },                   // GHL Hoteles     -> Bogotá
    { claves: ["decameron"], departamentoId: 6 },             // Decameron       -> Bolívar (Cartagena)
    { claves: ["dann"], departamentoId: 2 },                  // Dann Carlton    -> Antioquia (Medellín)
    { claves: ["estelar"], departamentoId: 31 },              // Estelar Hoteles -> Valle del Cauca (Cali)
    { claves: ["aviatur"], departamentoId: 5 },               // Aviatur         -> Bogotá
    { claves: ["satena"], departamentoId: 21 },               // Satena          -> Meta (Villavicencio)
    { claves: ["parque del cafe", "parque del café"], departamentoId: 25 }, // Parque del Café -> Quindío
    { claves: ["masaya"], departamentoId: 20 },               // Masaya Hostels  -> Magdalena (Santa Marta)
  ];

  // Caché en memoria: /Department y /Region solo se piden UNA vez por sesión.
  let cacheDepartamentos = null;
  let cacheRegiones = null;

  /** Minúsculas y sin tildes, para que el mapeo no dependa de acentos. */
  function norm(texto) {
    return String(texto || "")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "");
  }

  /** Escapa texto antes de insertarlo con innerHTML (evita XSS). */
  function esc(texto) {
    return String(texto ?? "").replace(/[<>&"']/g, (c) => (
      { "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&#39;" }[c]
    ));
  }

  /** GET tolerante a fallos: si la API no responde devuelve null (no rompe). */
  async function pedir(ruta) {
    try {
      const respuesta = await fetch(`${BASE}${ruta}`);
      if (!respuesta.ok) return null;
      return await respuesta.json();
    } catch (e) {
      return null;
    }
  }

  async function departamentos() {
    if (!cacheDepartamentos) cacheDepartamentos = (await pedir("/Department")) || [];
    return cacheDepartamentos;
  }

  async function regiones() {
    if (!cacheRegiones) cacheRegiones = (await pedir("/Region")) || [];
    return cacheRegiones;
  }

  /** Id de departamento asociado a una empresa, o null si no hay mapeo. */
  function departamentoIdDeEmpresa(nombreEmpresa) {
    const n = norm(nombreEmpresa);
    for (const item of MAPEO) {
      if (item.claves.some((clave) => n.includes(norm(clave)))) return item.departamentoId;
    }
    return null;
  }

  /** Nombre legible de la región a partir del regionId del departamento. */
  function nombreRegion(departamento, listaRegiones) {
    if (!departamento) return null;
    if (departamento.region && departamento.region.name) return departamento.region.name;
    const region = (listaRegiones || []).find((r) => r.id === departamento.regionId);
    return region ? region.name : null;
  }

  /** Recorta un texto largo a `max` caracteres sin cortar a media palabra. */
  function resumir(texto, max = 160) {
    const t = String(texto || "").trim();
    if (t.length <= max) return t;
    const corte = t.slice(0, max);
    const espacio = corte.lastIndexOf(" ");
    return `${(espacio > 40 ? corte.slice(0, espacio) : corte)}…`;
  }

  /**
   * Construye el HTML del bloque "Colombia" para el modal de detalle.
   * Pura (sin DOM) para poder testearla con node.
   */
  function renderContexto(departamento, region, atracciones, limite = 3) {
    if (!departamento) {
      return `<div class="colombia-vacio">Sin datos de ubicación para esta empresa.</div>`;
    }

    const datos = [];
    if (region) datos.push(`<span><strong>Región:</strong> ${esc(region)}</span>`);
    if (departamento.population != null) {
      datos.push(`<span><strong>Población:</strong> ${new Intl.NumberFormat("es-CO").format(departamento.population)}</span>`);
    }
    if (departamento.surface != null) {
      datos.push(`<span><strong>Superficie:</strong> ${new Intl.NumberFormat("es-CO").format(departamento.surface)} km²</span>`);
    }
    if (departamento.municipalities != null) {
      datos.push(`<span><strong>Municipios:</strong> ${esc(departamento.municipalities)}</span>`);
    }

    const tarjetas = (atracciones || []).slice(0, limite).map((a) => {
      const imagen = (a.images && a.images[0]) ? a.images[0] : null;
      const ciudad = (a.city && a.city.name) ? a.city.name : "";
      const imgHtml = imagen
        ? `<img class="colombia-atraccion__img" src="${esc(imagen)}" alt="${esc(a.name)}" loading="lazy" onerror="this.style.display='none'">`
        : "";
      return `
        <figure class="colombia-atraccion">
          ${imgHtml}
          <figcaption>
            <strong>${esc(a.name)}</strong>
            ${ciudad ? `<span class="muted"> · ${esc(ciudad)}</span>` : ""}
            <p class="muted">${esc(resumir(a.description, 90))}</p>
          </figcaption>
        </figure>`;
    }).join("");

    return `
      <section class="colombia">
        <h4 class="colombia__titulo">Ubicación · ${esc(departamento.name)}, Colombia</h4>
        <div class="colombia__datos">${datos.join("")}</div>
        ${departamento.description ? `<p class="muted colombia__desc">${esc(resumir(departamento.description))}</p>` : ""}
        ${tarjetas
          ? `<div class="colombia__atracciones"><h5>Atracciones turísticas</h5><div class="colombia__grid">${tarjetas}</div></div>`
          : `<div class="colombia__atracciones"><h5>Atracciones turísticas</h5><div class="colombia-vacio">Este departamento aún no tiene atracciones turísticas registradas en la API de Colombia.</div></div>`}
      </section>`;
  }

  /**
   * Enriquece la ficha de una empresa con los datos de su departamento.
   * `onRender` recibe el HTML ya construido (para poder testearlo sin DOM).
   */
  async function enriquecerEmpresa(nombreEmpresa, onRender) {
    const departamentoId = departamentoIdDeEmpresa(nombreEmpresa);
    if (departamentoId == null) {
      if (onRender) onRender(renderContexto(null));
      return;
    }

    // Pintamos un "cargando" de inmediato y luego el contenido real.
    if (onRender) onRender(`<div class="colombia-cargando">Cargando datos de Colombia…</div>`);

    const [listaDeptos, listaRegiones, atracciones] = await Promise.all([
      departamentos(),
      regiones(),
      pedir(`/Department/${departamentoId}/touristicattractions`),
    ]);

    const departamento = listaDeptos.find((d) => d.id === departamentoId) || null;
    const region = nombreRegion(departamento, listaRegiones);
    if (onRender) onRender(renderContexto(departamento, region, atracciones));
  }

  // ---------- Vista \"Colombia\" (selector de departamentos) ----------
  // Vive aquí (y no en cada dashboard) para que los TRES paneles la reutilicen.
  // Rellena el <select id=\"selector-departamento\"> y el <div id=\"colombia-contenido\">
  // de la vista activa. Si la API no responde, avisa sin romper el panel.

  async function cargarVistaColombia() {
    const selector = document.getElementById("selector-departamento");
    const contenedor = document.getElementById("colombia-contenido");
    if (!selector || !contenedor) return;
    if (selector.dataset.cargado === "1") return; // ya está, no volver a pedir

    selector.innerHTML = `<option value="">Cargando departamentos…</option>`;
    contenedor.innerHTML = `<div class="colombia-cargando">Cargando datos de Colombia…</div>`;

    const [deptos, listaRegiones] = await Promise.all([departamentos(), regiones()]);

    if (!deptos || deptos.length === 0) {
      selector.innerHTML = `<option value="">(API no disponible)</option>`;
      contenedor.innerHTML = `<div class="colombia-vacio">No fue posible conectar con la API de Colombia. Revisa tu conexión y vuelve a intentarlo.</div>`;
      return;
    }

    window.__colombiaDeptos = deptos;
    window.__colombiaRegiones = listaRegiones;

    selector.innerHTML =
      `<option value="">— Elige un departamento —</option>` +
      deptos
        .slice()
        .sort((a, b) => a.name.localeCompare(b.name, "es"))
        .map((d) => `<option value="${d.id}">${esc(d.name)}</option>`)
        .join("");
    selector.dataset.cargado = "1";
    contenedor.innerHTML = "";
  }

  async function mostrarDepartamento(id) {
    const contenedor = document.getElementById("colombia-contenido");
    if (!contenedor) return;
    if (!id) {
      contenedor.innerHTML = "";
      return;
    }

    contenedor.innerHTML = `<div class="colombia-cargando">Cargando datos de Colombia…</div>`;

    const deptos = window.__colombiaDeptos || (await departamentos());
    const listaRegiones = window.__colombiaRegiones || (await regiones());
    const atracciones = await pedir(`/Department/${id}/touristicattractions`);

    const departamento = deptos.find((d) => d.id === Number(id)) || null;
    const region = nombreRegion(departamento, listaRegiones);
    contenedor.innerHTML = renderContexto(departamento, region, atracciones, 6);
  }

  return {
    BASE,
    cargarVistaColombia,
    mostrarDepartamento,
    departamentoIdDeEmpresa,
    nombreRegion,
    resumir,
    renderContexto,
    enriquecerEmpresa,
    esc,
    // Datos crudos para la vista "Colombia" del panel del inversionista.
    listarDepartamentos: departamentos,
    listarRegiones: regiones,
    atraccionesDe: (id) => pedir(`/Department/${id}/touristicattractions`),
  };
})();

// Exponer las dos funciones de la vista como GLOBALES, para que el atributo
// inline onchange="mostrarDepartamento(this.value)" del <select> y la
// navegación de los TRES paneles (inversionista, analista, administrador)
// puedan invocarlas sin depender de dashboard.js. Así hay una sola copia.
window.cargarVistaColombia = window.TourInvestUI.Colombia.cargarVistaColombia;
window.mostrarDepartamento = window.TourInvestUI.Colombia.mostrarDepartamento;

