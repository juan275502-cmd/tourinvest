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
            "inversionista.html": ["portafolio", "alertas", "empresas", "perfil"],
            "analista.html": ["empresas", "indicadores", "reportes", "recomendaciones", "perfil"],
            "administrador.html": ["usuarios", "roles", "auditorias", "configuracion"],
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


if __name__ == "__main__":
    unittest.main()