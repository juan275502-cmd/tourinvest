// -----------------------------------------------------------------------------
// TourInvestUI.Graficas — graficas en SVG puro (sin librerias externas).
//
// Motivo: el proyecto no tiene Chart.js ni dependencias de terceros; se generan
// con SVG nativo para no anadir ~200 KB de JavaScript ni depender de Internet.
//
// Las funciones son puras sobre los datos que YA llegan de la API:
//   * barras()       -> variacion de las empresas (Analista)
//   * barraApilada() -> activo vs pasivo corriente (Analista, liquidez)
//   * doughnut()     -> reparto del portafolio por empresa (Inversionista)
//   * barras()       -> precios objetivo de las alertas (Inversionista)
// -----------------------------------------------------------------------------
window.TourInvestUI = window.TourInvestUI || {};

window.TourInvestUI.Graficas = (function () {
  const NS = "http://www.w3.org/2000/svg";

  /**
   * Crea un elemento SVG con viewBox, tamano intrinseco y accesibilidad.
   *
   * <p>IMPORTANTE: se fijan tambien los atributos `width`/`height`. Un SVG con
   * solo `viewBox` NO tiene tamano propio, asi que basta con que la hoja de
   * estilos falte o este cacheada (el `width:100%` no llega) para que se
   * estire a todo el ancho del contenedor. Con el tamano intrinseco, el dibujo
   * mantiene sus proporciones aunque falte el CSS; el CSS solo lo acota.
   */
  function svg(ancho, alto, etiqueta) {
    const el = document.createElementNS(NS, "svg");
    el.setAttribute("viewBox", `0 0 ${ancho} ${alto}`);
    el.setAttribute("width", ancho);
    el.setAttribute("height", alto);
    el.setAttribute("role", "img");
    el.setAttribute("aria-label", etiqueta || "Gráfica");
    return el;
  }

  function nodo(nombre, attrs, texto = null) {
    const el = document.createElementNS(NS, nombre);
    Object.entries(attrs).forEach(([k, v]) => el.setAttribute(k, v));
    if (texto !== null) el.textContent = texto;
    return el;
  }

  /** Escapa texto para insertarlo dentro del HTML/SVG de forma segura. */
  function esc(texto) {
    return String(texto).replace(/[<>&"']/g, (c) => (
      { "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&apos;" }[c]
    ));
  }

  function vacio(contenedor, mensaje) {
    contenedor.innerHTML = `<p class="grafica-vacia">${esc(mensaje)}</p>`;
  }

  function raiz(id) {
    return typeof id === "string" ? document.getElementById(id) : id;
  }

  /** Redondea a 2 decimales: evitaCoords con muchos decimales en el SVG. */
  function redondear(valor) {
    return Math.round(valor * 100) / 100;
  }

  /** Inserta el titulo de la grafica dentro del contenedor. */
  function conTitulo(contenedor, titulo) {
    contenedor.innerHTML = "";
    if (titulo) {
      const h = document.createElement("p");
      h.className = "grafica__titulo";
      h.textContent = titulo;
      contenedor.appendChild(h);
    }
  }

  /**
   * Barras horizontales; admite valores negativos (variacion de empresas).
   * El cero se coloca automaticamente segun el rango de los datos.
   */
  function barras(contenedorId, datos, opciones = {}) {
    const contenedor = raiz(contenedorId);
    if (!contenedor) return;
    if (!datos || datos.length === 0) {
      vacio(contenedor, opciones.vacio || "Sin datos para graficar.");
      return;
    }

    const ancho = 640;
    const altoFila = 30;
    const pad = { arriba: 8, derecha: 66, abajo: 10, izquierda: 84 };
    const alto = pad.arriba + datos.length * altoFila + pad.abajo;
    const areaAncho = ancho - pad.izquierda - pad.derecha;

    const max = Math.max(...datos.map((d) => d.valor), 0);
    const min = Math.min(...datos.map((d) => d.valor), 0);
    const rango = (max - min) || 1;
    const cero = pad.izquierda + areaAncho * ((0 - min) / rango);

    const grafica = svg(ancho, alto, opciones.titulo);
    grafica.appendChild(nodo("line", {
      x1: cero, y1: pad.arriba, x2: cero, y2: pad.arriba + datos.length * altoFila,
      class: "grafica__eje",
    }));

    datos.forEach((d, i) => {
      const y = pad.arriba + i * altoFila;
      const h = altoFila - 12;
      const largo = (Math.abs(d.valor) / rango) * areaAncho;
      const x = d.valor >= 0 ? cero : cero - largo;
      const clase = opciones.colorearPorSigno
        ? `grafica__barra grafica__barra--${d.valor >= 0 ? "positiva" : "negativa"}`
        : "grafica__barra";

      // Se redondea el ancho: sin esto el float produce valores como
      // "359.99999999999994" que ensucian el SVG.
      grafica.appendChild(nodo("rect", {
        x: redondear(x), y: y + 6, width: redondear(Math.max(largo, 1)), height: h,
        rx: 3, class: clase,
      }));
      grafica.appendChild(nodo("text", {
        x: pad.izquierda - 8, y: y + h / 2 + 10, "text-anchor": "end",
        class: "grafica__etiqueta",
      }, esc(d.etiqueta)));
      grafica.appendChild(nodo("text", {
        x: ancho - pad.derecha + 6, y: y + h / 2 + 10, "text-anchor": "start",
        class: "grafica__valor",
      }, esc(d.valorTexto != null ? d.valorTexto : d.valor)));
    });

    conTitulo(contenedor, opciones.titulo);
    contenedor.appendChild(grafica);
    if (opciones.leyenda) {
      const leyenda = document.createElement("div");
      leyenda.className = "grafica__leyenda";
      leyenda.innerHTML = opciones.leyenda;
      contenedor.appendChild(leyenda);
    }
  }

  /**
   * Barra apilada: activo corriente frente a pasivo corriente.
   * Cada seccion se mide sobre el total de los dos.
   */
  function barraApilada(contenedorId, activo, pasivo, opciones = {}) {
    const contenedor = raiz(contenedorId);
    if (!contenedor) return;
    if (!activo || !pasivo || activo <= 0) {
      vacio(contenedor, "Indica el activo y el pasivo corriente para ver el grafico.");
      return;
    }

    const total = activo + pasivo;
    const pctActivo = (activo / total) * 100;
    const pctPasivo = 100 - pctActivo;
    const liquidez = pasivo > 0 ? activo / pasivo : Infinity;

    const ancho = 640;
    const grafica = svg(ancho, 46, "Activo frente a pasivo corriente");
    grafica.appendChild(nodo("rect", {
      x: 0, y: 6, width: redondear((pctActivo / 100) * ancho), height: 32, rx: 4,
      class: "grafica__barra--positiva",
    }));
    grafica.appendChild(nodo("rect", {
      x: redondear((pctActivo / 100) * ancho), y: 6, width: redondear((pctPasivo / 100) * ancho), height: 32, rx: 4,
      class: "grafica__barra--negativa",
    }));
    // El % va en blanco: se lee bien sobre ambos colores.
    grafica.appendChild(nodo("text", {
      x: 12, y: 27, class: "grafica__etiqueta", fill: "#FFFFFF",
    }, `Activo ${pctActivo.toFixed(1)}%`));
    grafica.appendChild(nodo("text", {
      x: ancho - 12, y: 27, class: "grafica__etiqueta", fill: "#FFFFFF", "text-anchor": "end",
    }, `Pasivo ${pctPasivo.toFixed(1)}%`));

    conTitulo(contenedor, opciones.titulo);
    contenedor.appendChild(grafica);

    const pie = document.createElement("div");
    pie.className = "grafica__leyenda";
    pie.innerHTML = "<span class='positivo'>Activo corriente</span>"
      + "<span class='negativo'>Pasivo corriente</span>"
      + `<span>Ratio: <strong>${Number.isFinite(liquidez) ? liquidez.toFixed(2) : "—"}</strong></span>`;
    contenedor.appendChild(pie);
  }

  /**
   * Dona (anillo) para el reparto del portafolio por empresa.
   * Se dibuja con arcos SVG; el hueco central lleva el total.
   */
  function doughnut(contenedorId, datos, opciones = {}) {
    const contenedor = raiz(contenedorId);
    if (!contenedor) return;
    if (!datos || datos.length === 0) {
      vacio(contenedor, opciones.vacio || "Aun no tienes inversiones en el portafolio.");
      return;
    }

    const total = datos.reduce((suma, d) => suma + d.valor, 0);
    if (total <= 0) {
      vacio(contenedor, "No hay valores suficientes para graficar.");
      return;
    }

    // El anillo se dibuja pequeno a proposito: viewBox de 160 px. Antes usaba
    // 200 y seguia viéndose grande; ademas los atributos width/height hacen que
    // respete este tamaño aunque la hoja de estilos no llegue (o este cacheada).
    const tam = 160;
    const radio = 56;
    const grosor = 24;
    const centro = tam / 2;
    const circunferencia = 2 * Math.PI * radio;
    const colores = ["#1E3A5F", "#2E8B57", "#6fa6d9", "#C08A2E", "#7A5C99", "#4A7C8C"];

    const grafica = svg(tam, tam, opciones.titulo);
    // Anillo de fondo.
    grafica.appendChild(nodo("circle", {
      cx: centro, cy: centro, r: radio, fill: "none", stroke: "#E8ECF1", "stroke-width": grosor,
    }));

    let acumulado = 0;
    datos.forEach((d, i) => {
      const fraccion = d.valor / total;
      grafica.appendChild(nodo("circle", {
        cx: centro, cy: centro, r: radio, fill: "none",
        stroke: colores[i % colores.length], "stroke-width": grosor,
        "stroke-dasharray": `${redondear(fraccion * circunferencia)} ${circunferencia}`,
        "stroke-dashoffset": redondear(-acumulado * circunferencia),
        // rotate(-90) hace que el anillo empiece arriba y no a la derecha.
        transform: `rotate(-90 ${centro} ${centro})`,
      }));
      acumulado += fraccion;
    });

    grafica.appendChild(nodo("text", {
      x: centro, y: centro + 1, "text-anchor": "middle", class: "grafica__valor", "font-size": "17",
    }, esc(opciones.textoCentral != null ? opciones.textoCentral : total.toFixed(0))));
    grafica.appendChild(nodo("text", {
      x: centro, y: centro + 16, "text-anchor": "middle", class: "grafica__etiqueta",
    }, esc(opciones.pieCentral || "Total")));

    conTitulo(contenedor, opciones.titulo);
    contenedor.appendChild(grafica);

    const leyenda = document.createElement("div");
    leyenda.className = "grafica__leyenda";
    leyenda.innerHTML = datos.map((d, i) => (
      `<span><span style="display:inline-block;width:10px;height:10px;border-radius:2px;`
      + `background:${colores[i % colores.length]};margin-right:6px;vertical-align:middle"></span>`
      + `${esc(d.etiqueta)} · ${esc(d.valorTexto != null ? d.valorTexto : d.valor.toFixed(0))}</span>`
    )).join("");
    contenedor.appendChild(leyenda);
  }

  /**
   * Grafica de linea: evolucion de un valor por posicion (por ejemplo, el
   * rendimiento acumulado del portafolio). Es la tercera vista del selector.
   */
  function linea(contenedorId, datos, opciones = {}) {
    const contenedor = raiz(contenedorId);
    if (!contenedor) return;
    if (!datos || datos.length === 0) {
      vacio(contenedor, opciones.vacio || "Sin datos para graficar.");
      return;
    }

    const ancho = 640;
    const alto = 240;
    const pad = { arriba: 20, derecha: 24, abajo: 44, izquierda: 56 };
    const areaAncho = ancho - pad.izquierda - pad.derecha;
    const areaAlto = alto - pad.arriba - pad.abajo;

    const valores = datos.map((d) => Number(d.valor) || 0);
    const max = Math.max(...valores, 0);
    const min = Math.min(...valores, 0);
    const rango = (max - min) || 1;
    const yDe = (v) => pad.arriba + areaAlto * ((max - v) / rango);
    // Si solo hay un punto, lo centramos en X para que no quede en la esquina.
    const xDe = (i) => (datos.length === 1
      ? pad.izquierda + areaAncho / 2
      : pad.izquierda + (i / (datos.length - 1)) * areaAncho);

    const grafica = svg(ancho, alto, opciones.titulo);

    // Linea de base en el cero (si la serie lo cruza).
    if (min < 0) {
      grafica.appendChild(nodo("line", {
        x1: pad.izquierda, y1: redondear(yDe(0)), x2: ancho - pad.derecha, y2: redondear(yDe(0)),
        class: "grafica__eje",
      }));
    }
    // Rejilla horizontal: 3 lineas de referencia.
    [0, 0.5, 1].forEach((f) => {
      const valor = max - f * rango;
      grafica.appendChild(nodo("line", {
        x1: pad.izquierda, y1: redondear(pad.arriba + f * areaAlto),
        x2: ancho - pad.derecha, y2: redondear(pad.arriba + f * areaAlto),
        class: "grafica__eje",
      }));
      grafica.appendChild(nodo("text", {
        x: pad.izquierda - 8, y: redondear(pad.arriba + f * areaAlto + 4),
        "text-anchor": "end", class: "grafica__etiqueta",
      }, esc(opciones.formatoEje ? opciones.formatoEje(valor) : valor.toFixed(0))));
    });

    // Area bajo la linea (degradado suave) + trazo.
    const puntos = datos.map((d, i) => `${redondear(xDe(i))},${redondear(yDe(Number(d.valor) || 0))}`);
    const ceroY = redondear(yDe(Math.max(min, 0)));
    grafica.appendChild(nodo("polygon", {
      points: `${pad.izquierda},${ceroY} ${puntos.join(" ")} ${redondear(xDe(datos.length - 1))},${ceroY}`,
      class: "grafica__area",
    }));
    grafica.appendChild(nodo("polyline", {
      points: puntos.join(" "),
      class: "grafica__linea",
    }));

    // Marcador + etiqueta en cada punto.
    datos.forEach((d, i) => {
      const cx = redondear(xDe(i));
      const cy = redondear(yDe(Number(d.valor) || 0));
      grafica.appendChild(nodo("circle", { cx, cy, r: 4, class: "grafica__punto" }));
      grafica.appendChild(nodo("text", {
        x: cx, y: cy - 10, "text-anchor": "middle", class: "grafica__valor",
      }, esc(d.valorTexto != null ? d.valorTexto : d.valor.toFixed(0))));
      // Etiqueta del eje X, inclinada si hay muchas.
      const etiquetaX = {
        x: cx, y: alto - 20,
        "text-anchor": datos.length > 8 ? "end" : "middle",
        class: "grafica__etiqueta",
      };
      // Solo se rota si hay muchas; setAttribute con null no es valido.
      if (datos.length > 8) etiquetaX.transform = `rotate(-35 ${cx} ${alto - 20})`;
      grafica.appendChild(nodo("text", etiquetaX, esc(d.etiqueta)));
    });

    conTitulo(contenedor, opciones.titulo);
    contenedor.appendChild(grafica);
  }

  /**
   * Crea un selector de tipo de grafica (botones tipo "pill") y devuelve el
   * tipo activo. Permite al usuario cambiar entre Anillo / Barras / Linea.
   *
   * Se guarda la preferencia en localStorage para que no se pierda al cambiar
   * de vista o recargar la pagina.
   */
  function crearSelector(idSelector, tipos, claveStorage) {
    const contenedor = raiz(idSelector);
    if (!contenedor) return;

    let activo = tipos[0].id;
    try {
      const guardado = localStorage.getItem(claveStorage);
      if (guardado && tipos.some((t) => t.id === guardado)) activo = guardado;
    } catch (e) {
      // localStorage puede estar bloqueado: se usa el tipo por defecto.
    }

    const pintar = () => {
      contenedor.innerHTML = "";
      contenedor.className = "grafica-selector";
      tipos.forEach((tipo) => {
        const boton = document.createElement("button");
        boton.type = "button";
        boton.className = "grafica-selector__boton";
        boton.textContent = tipo.etiqueta;
        boton.setAttribute("aria-pressed", tipo.id === activo ? "true" : "false");
        boton.addEventListener("click", () => {
          activo = tipo.id;
          try {
            localStorage.setItem(claveStorage, activo);
          } catch (e) { /* sin persistencia */ }
          pintar();
          document.dispatchEvent(new CustomEvent("grafica:tipo", {
            detail: { clave: claveStorage, tipo: activo },
          }));
        });
        contenedor.appendChild(boton);
      });
    };

    pintar();
    return () => activo;
  }

  return { barras, barraApilada, doughnut, linea, crearSelector, redondear, esc, raiz, conTitulo, vacio, svg, nodo };
})();
