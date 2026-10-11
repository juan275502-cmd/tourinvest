# Tests de backend-python (réplica Python)

Cobertura:
- `test_repositories.py` — 19 tests unitarios de las implementaciones en memoria
  (autenticación RF01, registro RF04, inversión RF07, análisis RF10, entidad Usuario).
- `test_api.py` — 15 tests de integración sobre el Flask `test_client`
  (health, auth, inversiones, análisis, dashboard/admin).

## Requisitos
- Python 3.10+ (stdlib `unittest`).
- Para los tests de API hace falta **Flask** (`pip install -r requirements.txt`).
  Si Flask no está instalado, los 15 de API se marcan como `skipped` (la suite no
  rompe) y los 13 de repositorio siguen corriendo.

## Cómo ejecutar
```bash
# Desde la raíz del proyecto
python -m unittest discover -s backend-python/tests -t backend-python -v

# O con pytest (si está instalado)
pytest backend-python/tests -q
```

> Nota: los snapshots de la base en memoria se toman en tiempo de import para
> aislar cada test; no dependen del orden de ejecución ni de otros módulos.