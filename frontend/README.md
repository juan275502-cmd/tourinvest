# TourInvest — Frontend

Frontend de **TourInvest**, plataforma de análisis e inversión del sector turístico colombiano. Incluye una landing page pública, el flujo completo de autenticación (login, registro, recuperación de contraseña) y tres dashboards navegables según el rol del usuario: **Inversionista**, **Analista** y **Administrador**.

> Proyecto construido sin frameworks: HTML5 + CSS3 + JavaScript vanilla (módulos ES), consumiendo un backend Java expuesto en `localhost:8080`.

---

## Índice

- [Estructura del proyecto](#estructura-del-proyecto)
- [Tecnologías](#tecnologías)
- [Páginas](#páginas)
- [Arquitectura del frontend](#arquitectura-del-frontend)
- [Variables de entorno](#variables-de-entorno)
- [Puesta en marcha](#puesta-en-marcha)
- [Modo demo (datos locales)](#modo-demo-datos-locales)
- [Contrato con el backend](#contrato-con-el-backend)
- [Pruebas](#pruebas)
- [Convenciones de código](#convenciones-de-código)
- [Estado actual y mejoras pendientes](#estado-actual-y-mejoras-pendientes)

---

## Estructura del proyecto

El frontend se organiza en **tres capas** (presentación, lógica y datos) desacopladas por contratos explícitos — atributos `data-*`, `id` del DOM y endpoints HTTP — sin frameworks ni bundler obligatorio. A continuación: el árbol de directorios y los diagramas de esa arquitectura.

```
frontend/
├── index.html                     # Landing page pública (hero, nosotros, sectores, contacto)
├── .env.local                     # Variables de entorno (VITE_API_MODE, VITE_API_URL)
│
├── view/                          # Páginas de la aplicación
│   ├── login.html                 # Inicio de sesión
│   ├── registro.html              # Registro de cuentas (rol público → Inversionista)
│   ├── recuperar.html             # Recuperación de contraseña
│   ├── inversionista.html         # Dashboard: portafolio, alertas, noticias, empresas, perfil
│   ├── analista.html              # Dashboard: empresas, indicadores, reportes, recomendaciones, perfil
│   ├── administrador.html         # Dashboard: usuarios, empresas, roles, auditorías, configuración
│   └── usuarios-dao.html          # CRUD de usuarios con JDBC (módulo DAO)
│
├── js/
│   ├── config.js                  # Configuración sin bundler (apiMode · apiUrl)
│   ├── auth.js                    # Login, registro y recuperación (consume /auth/*)
│   ├── dashboard.js               # Driver del dashboard Inversionista
│   ├── dashboard_analista.js      # Driver del dashboard Analista
│   ├── dashboard_administrador.js # Driver del dashboard Administrador
│   ├── usuarios-dao.js            # Driver de la página Usuarios · DAO (CRUD JDBC)
│   ├── localData.js               # Datos semilla para el modo demo (API_MODE=local)
│   └── app.js                     # Helpers de UI compartidos (tabs, modales, toasts)
│
├── css/
│   ├── landing.css                # Estilos de la landing page
│   ├── styles.css                 # Estilos de autenticación y dashboards
│   └── real_theme.css             # Tema (sin referenciar por ningún HTML)
│
├── assets/imgs/                   # Logos e imágenes (AVIF / PNG / JPEG)
│
└── tests/
    └── test_dashboard_wiring.py   # Suite de validación estructural (Python + unittest)
```

### Arquitectura estructural

```
┌─ CAPA 1 · PRESENTACIÓN (HTML + CSS) ──────────────────────────────
│
│  Landing:      index.html                       css/landing.css
│  Auth:         view/login.html                  css/styles.css
│                view/registro.html
│                view/recuperar.html
│  Dashboards:   view/inversionista.html          css/styles.css
│                view/analista.html
│                view/administrador.html
│
│  Contrato con la lógica: onsubmit/onclick · data-vista ·
│  data-vista-panel · data-tab-id · data-open-modal · ids de DOM
│
└──────────────┬────────────────────────────────────────────────────
               │  eventos del usuario
┌──────────────▼─ CAPA 2 · LÓGICA (JavaScript vanilla) ─────────────
│
│  Un driver por página (js/):
│    auth.js                        login · registro · recuperar
│    dashboard.js                   → Inversionista
│    dashboard_analista.js          → Analista
│    dashboard_administrador.js     → Administrador
│  Compartido:
│    app.js                         TourInvestUI: tabs · modales ·
│                                   toasts (window.TourInvestUI)
│
│  Responsabilidades: guard de token (sessionStorage) ·
│  llamarApi() con Authorization: Bearer · navegación de vistas ·
│  manejo de 401 (logout + redirect a login)
│
└──────────────┬────────────────────────────────────────────────────
               │  resolución de datos según VITE_API_MODE
               │
               ├── "local" ──► CAPA 3A · DATOS (demo)
               │               js/localData.js: usuarios semilla en
               │               memoria; getUsers() los devuelve sin
               │               tocar el backend
               │
               └── otro ────► CAPA 3B · DATOS (real)
                               Backend Java (Spring) en VITE_API_URL
                               (http://localhost:8080 por defecto)
                               /auth/* · /empresas · /inversionista/*
                               /analista/* · /admin/*

   TRANSVERSAL · CONFIGURACIÓN Y CALIDAD
   ├── .env.local ······················ VITE_API_MODE · VITE_API_URL
   └── tests/test_dashboard_wiring.py ·· valida el cableado HTML ↔ JS
```

**Flujo de navegación y de datos:**

```
index.html ── "Acceder al Sistema" ──► view/login.html
                                           │
                    POST /auth/login       │
             { token, rol } ◄──────────────┘
                    │
                    │  sessionStorage: tourinvest_token · _nombre ·
                    │  _correo · _rol
                    │
                    │  redirección según RUTA_DASHBOARD[rol]
        ┌───────────┼────────────────┐
        ▼           ▼                ▼
  Administrador   Analista      Inversionista
  administrador   analista      inversionista
      .html         .html          .html
        └───────────┼────────────────┘
                    ▼
     driver dashboard*.js (valida el token)
                    │
                    │  llamarApi() + Bearer <token>
                    ▼
             API backend :8080 ── 401 ──► sessionStorage.clear()
                                          + redirect a login.html
```

**Grafo de dependencias JS** (lo que realmente carga cada página):

```
view/login.html · registro.html · recuperar.html
      └─► js/auth.js ─────────► ./data/localData (*)

view/inversionista.html ─► js/app.js + js/dashboard.js ─────────────┐
view/analista.html       ─► js/app.js + js/dashboard_analista.js ───┼─► ./data/localData (*)
view/administrador.html  ─► js/app.js + js/dashboard_administrador.js ┘

index.html ─► css/landing.css (sin JavaScript)
css/real_theme.css ── (sin referenciar por ningún HTML)

(*) El import apunta a ./data/localData, pero el archivo vive en
    js/localData.js — ver Estado actual y mejoras pendientes.
```

> El detalle de responsabilidades de cada driver y las claves de sesión están en [Arquitectura del frontend](#arquitectura-del-frontend).

## Tecnologías

| Capa | Tecnología |
|------|------------|
| Marcado | HTML5 semántico |
| Estilos | CSS3 (nomenclatura BEM-like, sin preprocesador) |
| Lógica | JavaScript vanilla (módulos ES) |
| Entorno de variables | Convención de Vite (`import.meta.env.VITE_*`) |
| Backend esperado | API Java (Spring) en `http://localhost:8080` |
| Pruebas | Python 3 + `unittest` (validación estructural HTML/JS) |

## Páginas

| Ruta | Descripción | Script asociado |
|------|-------------|-----------------|
| `index.html` | Landing pública: hero, nosotros, misión/visión, valores, sectores, contacto | — |
| `view/login.html` | Formulario de inicio de sesión | `js/auth.js` |
| `view/registro.html` | Registro de cuenta (rol público → siempre Inversionista) | `js/auth.js` |
| `view/recuperar.html` | Solicitud de recuperación de contraseña | `js/auth.js` |
| `view/inversionista.html` | Dashboard Inversionista | `js/dashboard.js` |
| `view/analista.html` | Dashboard Analista | `js/dashboard_analista.js` |
| `view/administrador.html` | Dashboard Administrador | `js/dashboard_administrador.js` |
| `view/usuarios-dao.html` | CRUD de usuarios con JDBC puro (módulo DAO, paso 6 de la guía) | `js/usuarios-dao.js` |

## Arquitectura del frontend

El proyecto sigue un patrón de **un driver JS por página**:

1. **`auth.js`** maneja los tres formularios de autenticación. Al hacer login, guarda la sesión en `sessionStorage` y redirige al dashboard según el rol que devuelve el backend:

   | Rol devuelto | Destino |
   |--------------|---------|
   | `Administrador` | `administrador.html` |
   | `Analista` | `analista.html` |
   | `Inversionista` | `inversionista.html` |

2. **Cada dashboard** tiene su driver (`dashboard*.js`) que:
   - Protege la ruta: si no hay token en `sessionStorage`, redirige a `login.html`.
   - Realiza las peticiones al backend con `Authorization: Bearer <token>` vía el helper `llamarApi()`.
   - Ante un `401`, limpia la sesión y devuelve al login.
   - Gestiona la navegación lateral entre vistas con los atributos `data-vista` / `data-vista-panel`.

3. **`app.js`** expone helpers globales bajo `window.TourInvestUI` (tabs, modales con backdrop/ESC, toasts) reutilizables por cualquier página.

Claves de sesión (`sessionStorage`):

| Clave | Contenido |
|-------|-----------|
| `tourinvest_token` | Token emitido por el backend en el login |
| `tourinvest_nombre` | Primer nombre del usuario |
| `tourinvest_correo` | Correo del usuario |
| `tourinvest_rol` | Rol del usuario |

## Variables de entorno

El código lee la configuración con la convención de Vite (`import.meta.env`):

| Variable | Valores | Descripción |
|----------|---------|-------------|
| `VITE_API_MODE` | `local` \| otro | En `local`, los drivers devuelven datos de demostración sin llamar al backend |
| `VITE_API_URL` | URL base | URL base de la API (ej. `http://localhost:8080`) |

Ejemplo (`.env.local` actual del proyecto):

```env
VITE_API_MODE=local
VITE_API_URL=http://localhost:8080
```

> ⚙️ **Nota:** el frontend ya no depende de `import.meta.env`: la configuración vive en **`js/config.js`** (`window.TourInvestConfig`), que debe cargarse antes que los drivers en cada página. Cambia `apiMode` entre `"api"` (backend real) y `"local"` (demo con `localData.js`).

## Puesta en marcha

### Requisitos

- Un navegador moderno.
- Python 3 (solo para ejecutar la suite de pruebas).
- Node.js + Vite **recomendado**: el código usa `import.meta.env`, que solo se resuelve a través de un bundler. Sin él, esas variables no estarán definidas en el navegador.

### Servir la aplicación

Con cualquier servidor estático apuntando a la **raíz del proyecto** (importante: sirve la raíz, no `view/`, para que las rutas relativas `js/`, `css/` y `assets/` resuelvan bien):

```bash
# Opción 1: Python
py -m http.server 5500

# Opción 2: Node
npx serve .
```

Luego abre `http://localhost:5500` (la landing) y navega a `view/login.html`.

Con Vite (recomendado para que `import.meta.env` funcione):

```bash
npx vite .
```

> ⚠️ **Nota:** el proyecto aún no incluye `package.json` ni configuración de Vite; ver [Estado actual](#estado-actual-y-mejoras-pendientes).

## Modo demo (datos locales)

Con `VITE_API_MODE=local`, las funciones `getUsers()` devuelven los usuarios semilla de `js/localData.js`, sin tocar el backend:

| Nombre | Correo | Rol | Contraseña |
|--------|--------|-----|------------|
| Juan | `carlos@tourinvest.com` | inversionista | `123456` |
| Marlen | `laura@tourinvest.com` | analista | `123456` |
| Enrique | `nuevo@tourinvest.com` | admin | `123456` |

> Los datos de demo son solo para desarrollo: contienen contraseñas en texto plano y no deben usarse en producción.

## Contrato con el backend

Base URL: `VITE_API_URL` (por defecto `http://localhost:8080`). Las peticiones de los dashboards viajan con `Authorization: Bearer <token>`; un `401` cierra la sesión y regresa al login.

### Autenticación (`js/auth.js`)

| Método | Endpoint | Cuerpo / Respuesta |
|--------|----------|--------------------|
| `POST` | `/auth/login` | Envía `{ correo, contrasena }`. Responde `{ token, nombre1, correo, rol }` |
| `POST` | `/auth/registro` | Envía `{ nombre1, apellido1, cedula, fechaNacimiento, correo, contrasena, confirmarContrasena }`. En error de validación responde `{ errores: { campo: mensaje } }` |
| `POST` | `/auth/recuperar` | Envía `{ correo }`. Responde `{ mensaje }` |

### Inversionista (`js/dashboard.js`)

| Método | Endpoint | Uso |
|--------|----------|-----|
| `GET` | `/inversionista/resumen` | Resumen del portafolio |
| `GET` | `/empresas` | Listado de empresas |
| `GET` | `/empresas/{id}` | Detalle de empresa |
| `POST` | `/inversionista/portafolio/inversiones` | Crear inversión `{ idAccion, cantidad }` |
| `GET` / `POST` | `/inversionista/alertas` | Listar / crear alertas `{ idAccion, precioObjetivo }` |
| `PATCH` | `/inversionista/alertas/{id}/cancelar` | Cancelar alerta |

### Analista (`js/dashboard_analista.js`)

| Método | Endpoint | Uso |
|--------|----------|-----|
| `GET` | `/empresas` | Listado de empresas (selector de reportes) |
| `POST` | `/analista/indicadores/liquidez` | Calcular razón de liquidez `{ activoCorriente, pasivoCorriente }` |
| `GET` / `POST` | `/analista/reportes` | Listar / crear reportes `{ idEmpresa, titulo, descripcion }` |

### Administrador (`js/dashboard_administrador.js`)

| Método | Endpoint | Uso |
|--------|----------|-----|
| `GET` | `/admin/usuarios` | Listado de usuarios |
| `PATCH` | `/admin/usuarios/{id}/suspender` | Suspender usuario |
| `PATCH` | `/admin/usuarios/{id}/activar` | Reactivar usuario |
| `GET` | `/empresas` · `GET /empresas/{id}` | Listado y detalle de empresas |
| `DELETE` | `/empresas/{id}` | Eliminar empresa |

### Módulo DAO — CRUD con JDBC puro (paso 6 de la guía)

Implementado por `UsuarioDAO.java` + `UsuarioDaoController.java` en `backend-servlets`
(`com.tourinvest.backend.dao`), sobre la tabla `usuarios` de MySQL con
`PreparedStatement`. Lo consume la pantalla `view/usuarios-dao.html`
(enlazada desde el panel de administrador). Contraseñas con BCrypt para
coherencia con el login de Spring Security.

| Método | Endpoint | DAO involucrado |
|--------|----------|-----------------|
| `GET` | `/dao/usuarios` | `listarUsuarios()` |
| `GET` | `/dao/usuarios/{id}` | `buscarPorId(id)` |
| `POST` | `/dao/usuarios` | `crearUsuario(usuario)` |
| `PUT` | `/dao/usuarios/{id}` | `actualizarUsuario(usuario)` (contraseña vacía = conservar) |
| `DELETE` | `/dao/usuarios/{id}` | `eliminarUsuario(id)` |
| `PATCH` | `/dao/usuarios/{id}/suspender` · `/activar` | `cambiarEstado(id, activo)` |

> Todos exigen rol **Administrador**: `SecurityConfig` añade `/dao/** → hasRole("ADMINISTRADOR")`.

## Pruebas

La carpeta `tests/` contiene una suite de `unittest` que valida la integración HTML ↔ JS (wiring):

- Cada dashboard carga su driver correcto.
- Todos los `getElementById` usados por cada driver existen en su HTML.
- Cada dashboard expone sus vistas (`data-vista-panel`).
- Los formularios contienen los campos `name` que el JS lee.
- No quedan residuos del vocabulario antiguo (`.campo__`).
- Las páginas de autenticación cargan `js/auth.js` y tienen los campos esperados.

```bash
py -m unittest discover -s tests -v
```

## Convenciones de código

- **Idioma:** código, comentarios, nombres de variables y mensajes de UI en español.
- **CSS:** nomenclatura tipo BEM (`landing-hero__title`, `sidebar__link--activo`).
- **JS:** funciones nombradas en español (`iniciarSesion`, `cargarEmpresas`), sin dependencias externas.
- **HTML ↔ JS:** el cableado se hace con atributos `data-*` (`data-vista`, `data-vista-panel`, `data-tab-id`, `data-open-modal`) e `id` estables.
- **Validación:** errores de campo con clases `.field--error` y mensajes `.field__mensaje-error`; errores globales con `#mensaje-global`.

## Estado actual y mejoras pendientes

- [x] **Ruta de import rota → RESUELTA:** `localData.js` expone `window.LocalData` y los drivers lo leen como global (ya no hay `import` de `./data/localData`).
- [x] **Variables de entorno sin bundler → RESUELTAS:** `js/config.js` (`window.TourInvestConfig`) sustituye a `import.meta.env`; los drivers ya no usan `import`/`export` y funcionan como scripts clásicos.
- [ ] **Suite de pruebas desactualizada:** `tests/test_dashboard_wiring.py` busca los HTML en la raíz del proyecto, pero hoy viven en `view/`, y espera `src="js/..."` mientras las páginas usan `src="../js/..."`. Los 8 tests fallan actualmente.
- [ ] El caché de empresas (`empresasCache`) solo se recarga cuando está vacío.
- [ ] `formatearMoneda` usa USD; evaluar usar COP para el contexto colombiano.

---

© 2025 TourInvest S.A.S. · Bogotá, Colombia



