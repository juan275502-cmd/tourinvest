# -*- coding: utf-8 -*-
"""Suite de validacion de estructura HTML/JS de los dashboards de TourInvest."""

import os
import re
import unittest

FRONTEND = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # .../frontend
JS_DIR = os.path.join(FRONTEND, "js")
# Las paginas viven en frontend/view/ (no en la raiz de frontend/), por eso los
# <script> se referencian como "../js/..." y no como "js/...".
HTML_DIR = os.path.join(FRONTEND, "view")
# El backend Java es la fuente de verdad de las reglas de validacion.
BACKEND = os.path.join(
    os.path.dirname(FRONTEND), "backend-servlets", "src", "main", "java", "com",
    "tourinvest", "backend",
)


def leer(ruta):
    with open(ruta, encoding="utf-8") as f:
        return f.read()


def listar_ids_driver(js):
    return set(re.findall(r'getElementById\("([^"]+)"\)', js))


def listar_campos_form(js):
    return set(re.findall(r"formulario\.([A-Za-z_][A-Za-z0-9_]*)\.value", js))


DASHBOARDS = {
    "inversionista.html": "dashboard.js",
    "analista.html": "dashboard_analista.js",
    "administrador.html": "dashboard_administrador.js",
}


class TestDashboardScriptsCargados(unittest.TestCase):
    def test_cada_dashboard_carga_su_driver(self):
        for html, driver in DASHBOARDS.items():
            html_src = leer(os.path.join(HTML_DIR, html))
            self.assertIn(f'src="../js/{driver}"', html_src,
                          f"{html} debe cargar ../js/{driver}")


class TestDashboardIds(unittest.TestCase):
    def test_ids_del_driver_presentes(self):
        for html, driver in DASHBOARDS.items():
            html_src = leer(os.path.join(HTML_DIR, html))
            js_src = leer(os.path.join(JS_DIR, driver))
            ids_necesarios = listar_ids_driver(js_src)
            for id_ in ids_necesarios:
                with self.subTest(html=html, id=id_):
                    self.assertIn(f'id="{id_}"', html_src,
                                  f"{html} no contiene el id '{id_}' requerido por {driver}")

    def test_vistas_panel_por_dashboard(self):
        esperado = {
            "inversionista.html": ["portafolio", "alertas", "empresas", "colombia", "configuracion"],
            "analista.html": ["empresas", "indicadores", "reportes", "recomendaciones", "colombia", "configuracion"],
            "administrador.html": ["usuarios", "roles", "auditorias", "configuracion", "colombia"],
        }
        for html, panes in esperado.items():
            html_src = leer(os.path.join(HTML_DIR, html))
            for p in panes:
                with self.subTest(html=html, panel=p):
                    self.assertIn(f'data-vista-panel="{p}"', html_src,
                                  f"{html} no contiene la vista '{p}'")


class TestFormulariosCableados(unittest.TestCase):
    """Los formularios que el driver enlaza por id deben tener los campos que lee."""

    CASOS = {
        "inversionista.html": {
            "form-agregar-portafolio": ["cantidad"],
            "form-crear-alerta": ["precioObjetivo"],
        },
        "analista.html": {
            "form-liquidez": ["activoCorriente", "pasivoCorriente"],
            "form-crear-reporte": ["idEmpresa", "titulo", "descripcion"],
        },
    }

    def _bloque_form(self, html, form_id):
        m = re.search(r'<form[^>]*id="%s".*?</form>' % form_id, html, re.S)
        self.assertIsNotNone(m, f"No se encontro el form {form_id}")
        return m.group(0)

    def test_campos_requeridos_presentes(self):
        for html, forms in self.CASOS.items():
            html_src = leer(os.path.join(HTML_DIR, html))
            for form_id, campos in forms.items():
                bloque = self._bloque_form(html_src, form_id)
                for campo in campos:
                    with self.subTest(html=html, form=form_id, campo=campo):
                        self.assertIn(f'name="{campo}"', bloque,
                                      f"El form {form_id} de {html} deberia tener name='{campo}'")


