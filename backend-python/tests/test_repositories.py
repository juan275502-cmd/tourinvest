# -*- coding: utf-8 -*-
"""
Tests de las implementaciones de repositorio (backend-python).
Cubren autenticacion (RF01), registro (RF04), inversion (RF07) y analisis (RF10)
sobre las estructuras en memoria de core.database.
"""

import unittest

from .helpers import cargar_modulo

database = cargar_modulo("core.database")
entities = cargar_modulo("features.authentication.domain.entities")
AuthRepo = cargar_modulo("features.authentication.data.repository_impl").UsuarioRepositoryImpl
AnalysisRepo = cargar_modulo("features.analysis.data.repository_impl").AnalysisRepositoryImpl
InvestRepo = cargar_modulo("features.investments.data.repository_impl").InvestmentsRepositoryImpl
DashRepo = cargar_modulo("features.dashboard.data.repository_impl").DashboardRepositoryImpl

# Snapshots para restaurar el estado en memoria entre tests
_SNAP_USUARIOS = [dict(u) for u in database.USUARIOS_DB]
_SNAP_REPORTES = [dict(r) for r in database.REPORTES_DB]


class TestAutenticacion(unittest.TestCase):
    def setUp(self):
        database.USUARIOS_DB[:] = [dict(u) for u in _SNAP_USUARIOS]
        self.repo = AuthRepo()

    def test_login_correcto(self):
        u = self.repo.autenticar("inversor@tourinvest.com", "inversor123")
        self.assertIsNotNone(u)
        self.assertEqual(u.rol, "Inversionista")
        self.assertEqual(u.estado, "Activo")

    def test_login_incorrecto(self):
        self.assertIsNone(self.repo.autenticar("inversor@tourinvest.com", "mala"))

    def test_login_correo_inexistente(self):
        self.assertIsNone(self.repo.autenticar("nadie@tourinvest.com", "x"))

    def test_login_correo_ignora_mayusculas(self):
        u = self.repo.autenticar("INVERSOR@tourinvest.com", "inversor123")
        self.assertIsNotNone(u)


class TestRegistro(unittest.TestCase):
    def setUp(self):
        database.USUARIOS_DB[:] = [dict(u) for u in _SNAP_USUARIOS]
        self.repo = AuthRepo()

    def test_registro_crea_inversionista_activo(self):
        nuevo = self.repo.registrar({
            "nombre": "Ana", "apellido": "Lopez", "cedula": "1005005005",
            "correo": "ana.lopez@correo.com", "password": "Clave123",
        })
        self.assertEqual(nuevo.rol, "Inversionista")
        self.assertEqual(nuevo.estado, "Activo")
        self.assertEqual(nuevo.id, 4)

    def test_registro_correo_duplicado_lanza_error(self):
        with self.assertRaises(ValueError):
            self.repo.registrar({
                "nombre": "X", "apellido": "Y", "cedula": "999",
                "correo": "inversor@tourinvest.com", "password": "z",
            })


class TestAnalisis(unittest.TestCase):
    def setUp(self):
        database.REPORTES_DB[:] = [dict(r) for r in _SNAP_REPORTES]
        self.repo = AnalysisRepo()

    def test_listar_reportes(self):
        reportes = self.repo.obtener_reportes()
        self.assertIsInstance(reportes, list)
        self.assertEqual(len(reportes), 2)

    def test_crear_analisis_appende(self):
        nuevo = self.repo.crear_analisis({"titulo": "T", "empresa": "GHL Hoteles", "fecha": "2026-01-01"})
        self.assertEqual(nuevo["empresa"], "GHL Hoteles")
        self.assertIn(nuevo, database.REPORTES_DB)

    def test_crear_analisis_sin_titulo_usar_default(self):
        nuevo = self.repo.crear_analisis({"empresa": "X"})
        self.assertNotEqual(nuevo["titulo"], "")


class TestInversiones(unittest.TestCase):
    def test_obtener_portafolio(self):
        repo = InvestRepo()
        p = repo.obtener_portafolio()
        self.assertIsInstance(p, list)
        self.assertTrue(all("empresa" in item and "participacion" in item for item in p))

    def test_valor_total_es_positivo(self):
        repo = InvestRepo()
        self.assertGreater(repo.valor_total_portafolio(), 0)

    def test_obtener_alertas_y_empresas(self):
        repo = InvestRepo()
        self.assertIsInstance(repo.obtener_alertas(), list)
        self.assertIsInstance(repo.obtener_empresas(), list)


class TestEntityUsuario(unittest.TestCase):
    def test_to_dict_excluye_password(self):
        usuario = entities.Usuario(id=1, nombre="N", apellido="A", cedula="1", correo="a@a.com", rol="Inversionista")
        d = usuario.to_dict()
        self.assertIn("rol", d)
        self.assertNotIn("password", d)


if __name__ == "__main__":
    unittest.main()
class TestDashboard(unittest.TestCase):
    def setUp(self):
        database.USUARIOS_DB[:] = [dict(u) for u in _SNAP_USUARIOS]
        self.repo = DashRepo()

    def _datos(self, **extra):
        base = {"nombre": "Luis", "apellido": "Torres", "cedula": "1006006006",
                "correo": "luis@tourinvest.com", "password": "Clave123", "rol": "Analista"}
        base.update(extra)
        return base

    def test_listar_no_expone_password(self):
        self.assertTrue(all("password" not in u for u in self.repo.listar_usuarios()))

    def test_crear_usuario_ignora_campos_extra(self):
        nuevo = self.repo.crear_usuario(self._datos(estado="Inactivo", admin=True))
        self.assertEqual(nuevo["estado"], "Activo")
        self.assertNotIn("admin", nuevo)
        self.assertNotIn("password", nuevo)

    def test_crear_usuario_correo_duplicado(self):
        with self.assertRaises(ValueError):
            self.repo.crear_usuario(self._datos(correo="INVERSOR@tourinvest.com"))

    def test_crear_usuario_rol_invalido(self):
        with self.assertRaises(ValueError):
            self.repo.crear_usuario(self._datos(rol="SuperAdmin"))

    def test_cambiar_estado_inexistente_devuelve_none(self):
        self.assertIsNone(self.repo.cambiar_estado(999, "Inactivo"))

    def test_cambiar_estado_invalido(self):
        with self.assertRaises(ValueError):
            self.repo.cambiar_estado(1, "raro")
            