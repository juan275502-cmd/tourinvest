# -*- coding: utf-8 -*-
"""
Tests de integracion de la API Flask (backend-python) usando el test-client.

Si Flask no esta instalado en el entorno actual, la suite se marca como skipped
(no falla), de modo que los tests de backend-python/ siempre se puedan lanzar
incluso en maquinas sin las dependencias de requirements.txt.
"""

import unittest

from .helpers import flask_disponible, cargar_modulo

HS = {"Content-Type": "application/json"}

# Snapshots PRESTINOS tomados en tiempo de import (antes de que corra cualquier test).
# Si se toman dentro de setUpClass, otros tests ya habrian mutado la base en memoria.
_DB = cargar_modulo("core.database")
_SNAP_USUARIOS = [dict(u) for u in _DB.USUARIOS_DB]
_SNAP_REPORTES = [dict(r) for r in _DB.REPORTES_DB]


@unittest.skipUnless(flask_disponible(), "Flask no esta instalado; se omiten los tests de la API.")
class TestApi(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        app_mod = cargar_modulo("app")
        cls.client = app_mod.app.test_client()

    def setUp(self):
        # Restaura la base en memoria a su estado original para no depender
        # del usuario/reporte que otros tests hayan insertado.
        _DB.USUARIOS_DB[:] = [dict(u) for u in _SNAP_USUARIOS]
        _DB.REPORTES_DB[:] = [dict(r) for r in _SNAP_REPORTES]

    def _login(self, correo="inversor@tourinvest.com", password="inversor123"):
        return self.client.post("/api/authentication/login", json={
            "correo": correo, "password": password,
        })

    # --- health ---

    def test_health(self):
        r = self.client.get("/api/health")
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.get_json()["ok"])

    # --- authentication ---

    def test_login_ok(self):
        r = self._login()
        self.assertEqual(r.status_code, 200)
        data = r.get_json()
        self.assertTrue(data["ok"])
        self.assertEqual(data["usuario"]["rol"], "Inversionista")

    def test_login_bad_credentials(self):
        r = self._login(password="mala")
        self.assertEqual(r.status_code, 401)
        self.assertFalse(r.get_json()["ok"])

    def test_registro_ok(self):
        r = self.client.post("/api/authentication/registro", json={
            "nombre": "Ana", "apellido": "Lopez", "cedula": "1005005005",
            "correo": "ana.lopez@correo.com", "password": "Clave123",
        })
        self.assertEqual(r.status_code, 201)
        data = r.get_json()
        self.assertTrue(data["ok"])
        self.assertEqual(data["usuario"]["rol"], "Inversionista")
        self.assertEqual(data["usuario"]["estado"], "Activo")

    def test_registro_faltan_campos(self):
        r = self.client.post("/api/authentication/registro", json={"correo": "x@x.com"})
        self.assertEqual(r.status_code, 400)

    def test_recuperar(self):
        r = self.client.post("/api/authentication/recuperar", json={"correo": "inversor@tourinvest.com"})
        self.assertEqual(r.status_code, 200)
        self.assertIn("mensaje", r.get_json())

    # --- investments ---

    def test_portafolio(self):
        r = self.client.get("/api/investments/portafolio")
        self.assertEqual(r.status_code, 200)
        data = r.get_json()
        self.assertTrue(data["ok"])
        self.assertGreater(data["valor_total"], 0)
        self.assertIsInstance(data["posiciones"], list)

    def test_alertas_empresas(self):
        self.assertEqual(self.client.get("/api/investments/alertas").status_code, 200)
        self.assertEqual(self.client.get("/api/investments/empresas").status_code, 200)

    # --- analysis ---

    def test_analysis_endpoints(self):
        self.assertEqual(self.client.get("/api/analysis/empresas").status_code, 200)
        self.assertEqual(self.client.get("/api/analysis/indicadores").status_code, 200)
        self.assertEqual(self.client.get("/api/analysis/reportes").status_code, 200)

    def test_crear_reporte_ok(self):
        r = self.client.post("/api/analysis/reportes", json={
            "titulo": "T1", "empresa": "GHL Hoteles", "fecha": "2026-01-01",
        })
        self.assertEqual(r.status_code, 201)
        self.assertTrue(r.get_json()["ok"])

    def test_crear_reporte_sin_empresa(self):
        r = self.client.post("/api/analysis/reportes", json={"titulo": "T"})
        self.assertEqual(r.status_code, 400)

    # --- dashboard (admin) ---

    def test_dashboard_usuarios(self):
        r = self.client.get("/api/dashboard/usuarios")
        self.assertEqual(r.status_code, 200)
        data = r.get_json()
        self.assertTrue(data["ok"])
        self.assertIsInstance(data["usuarios"], list)

    def test_dashboard_cambiar_estado(self):
        r = self.client.patch("/api/dashboard/usuarios/1/estado", json={"estado": "Inactivo"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.get_json()["usuario"]["estado"], "Inactivo")

    def test_dashboard_estado_invalido(self):
        r = self.client.patch("/api/dashboard/usuarios/1/estado", json={"estado": "raro"})
        self.assertEqual(r.status_code, 400)

    def test_dashboard_roles_y_auditorias(self):
        self.assertEqual(self.client.get("/api/dashboard/roles").status_code, 200)
        self.assertEqual(self.client.get("/api/dashboard/auditorias").status_code, 200)


if __name__ == "__main__":
    unittest.main()