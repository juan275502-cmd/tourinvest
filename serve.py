#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Servidor estático para el sitio de TourInvest.

Levanta un `http.server` desde la RAÍZ del proyecto (donde vive index.html),
igual que `python -m http.server`, pero con un filtro de seguridad: bloquea la
descarga de archivos/carpetas sensibles que no deben servirse por HTTP
(.env con secretos, .git, backend-python/, backend-servlets/, query.sql, .venv,
etc.).

Lo usan `setup.sh` (Linux) y `setup.ps1` (Windows) en `run-frontend` / `run`,
para no reimplementar el servidor en cada sistema operativo.

Uso (mismos argumentos que `python -m http.server`):

    python serve.py [PUERTO] [--bind DIRECCION]

Ejemplos:
    python serve.py 80 --bind 127.0.0.1
    python serve.py 8081
"""

import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


# Nombres de primer nivel (junto a index.html) que NUNCA se sirven por HTTP.
# Se comparan contra el primer segmento de la ruta solicitada.
RUTAS_BLOQUEADAS = frozenset({
    ".env", ".git", ".github", ".vscode", ".venv", ".idea",
    ".postman", "logs",
    "backend-python", "backend-servlets",
    "query.sql", "sql", "postman",
    "requirements.txt", "serve.py",
    "setup.ps1", "setup.sh", "setup.cmd",
    "tourinvest_postman_collection.json", "tourinvest_postman_environment.json",
    "TODO.md",
})

# Extensiones que jamás se sirven (código fuente, config, datos, claves).
EXTENSIONES_BLOQUEADAS = frozenset({
    ".py", ".java", ".class", ".jar", ".war",
    ".sql", ".env", ".properties", ".yml", ".yaml", ".ini", ".cfg", ".toml",
    ".md", ".pem", ".key", ".pfx", ".jks",
    ".log", ".bak", ".tmp", ".pyc",
})


class HandlerTourInvest(SimpleHTTPRequestHandler):
    """Sirve la raíz del proyecto, pero deniega lo sensible."""

    def _primer_segmento(self):
        # self.path -> "/backend-python/app.py" ; nos quedamos con "backend-python".
        ruta = self.path.split("?", 1)[0].split("#", 1)[0]
        segmento = ruta.strip("/").split("/", 1)[0]
        return segmento

    def _extension(self):
        import posixpath
        nombre = posixpath.basename(self.path.split("?", 1)[0].split("#", 1)[0])
        _, ext = os.path.splitext(nombre)
        return ext.lower()

    def _bloqueada(self):
        if self._primer_segmento() in RUTAS_BLOQUEADAS:
            return True
        if self._extension() in EXTENSIONES_BLOQUEADAS:
            return True
        return False

    def _denegar(self):
        self.send_error(403, "Forbidden")

    def end_headers(self):
        # Anti-caché: el navegador SIEMPRE debe traer la última versión de los
        # HTML/JS/CSS (evita que tras un cambio "no se vea nada" por caché).
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        self.send_header("Expires", "0")
        super().end_headers()

    def do_GET(self):
        if self._bloqueada():
            self._denegar()
            return
        super().do_GET()

    def do_HEAD(self):
        if self._bloqueada():
            self._denegar()
            return
        super().do_HEAD()


def main():
    argv = sys.argv[1:]
    puerto = "8000"
    bind = "127.0.0.1"

    # Acepta "serve.py 8080 --bind 0.0.0.0" sin depender de argparse, para
    # mantener la compatibilidad total con los flags de `http.server`.
    i = 0
    while i < len(argv):
        a = argv[i]
        if a in ("--bind", "-b") and i + 1 < len(argv):
            bind = argv[i + 1]
            i += 2
            continue
        if a.startswith("--bind="):
            bind = a.split("=", 1)[1]
            i += 1
            continue
        if a.isdigit():
            puerto = a
        i += 1

    # Sirve siempre desde la carpeta de este archivo (la raíz del proyecto),
    # independientemente del directorio actual con el que se invoque.
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    servidor = ThreadingHTTPServer((bind, int(puerto)), HandlerTourInvest)
    print(f"TourInvest servido en http://{bind}:{puerto}  (rutas sensibles bloqueadas)")
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        servidor.server_close()


if __name__ == "__main__":
    main()
