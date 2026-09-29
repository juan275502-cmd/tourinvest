// Prueba aislada de js/graficas.js: genera el markup de las tres graficas y
// lo guarda en /tmp para inspeccionarlo. No toca el DOM real.
const fs = require("fs");
const vm = require("vm");

// Stub minimo de DOM: createElementNS/createElement y innerHTML.
function elemento(tag) {
  return {
    tagName: tag, atributos: {}, texto: "", hijos: [], clase: "",
    setAttribute(k, v) { this.atributos[k] = v; },
    getAttribute(k) { return this.atributos[k]; },
    appendChild(h) { this.hijos.push(h); return h; },
    set textContent(v) { this.texto = v; },
    get textContent() { return this.texto; },
    set innerHTML(v) { this._html = v; this.hijos = []; },
    get innerHTML() { return this._html || ""; },
    toString() {
      const attrs = Object.entries(this.atributos)
        .map(([k, v]) => ` ${k}="${v}"`).join("");
      const hijos = this.hijos.map(String).join("");
      const inner = this._html != null ? this._html : this.texto + hijos;
      return `<${tag}${attrs}>${inner}</${tag}>`;
    },
  };
}

const ctx = { document: { createElementNS: (ns, t) => elemento(t), createElement: (t) => elemento(t), getElementById: () => null } };
ctx.window = ctx;
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname + "/../js/graficas.js", "utf8"), ctx);

const G = ctx.window.TourInvestUI.Graficas;
const salida = {};

// 1) Barras de variacion (con negativos, como las empresas).
const barras = elemento("div");
G.barras(barras, [
  { etiqueta: "AAPL", valor: 1.8, valorTexto: "+1.80%" },
  { etiqueta: "MSFT", valor: -0.65, valorTexto: "-0.65%" },
  { etiqueta: "AMZN", valor: 0.4, valorTexto: "+0.40%" },
], { titulo: "Variacion", colorearPorSigno: true, leyenda: "<span>leyenda</span>" });
salida.barras = barras.hijos.map(String).join("\n");

// 2) Barra apilada de liquidez.
const apilada = elemento("div");
G.barraApilada(apilada, 120000, 80000, { titulo: "Liquidez" });
salida.apilada = apilada.hijos.map(String).join("\n");

// 3) Dona del portafolio.
const dona = elemento("div");
G.doughnut(dona, [
  { etiqueta: "AAPL", valor: 1955, valorTexto: "$1,955" },
  { etiqueta: "MSFT", valor: 2160.75, valorTexto: "$2,160.75" },
], { titulo: "Portafolio", textoCentral: "$4,115.75" });
salida.dona = dona.hijos.map(String).join("\n");

// 4) Caso vacio: debe mostrar el placeholder, no romper.
const vacio = elemento("div");
G.barras(vacio, [], { vacio: "Sin datos." });
salida.vacio = vacio.innerHTML;

// 5) Escape de XSS en las etiquetas.
const xss = elemento("div");
G.barras(xss, [{ etiqueta: '<script>alert(1)</script>', valor: 1 }], {});
salida.xss = xss.hijos.map(String).join("\n");

// 6) Línea: serie con valores positivos y negativos.
const linea = elemento("div");
G.linea(linea, [
  { etiqueta: "AAPL", valor: 8.61, valorTexto: "+8.61%" },
  { etiqueta: "MSFT", valor: 5.28, valorTexto: "+5.28%" },
  { etiqueta: "TSLA", valor: -2.10, valorTexto: "-2.10%" },
  { etiqueta: "AMZN", valor: 1.50, valorTexto: "+1.50%" },
], { titulo: "Rendimiento", formatoEje: (v) => `${v.toFixed(0)}%` });
salida.linea = linea.hijos.map(String).join("\n");

// 7) Línea con un solo punto (no debe romperse ni quedar en la esquina).
const unPunto = elemento("div");
G.linea(unPunto, [{ etiqueta: "AAPL", valor: 3 }], { titulo: "Un punto" });
salida.unPunto = unPunto.hijos.map(String).join("\n");

// 8) Línea vacia.
const lineaVacia = elemento("div");
G.linea(lineaVacia, [], { vacio: "Sin datos." });
salida.lineaVacia = lineaVacia.innerHTML;

fs.writeFileSync("/tmp/graficas-prueba.html", Object.entries(salida)
  .map(([k, v]) => `<!-- ${k} -->\n${v}`).join("\n\n"));
console.log("OK -> /tmp/graficas-prueba.html");
for (const k of Object.keys(salida)) {
  console.log(`  ${k}: ${salida[k].length} chars`);
}