class TestSinResiduosDeVocabularioViejo(unittest.TestCase):
    def test_no_hay_campo(self):
        patron = re.compile(r'\.campo__|\.campo--|closest\("\.campo"\)')
        for a in os.listdir(JS_DIR):
            if a.endswith(".js"):
                self.assertFalse(patron.search(leer(os.path.join(JS_DIR, a))),
                                 f"Residuo de vocabulario .campo en js/{a}")


class TestAuthCableado(unittest.TestCase):
    def test_auth_carga_auth_js(self):
        for pagina in ["login.html", "registro.html", "recuperar.html"]:
            src = leer(os.path.join(HTML_DIR, pagina))
            self.assertIn('src="../js/auth.js"', src, f"{pagina} no carga ../js/auth.js")

    def test_campos_login(self):
        src = leer(os.path.join(HTML_DIR, "login.html"))
        for campo in ["correo", "contrasena"]:
            self.assertIn(f'name="{campo}"', src)

    def test_campos_registro(self):
        src = leer(os.path.join(HTML_DIR, "registro.html"))
        for campo in ["nombre1", "apellido1", "cedula", "fechaNacimiento", "correo",
                      "contrasena", "confirmarContrasena"]:
            self.assertIn(f'name="{campo}"', src)


class TestPoliticaContrasenaEnElFormulario(unittest.TestCase):
    """El formulario de registro debe reflejar la politica de contrasena."""

    REGISTRO = os.path.join(HTML_DIR, "registro.html")

    def _input_por_id(self, id_input):
        src = leer(self.REGISTRO)
        m = re.search(r'<input[^>]*id="%s"[^>]*>' % id_input, src, re.S)
        self.assertIsNotNone(m, f"registro.html no tiene el input id='{id_input}'")
        return m.group(0)

    def test_longitud_minima_y_maxima_de_contrasena(self):
        for campo in ["contrasena", "confirmarContrasena"]:
            bloque = self._input_por_id(campo)
            with self.subTest(campo=campo):
                self.assertIn('minlength="12"', bloque,
                              f"{campo} debe exigir 12 caracteres (PV-11)")
                self.assertIn('maxlength="72"', bloque,
                              f"{campo} debe limitar a 72 caracteres (PV-13)")

    def test_no_queda_el_minlength_antiguo(self):
        self.assertNotIn('minlength="6"', leer(self.REGISTRO))

    def test_checklist_de_reglas_presente(self):
        src = leer(self.REGISTRO)
        self.assertIn('id="reglas-contrasena"', src)
        for regla in ["longitud", "mayusculas", "minusculas", "numeros", "especiales"]:
            with self.subTest(regla=regla):
                self.assertIn(f'data-regla="{regla}"', src,
                              f"falta la regla '{regla}' en el checklist")

    def test_cedula_solo_numerica(self):
        bloque = self._input_por_id("cedula")
        self.assertIn('pattern="[0-9]{6,20}"', bloque, "PV-14: la cedula admite solo digitos")

    def test_nombre_admite_tildes_espacios_y_tope(self):
        for campo in ["nombre1", "apellido1"]:
            bloque = self._input_por_id(campo)
            with self.subTest(campo=campo):
                self.assertIn("pattern=", bloque, "PV-03/04/05: el nombre tiene patron")
                self.assertIn("áéíóúüöñ", bloque, "PV-04: el patron incluye letras del español")
                self.assertIn("maxlength=", bloque, "PV-13: el nombre tiene tope de longitud")

    def test_auth_js_expone_las_validaciones(self):
        js = leer(os.path.join(JS_DIR, "auth.js"))
        for funcion in ["validarContrasena", "validarCorreo", "validarNombre",
                        "validarCedula", "validarFecha", "validarFormularioRegistro",
                        "requisitosContrasena"]:
            with self.subTest(funcion=funcion):
                self.assertIn(f"function {funcion}(", js,
                              f"auth.js debe definir {funcion}")

    def test_la_politica_coincide_con_el_backend(self):
        """12/2/2/2/2 en JS debe ser el mismo numero que en PasswordPolicy.java."""
        js = leer(os.path.join(JS_DIR, "auth.js"))
        java = leer(os.path.join(BACKEND, "validation", "PasswordPolicy.java"))

        for clave, valor in [("longitudMinima", 12), ("minimoMayusculas", 2),
                             ("minimoMinusculas", 2), ("minimoNumeros", 2),
                             ("minimoEspeciales", 2)]:
            with self.subTest(regla=clave):
                self.assertIn(f"{clave}: {valor}", js)

        for constante, valor in [("LONGITUD_MINIMA", 12), ("MINIMO_MAYUSCULAS", 2),
                                 ("MINIMO_MINUSCULAS", 2), ("MINIMO_NUMEROS", 2),
                                 ("MINIMO_ESPECIALES", 2)]:
            with self.subTest(constante=constante):
                self.assertIn(f"{constante} = {valor};", java)

    def test_registro_bloquea_el_envio_si_hay_errores(self):
        js = leer(os.path.join(JS_DIR, "auth.js"))
        self.assertIn("if (validarFormularioRegistro(formulario)) {", js,
                      "registrarUsuario debe cortar el envio si la validacion falla")

    def test_registro_avisa_de_la_edad_minima(self):
        src = leer(self.REGISTRO)
        self.assertIn("field__ayuda", src, "debe explicar el requisito de mayoria de edad")
        self.assertIn("18", src)

    def test_auth_js_limita_el_selector_de_fecha(self):
        """auth.js acota el calendario: min = hoy - 18 años, max = ayer."""
        js = leer(os.path.join(JS_DIR, "auth.js"))
        self.assertIn("const EDAD_MINIMA = 18;", js)
        self.assertIn("function activarLimitesFecha(", js)
        self.assertIn("campo.max = aIsoFecha(max)", js)
        self.assertIn("campo.min = aIsoFecha(min)", js)
        self.assertIn("activarLimitesFecha();", js,
                      "debe ejecutarse en el arranque de la pagina")

    def test_la_edad_minima_coincide_con_el_backend(self):
        js = leer(os.path.join(JS_DIR, "auth.js"))
        java = leer(os.path.join(BACKEND, "validation", "MayorDeEdad.java"))
        dto = leer(os.path.join(BACKEND, "dto", "DatosPersonalesRequest.java"))

        self.assertIn("const EDAD_MINIMA = 18;", js)
        self.assertIn("int minima() default 18;", java)
        self.assertIn("@MayorDeEdad(minima = 18", dto)


