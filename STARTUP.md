# STARTUP — cómo arrancar TourInvest

El proyecto se lanza con **un solo script por sistema operativo**. Cada uno es
independiente y documenta su propio sistema: no mezcles comandos de uno con el otro.

| Acción | 🪟 Windows | 🐧 Linux |
|---|---|---|
| Diagnóstico | `.\setup.ps1 check` | `./setup.sh check` |
| Crear la BD | `.\setup.ps1 seed` | `./setup.sh seed` |
| **Proyecto completo** | **`.\setup.ps1 run`** | **`./setup.sh run`** |
| Solo la API `:8080` | `.\setup.ps1 run-backend` | `./setup.sh run-backend` |
| Solo el sitio (puerto 80) | `.\setup.ps1 run-frontend` | `./setup.sh run-frontend` |
| Ayuda | `.\setup.ps1 help` | `./setup.sh help` |

En Windows también vale `.\setup.cmd run` (doble clic) o el lanzador
`.\iniciar-todo.cmd`.

## La URL no muestra puerto

El sitio se sirve en el **puerto 80**, que es el puerto por defecto de HTTP, así
que el navegador muestra la dirección pelada, como si fuera un dominio real:

| | Dirección en el navegador |
|---|---|
| Portada | **http://www.tourinvest.com** |sudo 
| Login | **http://www.tourinvest.com** |
| API | http://www.tourinvest.com:8080 (esta sí lleva puerto: es la API) |

> En Linux el puerto 80 es privilegiado: la primera vez el script te dirá cómo
> darle permiso de una sola vez:
> `sudo setcap 'cap_net_bind_service=+ep' $(readlink -f $(which python3))`
> Si prefieres no usar `sudo`, pon `PUERTO_WEB=8081` en `.env` (y entonces la
> URL volverá a llevar `:8081`).

> En CORS el origen también va **sin puerto**: `http://www.tourinvest.com`
> (nunca `http://www.tourinvest.com:80`). Ya está configurado en
> `CORS_ALLOWED_ORIGINS`.

## Arranque rápido

```powershell
# 🪟 Windows (PowerShell)
.\setup.ps1 check
.\setup.ps1 run
```
```bash
# 🐧 Linux
sudo systemctl start mysql     # el servidor MySQL debe estar arriba
./setup.sh check
./setup.sh run
```

Ambos abren el navegador en **http://www.tourinvest.com**
y levantan la API en **http://www.tourinvest.com:8080**. Con un `Ctrl+C` (o
cerrando la ventana / la terminal) se detienen los dos servidores.

Usuarios de prueba (clave `123456`): `kike@tourinvest.com` (Administrador),
`marlen@tourinvest.com` (Analista), `juan@tourinvest.com` (Inversionista).

## Convención de hostname

- **Dirección real: siempre `127.0.0.1`**, nunca `localhost` (en IPv6 resuelve a
  `::1` y rompe JDBC/CORS de forma intermitente).
- **Nombre visible: `www.tourinvest.com`**, mapeado a `127.0.0.1` en el archivo
  de hosts del sistema:

```powershell
# 🪟 Windows (PowerShell como administrador)
Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "127.0.0.1 www.tourinvest.com"
```
```bash
# 🐧 Linux
echo "127.0.0.1 www.tourinvest.com" | sudo tee -a /etc/hosts
```

Los logs del arranque completo quedan en `logs/backend.log` y
`logs/frontend.log`. La contraseña de MySQL se guarda en `.env` (ignorado por
git), nunca en `application.properties`.
