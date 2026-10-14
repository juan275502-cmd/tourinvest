"""
core/database.py
Capa 'core' compartida entre features (ver arquitectura.md -> core / shared).
Simula la base de datos MySQL mencionada en README.md mediante estructuras en
memoria, para que el proyecto pueda ejecutarse sin necesidad de un servidor
MySQL real. Los valores de estado (Activo/Inactivo) y roles
(Administrador/Analista/Inversionista) coinciden con los ENUM de MySQL definidos
en query.sql, de modo que la réplica en memoria es vocabulariamente coherente con
el backend Java + MySQL. En producción, este módulo se reemplazaría por
conexiones mysql-connector-python / SQLAlchemy hacia MySQL.
"""

USUARIOS_DB = [
    {"id": 1, "nombre": "Juan", "apellido": "Pérez", "cedula": "1001001001",
     "correo": "inversor@tourinvest.com", "password": "123456",       "rol": "Inversionista", "estado": "Activo"},
    {"id": 2, "nombre": "María", "apellido": "Gómez", "cedula": "1002002002",
     "correo": "analista@tourinvest.com", "password": "123456",       "rol": "Analista", "estado": "Activo"},
    {"id": 3, "nombre": "Carlos", "apellido": "Ruiz", "cedula": "1003003003",
     "correo": "admin@tourinvest.com", "password": "123456",       "rol": "Administrador", "estado": "Activo"},
]

EMPRESAS_DB = [
    {"id": 1, "nombre": "GHL Hoteles", "sector": "Hotelería"},
    {"id": 2, "nombre": "Decameron", "sector": "Hotelería"},
    {"id": 3, "nombre": "Dann Carlton", "sector": "Hotelería"},
    {"id": 4, "nombre": "Estelar Hoteles", "sector": "Hotelería"},
    {"id": 5, "nombre": "Aviatur", "sector": "Agencia de viajes"},
    {"id": 6, "nombre": "Satena", "sector": "Transporte aéreo"},
    {"id": 7, "nombre": "Parque del Café", "sector": "Turismo"},
    {"id": 8, "nombre": "Masaya Hostels", "sector": "Hospedaje"},
]

PORTAFOLIO_DB = [
    {"empresa": "GHL Hoteles", "participacion": 26, "rendimiento": 4.5},
    {"empresa": "Decameron", "participacion": 20, "rendimiento": 1.8},
    {"empresa": "Parque del Café", "participacion": 18, "rendimiento": -0.6},
    {"empresa": "Estelar Hoteles", "participacion": 15, "rendimiento": 2.1},
    {"empresa": "Dann Carlton", "participacion": 12, "rendimiento": 1.2},
    {"empresa": "Aviatur", "participacion": 9, "rendimiento": -1.2},
]

ALERTAS_DB = [
    {"empresa": "Parque del Café", "descripcion": "Caída de indicador de ocupación en temporada baja", "prioridad": "alta"},
    {"empresa": "Aviatur", "descripcion": "Variación inusual en el precio de las acciones", "prioridad": "media"},
    {"empresa": "Decameron", "descripcion": "Nuevo reporte financiero disponible", "prioridad": "baja"},
]

INDICADORES_DB = [
    {"indicador": "Ocupación hotelera", "empresa": "GHL Hoteles", "valor": 78.4},
    {"indicador": "ROE", "empresa": "Parque del Café", "valor": 14.5},
    {"indicador": "Endeudamiento", "empresa": "Aviatur", "valor": 62},
]

REPORTES_DB = [
    {"titulo": "Análisis de temporada alta en el Caribe", "empresa": "Decameron", "fecha": "2026-06-30"},
    {"titulo": "Evaluación de riesgo de la red hotelera", "empresa": "GHL Hoteles", "fecha": "2026-06-15"},
]

AUDITORIAS_DB = [
    {"fecha": "2026-07-05", "usuario": "María Gómez", "accion": "Creó un nuevo análisis"},
    {"fecha": "2026-07-04", "usuario": "Carlos Ruiz", "accion": "Suspendió al usuario Luis Torres"},
]


def siguiente_id(coleccion):
    return max([item["id"] for item in coleccion], default=0) + 1