class TestCoherenciaProduccion(unittest.TestCase):
    """Cambios de coherencia multi-rol: Configuración, tema, legales, KPIs."""

    PANELS = ["inversionista.html", "analista.html", "administrador.html"]

    def _html(self, nombre):
        return leer(os.path.join(HTML_DIR, nombre))

    # ---- Configuración unificada (antes "Perfil" en algunos roles) ----------
    def test_sidebar_usa_configuracion_en_los_tres_roles(self):
        for nombre in self.PANELS:
            with self.subTest(panel=nombre):
                self.assertIn('data-vista="configuracion"', self._html(nombre),
                              f"{nombre} debe enlazar la vista Configuración")
                self.assertNotIn('data-vista="perfil"', self._html(nombre),
                                 f"{nombre} no debe usar ya el nombre 'perfil'")

    def test_configuracion_incluye_tema_y_legales(self):
        for nombre in self.PANELS:
            src = self._html(nombre)
            with self.subTest(panel=nombre):
                self.assertIn('data-tema-btn="diurno"', src)
                self.assertIn('data-tema-btn="nocturno"', src)
                self.assertIn('data-tema-btn="sistema"', src)
                self.assertIn("TourInvestUI.Configuracion.abrir('tratamiento')", src)
                self.assertIn("TourInvestUI.Configuracion.abrir('privacidad')", src)
                self.assertIn("TourInvestUI.Configuracion.abrir('terminos')", src)

    def test_carga_configuracion_js(self):
        for nombre in self.PANELS:
            with self.subTest(panel=nombre):
                self.assertIn('src="../js/configuracion.js"', self._html(nombre))

    # ---- Sidebar plegable con iniciales -------------------------------------
    def test_topbar_tiene_toggle_sidebar(self):
        for nombre in self.PANELS:
            with self.subTest(panel=nombre):
                self.assertIn('id="btn-toggle-sidebar"', self._html(nombre))

    def test_boton_unico_en_brand_sin_duplicado_en_header(self):
        # UN SOLO botón: el del .brand del aside. El header NO tiene hamburguesa.
        for nombre in self.PANELS:
            src = self._html(nombre)
            with self.subTest(panel=nombre):
                self.assertIn('id="btn-toggle-sidebar"', src)
                self.assertNotIn('btn-abrir-menu', src)
                self.assertNotIn('sidebar-toggle--movil', src)
                self.assertEqual(src.count('btn-toggle-sidebar'), 1,
                                 f"una sola hamburguesa en {nombre}")

    def test_toggle_dentro_del_brand(self):
        # La hamburguesa vive DENTRO del .brand, no en el topbar.
        for nombre in self.PANELS:
            src = self._html(nombre)
            with self.subTest(panel=nombre):
                m = re.search(r'<div class="brand">(.*?)btn-toggle-sidebar', src, re.S)
                self.assertIsNotNone(m, f"el botón debe estar dentro del .brand en {nombre}")
                # ...y no debe quedar otra copia fuera (p. ej. en el topbar)
                self.assertEqual(src.count('id="btn-toggle-sidebar"'), 1,
                                 f"una sola hamburguesa en {nombre}")

    def test_brand_tiene_logo_nombre_y_boton(self):
        # Brand en 3 zonas: logo (brand__logo) + nombre (brand__name) + botón.
        for nombre in self.PANELS:
            src = self._html(nombre)
            with self.subTest(panel=nombre):
                self.assertIn('class="brand__logo"', src)
                self.assertIn('class="brand__name"', src)
                m = re.search(r'<div class="brand">(.*?)</div>\s*<span class="brand__name">', src, re.S)
                self.assertIsNotNone(m, f"logo antes del nombre en {nombre}")

    def test_js_asigna_iniciales_a_enlaces(self):
        js = leer(os.path.join(JS_DIR, "app.js"))
        self.assertIn("asignarIniciales()", js)
        self.assertIn('data-inicial', js)
        self.assertIn('sidebar--colapsado', js)

    def test_css_colapsado_usa_circulos_con_inicial(self):
        css = leer(os.path.join(os.path.dirname(JS_DIR), "css", "styles.css"))
        self.assertIn("content: attr(data-inicial)", css)

    def test_css_menu_superior_movil(self):
        css = leer(os.path.join(os.path.dirname(JS_DIR), "css", "styles.css"))
        self.assertIn("sidebar--movil-abierto", css)
        # Sin overlay ni botón duplicado en el header.
        self.assertNotIn("sidebar-toggle--movil", css)
        # El menú móvil abierto usa los mismos círculos con inicial que el
        # plegado de escritorio (fila con wrap bajo la barra superior).
        self.assertIn("flex-wrap: wrap", css)
        # El centrado de la inicial anula el gap heredado de la regla base.
        self.assertIn("gap: 0", css)

    def test_js_menu_movil_sin_boton_duplicado(self):
        js = leer(os.path.join(JS_DIR, "app.js"))
        self.assertIn("drawerMovil", js)
        self.assertNotIn("btn-abrir-menu", js)

    def test_navegacion_persiste_panel_activo(self):
        # app.js expone el helper compartido de persistencia.
        js = leer(os.path.join(JS_DIR, "app.js"))
        self.assertIn("UI.Navegacion", js)
        self.assertIn("ti_vista", js)
        self.assertIn("hashchange", js)
        # Los 3 dashboards lo usan (una sola implementación, sin copias).
        for driver in ("dashboard.js", "dashboard_analista.js",
                       "dashboard_administrador.js"):
            src = leer(os.path.join(JS_DIR, driver))
            with self.subTest(driver=driver):
                self.assertIn("TourInvestUI.Navegacion.inicializar", src)
                self.assertNotIn('querySelectorAll(".sidebar__link[data-vista]")', src)

    def test_botón_home_cierra_sesión_y_no_navega_a_ciegas(self):
        # El logo ya no apunta a location.href directo: pregunta y limpia sesión.
        for nombre in self.PANELS:
            src = self._html(nombre)
            with self.subTest(panel=nombre):
                self.assertIn("TourInvestUI.SalirAlInicio()", src)
                self.assertNotIn("onclick=\"location.href='../../index.html'\"", src)

    def test_logout_sidebar_limpia_sesion(self):
        for nombre in self.PANELS:
            with self.subTest(panel=nombre):
                self.assertIn("javascript:cerrarSesion()", self._html(nombre))

    # ---- Tema (anti-parpadeo) ----------------------------------------------
    def test_anti_flash_de_tema_en_head(self):
        for nombre in self.PANELS:
            with self.subTest(panel=nombre):
                self.assertIn("ti_tema", self._html(nombre))

    # ---- KPIs dinámicos del analista ---------------------------------------
    def test_kpis_analista_vienen_de_api(self):
        src = self._html("analista.html")
        js = leer(os.path.join(JS_DIR, "dashboard_analista.js"))
        for id_ in ["kpi-empresas", "kpi-reportes", "kpi-activos"]:
            with self.subTest(id=id_):
                self.assertIn(f'id="{id_}"', src, f"analista.html debe tener el id {id_}")
                self.assertIn(f'getElementById("{id_}")', js, "el driver debe leer ese id")
        # Los valores no deben estar fijos en el HTML.
        self.assertNotIn('<div class="value">24</div>', src)
        self.assertIn("cargarKpis()", js)

    # ---- Colombia: departamentos sin atracciones ---------------------------
    def test_colombia_muestra_aviso_sin_atracciones(self):
        js = leer(os.path.join(JS_DIR, "colombia.js"))
        self.assertIn("aún no tiene atracciones turísticas registradas", js)
    def test_index_tiene_mapa_de_rutas(self):
        src = leer(os.path.join(os.path.dirname(FRONTEND), "index.html"))
        # El mapa de rutas esta SOLO en el nav; el index NO tiene la seccion.
        self.assertNotIn('<section id="rutas"', src)
        self.assertNotIn('<h2 class="landing-h2">Mapa de rutas del proyecto</h2>', src)
        # El nav enlaza al mapa de sitio y el detalle va dentro de la nav.
        self.assertIn('href="#rutas"', src, "el nav debe enlazar al mapa de sitio")
        self.assertIn('Mapa del sitio', src)
        idx_nav = src.find('landing-nav__inner')
        idx_detalle = src.find('<details class="landing-mapa">')
        self.assertIsNotNone(idx_detalle)
        self.assertGreater(idx_detalle, idx_nav)

        raiz = os.path.dirname(FRONTEND)
        src = leer(os.path.join(raiz, "index.html"))
        self.assertIn('<details class="landing-mapa">', src)
        self.assertIn('<summary class="landing-mapa__toggle">Mapa del sitio</summary>', src)
        # El resumen va AFTER el <h2>/<p>; al presionar se muestra el contenido
        # (se abre a pedido) pero NO está visible de por sí.
        idx_resumen = src.find('Mapa del sitio</summary>')
        idx_resumen_apertura = src.rfind('<details', 0, idx_resumen)
        self.assertIsNotNone(idx_resumen)
        self.assertIsNotNone(idx_resumen_apertura)

if __name__ == "__main__":
    unittest.main()
    def test_index_tiene_mapa_de_rutas(self):
        src = leer(os.path.join(os.path.dirname(FRONTEND), "index.html"))
        # El mapa de rutas esta SOLO en el nav; el index NO tiene la seccion.
        self.assertNotIn('<section id="rutas"', src)
        self.assertNotIn('<h2 class="landing-h2">Mapa de rutas del proyecto</h2>', src)
        # El nav enlaza al mapa de sitio y el detalle va dentro de la nav.
        self.assertIn('href="#rutas"', src, "el nav debe enlazar al mapa de sitio")
        self.assertIn('Mapa del sitio', src)
        idx_nav = src.find('landing-nav__inner')
        idx_detalle = src.find('<details class="landing-mapa">')
        self.assertIsNotNone(idx_detalle)
        self.assertGreater(idx_detalle, idx_nav)
