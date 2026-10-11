from core.database import INDICADORES_DB, REPORTES_DB, EMPRESAS_DB


class AnalysisRepositoryImpl:

    def obtener_indicadores(self):
        return INDICADORES_DB

    def obtener_reportes(self):
        return REPORTES_DB

    def obtener_empresas(self):
        return EMPRESAS_DB

    def crear_analisis(self, datos: dict):
        """RF10 - Crear análisis: genera un nuevo reporte para una empresa."""
        nuevo_reporte = {
            "titulo": datos.get("titulo", "Análisis sin título"),
            "empresa": datos.get("empresa", ""),
            "fecha": datos.get("fecha", ""),
        }
        REPORTES_DB.append(nuevo_reporte)
        return nuevo_reporte
