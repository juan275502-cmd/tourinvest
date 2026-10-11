window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Api = (function () {
  const BASE_URL = "http://www.tourinvest.com:8080";

  function obtenerToken() {
    const token = sessionStorage.getItem("tourinvest_token");
    if (!token) {
      window.location.href = "login.html";
      return null;
    }
    return token;
  }

  async function llamar(ruta, opciones = {}) {
    const token = obtenerToken();
    if (!token) return null;

    const respuesta = await fetch(`${BASE_URL}${ruta}`, {
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

  function moneda(valor) {
    return new Intl.NumberFormat("es-CO", { style: "currency", currency: "USD", minimumFractionDigits: 2 }).format(valor);
  }

  function porcentaje(valor) {
    const signo = valor >= 0 ? "+" : "";
    return `${signo}${Number(valor).toFixed(2)}%`;
  }

  /** Escapa texto antes de insertarlo con innerHTML (evita XSS). */
  function esc(texto) {
    return String(texto ?? "").replace(/[<>&"']/g, (c) => (
      { "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&#39;" }[c]
    ));
  }

  return { BASE_URL, obtenerToken, llamar, moneda, porcentaje, esc };
})();