from core.database import USUARIOS_DB, AUDITORIAS_DB, siguiente_id

ROLES = [
    {"rol": "Administrador", "permisos": "Gestionar usuarios, roles y auditorías"},
    {"rol": "Analista", "permisos": "Gestionar empresas, crear análisis, generar reportes"},
    {"rol": "Inversionista", "permisos": "Consultar portafolio, alertas y noticias"},
]
ESTADOS = ("Activo", "Inactivo")


def _publico(usuario: dict) -> dict:
    return {k: v for k, v in usuario.items() if k != "password"}


class DashboardRepositoryImpl:

    def listar_usuarios(self):
        return [_publico(u) for u in USUARIOS_DB]

    def crear_usuario(self, datos: dict):
        """RF13 - Gestionar usuarios. Solo se copian los campos permitidos."""
        if datos["rol"] not in [r["rol"] for r in ROLES]:
            raise ValueError("Rol inválido.")
        if any(u["correo"].lower() == datos["correo"].lower() for u in USUARIOS_DB):
            raise ValueError("El correo ya está registrado.")

        nuevo = {
            "id": siguiente_id(USUARIOS_DB),
            "nombre": datos["nombre"],
            "apellido": datos["apellido"],
            "cedula": datos["cedula"],
            "correo": datos["correo"],
            "password": datos["password"],
            "rol": datos["rol"],
            "estado": "Activo",
        }
        USUARIOS_DB.append(nuevo)
        return _publico(nuevo)

    def cambiar_estado(self, usuario_id: int, estado: str):
        """RF13 - Suspender / activar. Devuelve None si no existe."""
        if estado not in ESTADOS:
            raise ValueError("Estado inválido.")
        for u in USUARIOS_DB:
            if u["id"] == usuario_id:
                u["estado"] = estado
                return _publico(u)
        return None

    def listar_roles(self):
        return ROLES

    def listar_auditorias(self):
        return AUDITORIAS_DB