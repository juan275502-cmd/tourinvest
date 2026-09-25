"""
Data / Repository Implementation
Implementa UsuarioRepository usando la 'base de datos' en memoria de core.database
(ver arquitectura.md -> Data: Models, Datasources, Repository Implementations).
"""

from core.database import USUARIOS_DB, siguiente_id
from features.authentication.domain.entities import Usuario
from features.authentication.domain.repository import UsuarioRepository


class UsuarioRepositoryImpl(UsuarioRepository):

    def autenticar(self, correo: str, password: str):
        """
        # Verifica credenciales contra la base de datos.
        # Compara correo (sin distinguir mayúsculas/minúsculas) y password exacto.
        # Retorna un objeto Usuario si son válidas, o None si no.
        """
        for u in USUARIOS_DB:
            if u["correo"].lower() == correo.lower() and u["password"] == password:
                return Usuario(
                    id=u["id"], nombre=u["nombre"], apellido=u["apellido"],
                    cedula=u["cedula"], correo=u["correo"], rol=u["rol"], estado=u["estado"]
                )
        # Ningún usuario coincidió con correo + password
        return None

    def registrar(self, datos: dict):
        correo_existente = any(u["correo"].lower() == datos["correo"].lower() for u in USUARIOS_DB)
        if correo_existente:
            raise ValueError("El correo ya está registrado.")

        nuevo = {
            "id": siguiente_id(USUARIOS_DB),
            "nombre": datos["nombre"],
            "apellido": datos["apellido"],
            "cedula": datos["cedula"],
            "correo": datos["correo"],
            "password": datos["password"],
            "rol": "Inversionista",  # RF04: el registro público crea Inversionista (vocabulary coherente con MySQL)
            "estado": "Activo",
        }
        USUARIOS_DB.append(nuevo)
        return Usuario(
            id=nuevo["id"], nombre=nuevo["nombre"], apellido=nuevo["apellido"],
            cedula=nuevo["cedula"], correo=nuevo["correo"], rol=nuevo["rol"], estado=nuevo["estado"]
        )
