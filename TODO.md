# TODO — Hallazgos de incoherencias, rutas rotas, archivos faltantes y duplicidad

> Análisis integral del repositorio `tourinvest` (frontend HTML/CSS/JS, backend Python Flask y backend Java Spring Boot + Clean Architecture).
> Cada hallazgo incluye: evidencia, impacto, ubicación y solución sugerida. Los checkboxes `[ ]` indican corrección pendiente.
> Prioridades: **CRÍTICO** > **ALTO** > **MEDIO** > **BAJO**.

---

## 📊 Resumen de hallazgos

| # | Categoría | Hallazgo | Prioridad | Archivo(s) clave |
|---|-----------|----------|-----------|------------------|
| A1 | Archivo faltante / mal ubicado | `application.properties` contiene código Java (clase main) en vez de propiedades | CRÍTICO | `backend-servlets/src/main/resources/application.properties` |
| A2 | Archivo faltante | Falta `TourinvestBackendApplication.java` (clase main de Spring Boot) | CRÍTICO | `backend-servlets/src/main/java/com/tourinvest/backend/` |
| B1 | Duplicidad de pila | Dos backends (Python:5000 y Java:8080) con contratos incompatibles | ALTO | `backend-python/`, `backend-servlets/.../backend/` |
| B2 | Incoherencia de vocabulario | 3+ vocabularios de estado/rol duplicados entre capas | MEDIO | varios (ver detalle) |
| C1 | Ruta rota | `location.href='index.html'` desde `frontend/` apunta a `frontend/index.html` (inexistente) | ALTO | `frontend/inversionista.html`, `analista.html`, `administrador.html`, `login.html` |
| C2 | Ruta rota | `auth.js` redirige a `dashboard-*.html` (no existen) | ALTO | `frontend/js/auth.js` |
| D1 | Código muerto / duplicidad | 4 archivos JS huérfanos no son cargados por ningún HTML | ALTO | `frontend/js/auth.js`, `dashboard.js`, `dashboard_administrador.js`, `dashboard_analista.js` |
| D2 | Incoherencia | Los JS huérfanos usan selectores que no existen en los HTML reales | MEDIO | `frontend/js/dashboard*.js` |
| E1 | Error de sintaxis | `auth.js` tiene un bloque suelto (función `registrar` sin declaración) | ALTO | `frontend/js/auth.js` (l.124) |
| E2 | Documentación incorrecta | README: `javac -d out $(find ...)` compila fuentes Spring sin classpath | MEDIO | `README.md` (l.82) |
| F1 | Drift documental | `estructura` desactualizado (habla de `config/`, main como `.java`) | BAJO | `backend-servlets/estructura` |
| F2 | Funcionalidad incompleta | `recuperar.html` no consume la API (`/recuperar`) disponible | MEDIO | `frontend/recuperar.html` |
| F3 | Contrato API inconsitente | `auth.js` espera JSON en registro, pero `AuthController.registrar` devuelve texto plano (correo) | MEDIO | `frontend/js/auth.js`, `backend-servlets/.../controller/AuthController.java` |

---

## ✅ Cambios aplicados (sesión actual)

> Correcciones **no destructivas** aplicadas y validadas. Los checkboxes `[x]` de la sección siguiente marcan lo resuelto.

### 🆕 Estado 2026-09-28 — "listo para producción + pruebas en Postman"

**Problema reportado:** `./setup.sh run-backend` fallaba con
`Communications link failure` / `Conexión rehusada`.

**Diagnóstico:** no era un bug de código sino que **MySQL no respondía en
`localhost:3306`**, y el proyecto no ayudaba a detectarlo: la contraseña estaba
hardcodeada en `application.properties`, no había chequeo previo, y el seed
corrompía los datos de prueba.

| # | Hallazgo | Prioridad | Estado | Acción aplicada |
|---|---|---|---|---|
| **G1** | Arranque con BD inaccesible no diagnosticable | CRÍTICO | ✅ | `check` y `run-backend` verifican **puerto TCP + autenticación + existencia de la BD** antes de arrancar, en `setup.sh` **y** `setup.ps1`, con el comando exacto de solución por SO. |
| **G2** | Contraseña MySQL y clave JWT **commiteadas en git** | CRÍTICO | ✅ | `application.properties` usa placeholders `${DB_PASSWORD:}` / `${JWT_SECRET:…}`. Nueva `.env.example` + `.env` en `.gitignore`. `setup.ps1` ya no escribe secretos en el properties. |
| **G3** | Sin configuración de producción | ALTO | ✅ | Nuevo `application-prod.properties` + `PasswordEncoderConfig.JwtConfigValidator` (**@Profile("prod")**) que **rechaza arrancar** con secretos de ejemplo, vacíos o JWT < 32 bytes. |
| **G4** | 10 tests en rojo (`mvn test` fallaba) | ALTO | ✅ | Causa raíz: con `addFilters=false` el post-processor `user(...)` no puebla el `SecurityContextHolder`, así que `@AuthenticationPrincipal Usuario` llegaba `null`. Nuevo helper `AutenticadoComo`. **81/81 tests verdes.** |
| **G5** | `GET /analista/reportes` devolvía **HTTP 500** | ALTO | ✅ | `LazyInitializationException` latente: el DTO mapea `empresa`/`usuario` (LAZY) fuera de sesión. Resuelto con `findAllConEmpresaYUsuario()` (fetch join) tras activar `open-in-view=false`. |
| **G6** | Token inválido/expirado provocaba 500 | MEDIO | ✅ | `JwtAuthFilter` captura la excepción y deja la petición sin autenticar → 401 uniforme. `authenticationEntryPoint`/`accessDeniedHandler` devuelven **JSON** en 401/403. |
| **G7** | Sin endpoint de salud | MEDIO | ✅ | `GET /api/health` (público) → `200 {"estado":"UP","baseDatos":"UP"}` o `503`. Smoke test en Postman sin token. |
| **G8** | Colección Postman **desalineada de la API real** | ALTO | ✅ | Reescrita (28 peticiones / 44 asserts, verificada con Newman). Tenía 3 endpoints inexistentes, cuerpos con campos equivocados y **roles de login invertidos**. Nuevo `tourinvest_postman_environment.json`. |
| **G9** | `query.sql` **destruía el seed** | ALTO | ✅ | El bloque de verificación renombraba el admin (`kike@`→`kike@`) y borraba alerta/reporte. Movido a `sql/pruebas-escritura.sql` (opt-in). |
| **G10** | CORS con orígenes hardcodeados | MEDIO | ✅ | `app.cors.allowed-origins` configurable por variable de entorno (`CORS_ALLOWED_ORIGINS`). |
| **G11** | Rutas rotas en el frontend | ALTO | ✅ | `view/analista.html` cargaba `js/app.js` en vez de `../js/app.js` (scripts rotos). Botones "home" de `administrador`/`inversionista` apuntaban a un `index.html` inexistente. |
| **G12** | Documentación de arranque dispersa e inconsistente | MEDIO | ✅ | `setup.ps1` (solo Windows) y `setup.sh` (solo Linux), cada uno con sus propios comandos de arranque y documentacion de su sistema; `STARTUP.md` resume la tabla de equivalencias. |
| **G13** | CORS no exponía `Content-Length` | BAJO | ✅ | `setExposedHeaders` para que `fetch` lea el tamaño de la respuesta. |
| **G14** | `localhost` vs `127.0.0.1` ambiguo + el dominio real apuntaba a Internet | ALTO | ✅ | Convención única: **`127.0.0.1`** como dirección real y **`www.tourinvest.com`** como nombre visible (mapeado en el archivo de hosts). `check`/`run-backend` validan el mapeo. Ver abajo. |
| **G15** | `run-backend` no pedía la contraseña de MySQL | CRÍTICO | ✅ | Causa del error reportado `Access denied ... (using password: NO)`: `seed` la pedía pero `run-backend` no. Ahora la pide **una sola vez**, la **valida** contra MySQL antes de guardarla y la persiste en `.env`. Clave incorrecta → mensaje claro y no arranca. |
| **G16** | `DB_URL` con host/puerto/base fijos ignoraba `DB_HOST`/`DB_PORT`/`DB_NAME` | ALTO | ✅ | `spring.datasource.url` era un valor único con `127.0.0.1:3306` hardcodeados, así que Spring conectaba siempre al puerto por defecto aunque `.env` dijera otro. Ahora se **compone** desde esas variables (y `DB_URL` permite override completo). |
| **G17** | Roles de los usuarios semilla **invertidos** en docs y Postman | ALTO | ✅ | `query.sql` (editado por el usuario) define `kike`=Administrador, `marlen`=Analista, `juan`=Inversionista. Documentación y los 3 logins de Postman usan ahora esos mismos roles (verificado contra la BD real y con Newman). |
| **G18** | `sed -i` fallaba al guardar en `.env` (permisos) | MEDIO | ✅ | `guardar_en_env` reescribe con temporal + `awk` en vez de `sed -i`, que fallaba con «Operation not permitted» y dejaba claves sin actualizar en silencio. |
| **G19** | Hashes del seed con **BCrypt coste 4** (inseguros) y **compartidos** entre los 3 usuarios | ALTO | ✅ | El seed usaba `$2b$04$…` (coste 4, ~14x más débil que el 10 de `PasswordEncoderConfig`) y el **mismo** hash para las 3 cuentas, lo que permite comparar usuarios entre sí. Regenerados 3 hashes de coste 10, uno por usuario. Verificado: login OK y clave incorrecta → 401. |
| **G20** | Login se quedaba en "Verificando..." sin poder reintentar | MEDIO | ✅ | El `catch` podía lanzar si faltaba `#mensaje-global`, y el `finally` no llegaba a restaurarse; además el fallback de redirección apuntaba a `dashboard-inversionista.html`, un archivo inexistente. Corregidos: `mostrarMensajeGlobal` es a prueba de nulos, `DASHBOARD_POR_DEFECTO` apunta a `inversionista.html` y el `finally` siempre restaura el botón. |
| **G21** | El backend tardaba mucho en arrancar (~35s) | MEDIO | ✅ | Medido: ~14s de overhead de Maven + ~20s de Spring. Añadido `spring.main.lazy-initialization` (**20.4s → 18.4s**), `banner-mode=off`, `defer-datasource-initialization` y pool Hikari pequeño. Nuevo comando **`run-backend-fast`** (empaqueta el JAR y arranca con `java -jar`, saltándose Maven). `prod` mantiene arranque *fail-fast*. Ver `setup.ps1 run-backend-fast`. |
| **G22** | No existía el perfil de usuario (solo lectura y sin API) | ALTO | ✅ | `PerfilController` (`GET /perfil`, `PUT /perfil`, `PATCH /perfil/contrasena`) + `PerfilService` + `PerfilDTO`. Disponible para los **3 roles** (`/perfil/**` → `authenticated()`). El usuario sale del **token JWT**, nunca de un parámetro: es imposible editar el perfil de otro. Rol/estado/contraseña NO son editables aquí. |
| **G23** | El panel de Administrador solo tenía Suspender/Activar (sin CRUD) | ALTO | ✅ | `POST /admin/usuarios`, `PUT /admin/usuarios/{id}`, `DELETE /admin/usuarios/{id}` + modal Crear/Editar en `administrador.html` (módulo `usuarios.js`). El Administrador asigna el rol; la contraseña solo cambia si se informa. Protecciones: no se borra un usuario con datos asociados ni el último Administrador. |
| **G24** | El modal de perfil abría VACÍO y el guardado fallaba en Analista/Inversionista | ALTO | ✅ | `abrirModal()` no rellenaba el formulario, y `guardar()` leía `formulario.nombre`/`.apellido` cuando el HTML define `nombre1`/`apellido1` → los campos llegaban `undefined` y el PUT fallaba. Corregido con `rellenarFormulario()` y `querySelector` por `id`. Verificado en los 3 roles. |
| **G25** | La colección Postman no era idempotente (fallaba al 2º uso) | MEDIO | ✅ | El test de «cambiar contraseña» **modificaba la clave real** del inversionista, así que la 2ª corrida daba 401 en todos los logins. Además: URLs mal formadas (`{{baseUrl}`), body de perfil con datos de otro usuario y el 403 usaba `noauth` (401). Sustituido por un caso **negativo** seguro. Verificado con **3 corridas seguidas: 35 requests / 54 asserts, 0 fallos**. |
| **G26** | Campos numéricos se amontonaban y montaban encima del botón «Calcular» | ALTO | ✅ | `input[type="number"]` **no estaba** en la lista de estilos de `styles.css` (solo `text`/`email`/`password`/`date`/`select`), así que los campos perdían el `width:100%` y se apilaban sin separación. Añadido el tipo `number` + ocultación de spinners + clases `.dashboard-form` / `.dashboard-form__fila` (grid de 2 columnas que se apila en móvil). Corregidos los formularios de **liquidez** (Analista) y **agregar al portafolio** (Inversionista), que tenían el mismo defecto. |
| **G27** | El proyecto no tenía ninguna gráfica | ALTO | ✅ | Nuevo `js/graficas.js`: SVG nativo, **sin librerías externas** (~200 KB menos y sin depender de Internet). Funciones `barras()` (admite negativos), `barraApilada()` y `doughnut()`. Conectadas a los datos que **ya llegaban** de la API: variación de empresas y composición de liquidez (Analista); reparto del portafolio y diferencia de alertas (Inversionista). SVG con `role="img"`+`aria-label`, textos escapados (anti-XSS) y placeholder cuando no hay datos. Test en `frontend/tests/graficas_test.js`. |
| **G28** | La gráfica del portafolio se veía **desproporcionadamente grande** | MEDIO | ✅ | `.grafica svg` tenía `width:100%` y el anillo declara `viewBox="0 0 200 200"`, así que se estiraba a todo el ancho del panel. Añadidas clases por tipo con ancho máximo (`.grafica--anillo` 220px centrado, `.grafica--barras`/`.grafica--linea` 640px). El contenedor cambia de clase al redibujar. |
| **G29** | No se podía cambiar el tipo de gráfica | MEDIO | ✅ | Selector de botones tipo "pill" (`crearSelector`) con `aria-pressed` y navegación por teclado. **Portafolio:** Anillo / Barras / **Línea** (nueva, con área bajo la curva, marcadores y rejilla). **Alertas:** Barras / Línea. La preferencia se guarda en `localStorage`, así que se recuerda al cambiar de vista o recargar. Se redibuja sin volver a llamar a la API (los datos quedan en caché). |

**Convención de hostname (G14):**
- Dirección real única: **`127.0.0.1`** (nunca `localhost`: en IPv6 resuelve a `::1` y rompe JDBC/CORS de forma intermitente).
- Nombre visible único: **`www.tourinvest.com`**, mapeado a `127.0.0.1` en el archivo de hosts del sistema.
- Aplicado en: `application.properties` (JDBC + CORS), `.env.example`, ambos `ConexionJDBC.java`, los 4 JS del frontend, la colección y el entorno de Postman, `setup.sh`, `setup.ps1`, `iniciar-todo.cmd`, `README.md`.
- `check` valida el mapeo y muestra el comando exacto si falta (sección `-- Nombre del sitio --`).
- Documentado en `STARTUP.md` → sección "Convención de hostname".

**Validación en vivo** (MySQL real + Newman):
- `mvn test` → `Tests run: 81, Failures: 0, Errors: 0`
- `newman run` → `28 requests, 44 assertions, 0 fallos`
- `GET /api/health` → `{"estado":"UP","baseDatos":"UP"}`
- Los 3 roles hacen login y respetan permisos (Inversionista → 403 en `/admin/**` y `POST /empresas`)

---

### Historial de correcciones anteriores| Hallazgo | Prioridad | Acción aplicada | Validación |
|---|---|---|---|
| **A1** | CRÍTICO | `application.properties` (resources) reescrito con datasource MySQL, JPA, `jwt.secret`, `jwt.expiration-ms`, puerto 8080. El código Java que estaba allí se movió a su `.java`. | Archivo ya no contiene código fuente Java. |
| **A2** | CRÍTICO | Creada `com/tourinvest/backend/TourinvestBackendApplication.java` (`@SpringBootApplication`). | Ubicación/paquete correctos; arranca con `mvn spring-boot:run`. |
| **B2** | MEDIO | Vocabulario `estado` de la Clean Architecture unificado a `Activo`/`Inactivo` (coherente con `query.sql` y `EstadoUsuario` Spring). | ✅ `javac + java Main` → usuarios `Activo`; suspensión → `Inactivo`. |
| **C1** | ALTO | `location.href='index.html'` → `'../index.html'` en `inversionista/analista/administrador.html` (home button) y `login.html` (fallback). | — |
| **C2** | ALTO | `RUTA_DASHBOARD` de `auth.js` apunta a `administrador.html`/`analista.html`/`inversionista.html`. | ✅ `node --check auth.js` OK. |
| **E1** | ALTO | `auth.js` l.124: bloque suelto envuelto en `async function registrarUsuario(evento) { … }`. | ✅ `node --check auth.js` OK. |
| **E2** | MEDIO | README: `javac` limitado a `domain/ + data/ + Main` (+ nota `mvn spring-boot:run`). | ✅ mismo comando compiló+mostró el demo de dominio. |
| **F1** | BAJO | `estructura` actualizado: quita paquete fantasma `config/`, documenta beans en `security/`, añade capa Clean `com/tourinvest/{domain,data}` + `Main`. | — |
| **F2** | MEDIO | `recuperar.html` hace `fetch POST http://localhost:5000/api/authentication/recuperar` y enlaza el mensaje al `{mensaje}` del backend. | — |
| **F3** | MEDIO | `AuthController.registrar` devuelve `UsuarioResumenDTO` (JSON) en vez de texto plano; import añadido. | `AuthControllerTest` sigue esperando `201` (compatible). |

> **Pendiente decisión de pila (B1 → D1/D2):** ambos backends son ahora correctos/booteables, pero conviven dos APIs (`/api/*` Python:5000 vs `/auth/*` Spring:8080) y el frontend *inline* usa Python mientras `js/*.js` usan Java. Decidir la pila única determina si se eliminan los JS huérfanos (D1/D2) y/o el backend Spring.

## 🔍 Hallazgos detallados

### A. Archivos faltantes / mal ubicados (bloqueo de arranque del backend Java)

#### [x] A1 — `application.properties` contiene código fuente Java en lugar de configuración
- **Ubicación:** `backend-servlets/src/main/resources/application.properties` (358 bytes)
- **Evidencia:** El archivo contiene literalmente el código fuente de la clase `TourinvestBackendApplication`:
  ```java
  package com.tourinvest.backend;
  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;
  @SpringBootApplication
  public class TourinvestBackendApplication {
      public static void main(String[] args) {
          SpringApplication.run(TourinvestBackendApplication.class, args);
      }
  }
  ```
- **Impacto (CRÍTICO):**
  1. No existe la **clase main de Spring Boot compilada**. Spring Boot necesita una clase anotada `@SpringBootApplication` en el classpath para arrancar; como el código está dentro de un `.properties` (texto no compilado), `mvn spring-boot:run` / `java -jar` fallan (no hay clase principal ejecutable).
  2. Faltan **todas** las propiedades reales: `spring.datasource.*`, `spring.jpa.hibernate.ddl-auto`, `jwt.secret` y `jwt.expiration-ms`. El `JwtUtil` inyecta `@Value("${jwt.secret}")` y `@Value("${jwt.expiration-ms}")` → **la aplicación no arranca** si se corrige A2, porque esos placeholders no se resuelven.
  3. Spring intentará parsear este archivo como propiedades y la primera línea `package com.tourinvest.backend;` no es `clave=valor`, lo que puede provocar errores al cargar el contexto.
- **Solución:**
  - Mover el código Java a `backend-servlets/src/main/java/com/tourinvest/backend/TourinvestBackendApplication.java`.
  - Reemplazar el contenido de `application.properties` por la configuración real:
    ```properties
    spring.datasource.url=jdbc:mysql://localhost:3306/tourinvest
    spring.datasource.username=root
    spring.datasource.password=
    spring.jpa.hibernate.ddl-auto=update
    spring.jpa.show-sql=true
    jwt.secret=<clave-secreta-de-al-menos-32-caracteres>
    jwt.expiration-ms=3600000
    ```

#### [x] A2 — Clase main de Spring Boot inexistente como archivo fuente
- **Ubicación:** debería estar en `backend-servlets/src/main/java/com/tourinvest/backend/TourinvestBackendApplication.java`.
- **Evidencia:** `find backend-servlets/src -iname '*application*.java'` → **ningún resultado**. La clase sólo "existe" como texto dentro de `application.properties` (ver A1). El `.vscode/launch.json` referencia `com.tourinvest.Main`, que es el demo de dominio puro, **no** el backend Spring.
- **Impacto:** El backend Spring Boot (controllers, services, security/JWT) no es ejecutable. Ningún endpoint `/auth/login`, `/admin/usuarios`, etc. funciona hasta que exista esta clase + las propiedades de A1.
- **Solución:** Crear el archivo `.java` con el contenido actual de `application.properties` y dejar `application.properties` con propiedades reales.


### B. Duplicidad de pila y vocabulario entre backends

#### [ ] B1 — Dos backends paralelos con contratos incompatibles
- **Ubicación:** `backend-python/` (Flask, puerto 5000) y `backend-servlets/src/main/java/com/tourinvest/backend/` (Spring Boot, puerto 8080).
- **Evidencia:**
  - Python expone `POST /api/authentication/login` → responde `{ok, usuario:{id,nombre,apellido,cedula,correo,rol,estado}}`.
  - Java expone `POST /auth/login` → responde `LoginResponse` `{token, nombre1, correo, rol}`.
  - El README ("Cómo ejecutar") documenta sólo: frontend (HTML), **Python como API** (`python app.py` en `localhost:5000/api/...`) y **Java como demo de dominio** (`com.tourinvest.Main`, sin el servidor Spring). No documenta arrancar el backend Spring Boot.
  - Sin embargo existe un backend Spring Boot completo (controllers, services, `SecurityConfig` con JWT, `SecurityFilterChain`, repos JPA, excepciones) que **no está conectado a nada ejecutable** (ver A1/A2) y cuya API no coincide ni con el frontend ni con la documentación.
- **Impacto (ALTO):** Confusión operativa: ¿qué backend se consume? El frontend HTML activo usa Python (5000); los JS huérfanos (auth.js/dashboard*.js) usan Java (8080). Si se intenta arrancar el Spring Boot, falta la clase main y las propiedades (A1/A2).
- **Solución:** Decidir pila única. O bien (a) se descarta el backend Spring Boot y se borran los JS huérfanos + controllers Java, manteniendo Python; o (b) se completa el backend Spring Boot (clase main + properties), se eliminan los JS huérfanos y se reenlaza el frontend al Java. Mantener ambas pilas duplica esfuerzo y genera incoherencias.

#### [x] B2 — Vocabulario de roles/estado duplicado y contradictorio entre capas
- **Ubicación:** varios.
- **Evidencia:**
  - `backend-servlets/.../domain/entities/Usuario.java` (Clean Architecture): `Rol { ADMINISTRADOR, ANALISTA, INVERSIONISTA }`; `estado` = `"activo"` / `"suspendido"` (lowercase).
  - `backend-servlets/.../backend/model/Usuario.java` (Spring): `EstadoUsuario { Activo, Inactivo }` (el SQL usa `Inactivo`, el dominio usa `suspendido`).
  - `backend-python/core/database.py`: `"estado": "activo"` (lowercase); roles `"inversionista"|"analista"|"administrador"` (lowercase).
  - `query.sql` tabla `usuarios.estado ENUM('Activo','Inactivo')` (el Spring model usa `Activo/Inactivo`, pero el dominio Java usa `activo/suspendido`).
- **Impacto (MEDIO):** Un mismo concepto ("suspender/usuario inactivo") se expresa de 3 formas distintas; cruzar capas (p. ej. mapear dominio→Spring) produce bugs silentes. El `GestionarUsuariosUseCase.suspenderUsuario` escribe `"suspendido"`, pero la tabla SQL Spring espera `Activo/Inactivo`.
- **Solución:** Unificar vocabulario (p. ej. `Activo/Inactivo` en las 3 capas, o `activo/suspendido`). Mantener una única definición canónica de roles/estados y consumirla desde ambos backends.


### C. Rutas rotas / enlaces rotos (frontend)

#### [x] C1 — Enlace a `index.html` roto desde las páginas de `frontend/`
- **Ubicación:** `frontend/inversionista.html` (l.40), `frontend/analista.html` (l.38), `frontend/administrador.html` (l.39) y `frontend/login.html` (l.78, fallback).
- **Evidencia:** El "brand/home" de cada dashboard ejecuta `onclick="location.href='index.html'"`. Pero `index.html` vive en la **raíz del repo**, no dentro de `frontend/`. Desde `frontend/inversionista.html`, la ruta relativa `index.html` resuelve a `frontend/index.html`, que **no existe** → 404 al hacer clic en el logo/TourInvest.
- **Verificado:** `ls frontend/index.html` → `No existe el archivo o el directorio`; `ls index.html` (raíz) → existe.
- **Impacto (ALTO):** El botón de "volver al inicio" de los 3 dashboards y el fallback de login están rotos.
- **Solución:** Cambiar a `location.href='../index.html'` (o `href="../index.html"` como `<a>`).

#### [x] C2 — `auth.js` redirige a nombres de archivo inexistentes
- **Ubicación:** `frontend/js/auth.js` (l.5-9, constante `RUTA_DASHBOARD`).
- **Evidencia:**
  ```js
  const RUTA_DASHBOARD = {
    Administrador: "dashboard-administrador.html",
    Analista: "dashboard-analista.html",
    Inversionista: "dashboard-inversionista.html",
  };
  ```
  Los archivos reales se llaman `administrador.html`, `analista.html`, `inversionista.html` (sin el prefijo `dashboard-`).
- **Verificado:** `for f in dashboard-administrador.html dashboard-analista.html dashboard-inversionista.html; do [ -e "frontend/$f" ] && echo EXISTS || echo MISSING; done` → los 3 son **MISSING**.
- **Impacto (ALTO):** Si alguna vez se activa `auth.js`, los logins redirigen a URLs inexistentes.
- **Solución:** Usar los nombres reales o renombrar los HTML.

### D. Código muerto / duplicidad frontend (JS huérfanos)

#### [ ] D1 — Cuatro archivos JS no son cargados por ningún HTML
- **Ubicación:** `frontend/js/auth.js`, `frontend/js/dashboard.js`, `frontend/js/dashboard_administrador.js`, `frontend/js/dashboard_analista.js`.
- **Evidencia:** `grep -rn 'auth\.js\|dashboard\.js\|dashboard_administrador.js\|dashboard_analista.js' frontend/*.html index.html` → **NONE**. Los HTML usan scripts **inline** (login/registro/recuperar con validación inline, y dashboards con routing por hash inline). Sólo `frontend/js/app.js` se carga (`<script src="js/app.js" defer>` en los 3 dashboards).
- **Impacto (ALTO):** Código muerto y duplicado: la lógica de login, registro, portafolio, alertas, usuarios, reportes, indicadores está escrita **dos veces** (inline + JS externo), el externo apuntando al backend Java (8080) y el inline al Python (5000).
- **Solución:** Eliminar los 4 archivos huérfanos (y `app.js` si se reemplaza el inline) y consolidar en una sola implementación alineada al backend que se vaya a usar.

#### [ ] D2 — Los JS huérfanos usan selectores/estructura que no existen en los HTML
- **Ubicación:** `frontend/js/dashboard.js`, `dashboard_administrador.js`, `dashboard_analista.js`.
- **Evidencia:**
  - `dashboard.js` consulta `.sidebar__link[data-vista]`, `.vista[data-vista-panel]`, `#nombre-usuario-topbar`, `#tabla-portafolio-cuerpo`, `#form-agregar-portafolio`, `#form-crear-alerta`, `#selector-empresa-reporte`, `#tabla-alertas-cuerpo`, `#perfil-nombre`, `#perfil-rol` (varios).
  - `dashboard_administrador.js` consulta `.sidebar__link[data-vista]`, `.vista[data-vista-panel]`, `#nombre-usuario-topbar`, `#tabla-usuarios-cuerpo`.
  - El HTML real usa `data-view` / `data-view-pane`, contenedores `#inversionista-root`/`#analista-root`/`#admin-root`, `.sidebar`/`sidebar-link`/`.sidebar-nav`, y es **estático** (sin IDs de tabla `tabla-portafolio-cuerpo`, etc.).
  - `dashboard_analista.js` incluso mezcla convenciones: usa `.sidebar-link` (que existe) pero espera panes `.vista[data-vista-panel]` (el HTML usa `.view[data-view-pane]`).
  - Verificado: `grep -nE 'sidebar__link|data-vista|\.vista--activa|nombre-usuario-topbar|tabla-portafolio-cuerpo' frontend/css/*.css` → **NONE** (ningún selector de los JS huérfanos está en las CSS).
- **Impacto (MEDIO):** Confirmación de que los JS huérfanos están desconectados del HTML real; no pueden funcionar ni siquiera si se cargan.
- **Solución:** Si se reutilizan, reescribirlos contra la estructura real (`data-view`/`data-view-pane`) o regenerar el HTML contra los selectores del JS. Mejor: consolidar (ver D1).


### E. Errores de código y sintaxis

#### [x] E1 — `auth.js` tiene un bloque suelto (función `registrar` sin declaración)
- **Ubicación:** `frontend/js/auth.js`, a partir de la línea 124.
- **Evidencia:** La función `solicitarRecuperacion` cierra correctamente en la línea 123 (`}`), y a continuación aparece código suelto:
  ```js
  }
    evento.preventDefault();          // ← línea 124: 'evento' NO está definido a nivel módulo
    const formulario = evento.target;
    const boton = formulario.querySelector("button[type='submit']");
    ...
    const respuesta = await fetch(`${API_BASE_URL}/auth/registro`, { ... });
  ```
  Parece la **cuerpo de una función `registrar`/`registrarse`** cuya cabecera `async function registrar(evento) {` ha desaparecido. Ese `await fetch` también está a nivel módulo (fuera de cualquier función), lo cual además es un error de sintaxis en módulos (top-level `await` no permitido en scripts clásicos).
- **Impacto (ALTO):** Si se cargara `auth.js`, el script lanzaría `ReferenceError: evento is not defined` y/o error de parseo, bloqueando la carga. Como es huérfano (ver D1) no afecta al runtime actual, pero es un defecto real.
- **Solución:** Añadir la declaración `async function registrarUsuario(evento) { ... }` que envuelva ese bloque (o borrar el archivo si se descarta el backend Java).

#### [x] E2 — La instrucción de compilación del README es incorrecta
- **Ubicación:** `README.md` (l.82) → `javac -d out $(find src -name "*.java")`.
- **Evidencia:** `find src -name "*.java"` incluye `com/tourinvest/backend/**` (controllers, modelos JPA, `JwtUtil`, `SecurityConfig`, services), que importan `org.springframework.*`, `jakarta.persistence.*`, `io.jsonwebtoken.*`. El `javac` plano (sin el classpath de Maven) **no resuelve esas dependencias** → la compilación falla. Sólo la capa Clean Architecture (`com/tourinvest/domain`, `com/tourinvest/data`, `com/tourinvest/Main`) compila con javac plano.
- **Impacto (MEDIO):** No se puede reproducir el demo de Java tal como está escrito en el README.
- **Solución:** Para la demo de dominio, limitar la compilación: `javac -d out backend-servlets/src/main/java/com/tourinvest/domain/**/*.java backend-servlets/src/main/java/com/tourinvest/data/**/*.java backend-servlets/src/main/java/com/tourinvest/Main.java` (y equivalente recursivo). Para el backend Spring, usar `mvn -q compile` o `mvn spring-boot:run`.

### F. Drift documental y funcionalidades incompletas

#### [x] F1 — `backend-servlets/estructura` está desactualizado
- **Ubicación:** `backend-servlets/estructura`.
- **Evidencia:** Documenta `com/tourinvest/backend/TourinvestBackendApplication.java` como "clase main" y un paquete `config/` para "CORS, beans, config general". En la realidad:
  - La clase main **no existe** como `.java` (está como texto en `application.properties`, ver A1).
  - No hay paquete `config/` (`find backend-servlets/src -type d -name config` → vacío); los beans de configuración están dentro de `security/SecurityConfig`.
  - No menciona la capa Clean Architecture (`com/tourinvest/domain/...`, `com/tourinvest/data/`, `com/tourinvest/Main.java`).
- **Impacto (BAJO):** La guía de estructura no refleja el código real, lo que confunde a quien lo sigue.
- **Solución:** Actualizar `estructura` a la estructura efectiva o generar la estructura que `estructura` describe (clase main, package config, application.properties real).

#### [x] F2 — `recuperar.html` no consume la API disponible
- **Ubicación:** `frontend/recuperar.html` (script inline, l.40-59).
- **Evidencia:** La función `enviarRecuperacion` es un mock que solo muestra un mensaje estático ("Si el correo está registrado, recibirás un enlace..."). No hace `fetch`. El backend Python **sí expone** `POST /api/authentication/recuperar` (`backend-python/features/authentication/presentation/routes.py` l.46), y `auth.js` (huérfano) incluso la llama a `${API_BASE_URL}/auth/recuperar`.
- **Impacto (MEDIO):** Funcionalidad incompleta: la recuperación nunca llega al backend.
- **Solución:** Implementar el `fetch('http://localhost:5000/api/authentication/recuperar', {...})` y enlazar el mensaje al resultado.

#### [x] F3 — Contratos de respuesta / nombres de campo entre frontend y backend Java
- **Ubicación:** `frontend/js/auth.js` vs `backend-servlets/.../dto/LoginResponse.java` y `RegistroRequest.java`.
- **Evidencia:** `auth.js` almacena `cuerpo.nombre1`, `cuerpo.correo`, `cuerpo.rol` y usa `cuerpo.token` (forma Java) — pero `AuthController.registrar` devuelve `ResponseEntity.status(201).body(creado.getCorreo())`, es decir, **sólo el correo como texto plano**, no un objeto JSON. Por tanto, aun sin el bug de sintaxis (E1), el flujo de registro de `auth.js` no funcionaría contra el backend Java.
- **Impacto (BAJO/MEDIO):** Refuerza que los JS huérfanos están no probados contra el backend real.
- **Solución:** Devolver un DTO consistente (`UsuarioResumenDTO` o `LoginResponse`) desde `AuthController.registrar`.


### G. Verificación y próximos pasos

#### ✅ Verificado consistente (no es problema)
- **Credenciales del README ↔ Python `database.py`:** coinciden (`inversor@`/`inversor123`, `analista@`/`analista123`, `admin@`/`admin123`).
- **Assets del frontend:** todas las imágenes referenciadas (`frontend/assets/tourinvest.png`, `*.avif`) y CSS (`css/styles.css`, `css/real_theme.css`, `css/landing.css`) existen; `index.html` (raíz) referencia correctamente `frontend/...`.
- **Navegación inline de dashboards:** los `data-view-pane` de `inversionista.html` (portafolio/alertas/noticias/empresas/perfil) y `analista.html` (empresas/indicadores/reportes/recomendaciones) coinciden con sus ítems de sidebar.
- **`app.js` (modales/toasts/tabs):** está cargado y sus clases CSS (`real_theme.css: .modal`, `.modal--open`, `.toast`) existen.

#### 🛠 Hoja de ruta sugerida (prioridad)
1. **CRÍTICO-A1/A2:** Recuperar/crear `TourinvestBackendApplication.java` y rellenar `application.properties` real (datasource + jwt.secret + jwt.expiration-ms). Sin esto, el backend Java Spring Boot no arranca.
2. **ALTO-B1/D1:** Elegir *una* pila backend. Recomendación: **consolidar en Python** (el que el README y el frontend activo ya usan). Con ello:
   - Borrar los 4 JS huérfanos (`auth.js`, `dashboard*.js`) y `app.js` (reemplazado por inline) — o bien migrar el inline a un único JS bien estructurado.
   - Borrar/anotar el backend Spring Boot (`backend-servlets/.../backend/`) y los tests Java si no se mantiene.
   - Si en su lugar se elige **Java**: completar A1/A2, borrar el backend Python, y reescribir el frontend HTML inline para consumir `/auth/*`, `/admin/*`, `/inversionista/*`, `/analista/*` en 8080 (incluyendo corregir `index.html` → `../index.html`).
3. **ALTO-C1/C2:** Corregir rutas rotas (`../index.html`; nombres reales de dashboards) — inmediato en frontend.
4. **ALTO-E1:** Reparar o eliminar `auth.js` (bloque suelto).
5. **MEDIO-B2/F2/E2/F3 y BAJO-F1:** armonizar vocabulario, conectar `recuperar.html` a la API, documentar la compilación correcta y actualizar `estructura`.

> **Nota de proceso:** Todos los hallazgos se obtuvieron con `grep`/`find` sobre el árbol (excluyendo `node_modules`, `venv`, `target`, `out`, `__pycache__`) y lectura directa de los archivos fuente. Los fragmentos citados provienen de las versiones actuales del repositorio.
---

## 🧪 Apéndice — Validación de la compilación y tests

Se ejecutó `mvn -q test` en `backend-servlets` (online) y `javac + java` en la Clean Architecture, validando A1/A2, B2, E2 y F3:

**Backend Java Spring Boot (Maven) — el contexto arranca ✅**
- `mvn` resolvió dependencias y el `ApplicationContext` de Spring Boot **arrancó correctamente** con el `application.properties` corregido (datasource MySQL, JPA, `jwt.secret`/`jwt.expiration-ms`, puerto 8080) y la nueva clase `TourinvestBackendApplication` → **A1/A2 validados**.
- `AuthControllerTest`: **8 tests, 0 fallos** → el cambio F3 (`registrar` devuelve `UsuarioResumenDTO`) es compatible (el test asiente `201`).
- `JwtUtilTest`: **6 tests, 0 fallos** → los placeholders `${jwt.secret}` / `${jwt.expiration-ms}` se resuelven.
- Global: `Tests run: 74, Failures: 11, Errors: 1`. Los 11 fallos son **preexistentes y no causados por esta sesión** (ninguno toca `AuthController`/`JwtUtil`); son: fields JSON inexistentes en respuestas (`simbolo`, `nombreEmpresa`, `nombreUsuario`, `estado`), redondeo en `PortafolioService` (8.6111 vs 8.6100) y autorización en `Alerta/Inversionista/AdministradorController`. Son síntoma de **B1/B2** y quedan pendientes de la decisión de pila.

**Clean Architecture Java (dominio/demo) — compila y corre ✅**
- `javac -d out $(find domain data -name '*.java') Main.java && java -cp out com.tourinvest.Main` → salida correcta con estados unificados (`Activo` / `Inactivo`). Valida **E2** y **B2**.

**Frontend / Python**
- `node --check frontend/js/auth.js` → **OK** (valida E1 + C2).
- `node --check` en `app.js`, `dashboard.js`, `dashboard_administrador.js`, `dashboard_analista.js` → todos OK (sintaxis).
- `python3 -m py_compile backend-python/app.py` → **OK**.

**Pendiente decisión de pila (B1 → D1/D2)**
- Conviven Python:5000 (`/api/...`) y Spring:8080 (`/auth/...`). El frontend *inline* usa Python; los JS huérfanos usan Java. Resolverlo implica elegir la pila única y, con ello, eliminar o migrar los 4 JS huérfanos (`auth.js`, `dashboard*.js`) y el backend no consolidado.

---

## 🧭 Coherencia aplicada (fase actual) + fase siguiente

**Decisión B1:** el frontend y todos los dashboards consumen el backend **Java Spring Boot
(`:8080`) sobre **MySQL** (`tourinvest`) con **JWT**. Python (`backend-python`) queda como
réplica coherente de vocabulario y modelo de datos (no como API canónica del frontend).
Justificación: los DTOs Spring ya coinciden con los fields que los `dashboard_*.js` leen
(`nombreUsuario`, `simbolo`, `nombreEmpresa`, `fechaGeneracion`, `idAlerta`, …) y los
endpoints Spring (`/auth/*`, `/inversionista/*`, `/empresas`, `/analista/*`, `/admin/*`)
coinciden con los de los dashboards; Java arranca sobre MySQL.

**Hecho esta fase (validado):**
- Frontend auth unificado en Java `:8080` vía `js/auth.js` (login/registro/recuperar).
  `:5000` eliminado del frontend; token JWT guardado en `sessionStorage`. `node --check OK`.
- Vocabulario Python alineado a MySQL (`<estado>` `Activo/Inactivo`, roles
  `Administrador/Analista/Inversionista`). `py_compile` OK; assert de vocabulario OK.
- `GlobalExceptionHandler`: `ArithmeticException` (pasivo=0) ahora mapea a **400** (era 500).
- README reescrito coherente (un único contrato, MySQL fuente de verdad).

**Pendiente (fase siguiente) — no tocados para no destabilizar:**
- **Tests Spring Boot (12 → clasificados):** 10 son fallos de setup de seguridad en los
  tests (principal no inyectado → autorización omitida; datos mock a null → `No value at $.X`).
  El código producto ya es coherente (DTOs == fields de los dashboards; authz vía excepciones).
  Restantes reales de producto: (a) `ArithmeticException→400` corregido; (b) precisión
  `rendimientoTotal` `8.6100` vs `8.6111` en `PortafolioService`.

**✅ Dashboards en vivo (D1/D2) — COMPLETADO (2026-08-26):**
- `dashboard.js`, `dashboard_analista.js`, `dashboard_administrador.js` **cableados** y
  alineados con sus HTML (`data-vista`/`data-vista-panel`, `#nombre-usuario`,
  `#tabla-portafolio-cuerpo`, `#form-agregar-portafolio[data-idAccion]`, …). `node --check` OK en los 4.
- Vista inicial visible en los 3 dashboards (faltaba `vista--activa` → el contenido quedaba en blanco al cargar).
- Modal de empresa unificado a `.modal--open` + `TourInvestUI.initModals()` (cierra con ✕, fondo o ESC;
  atributo HTML corregido a `data-close-modal`). Eliminado el doble submit (`onsubmit` +
  `addEventListener` duplicaban los POST de portafolio/alertas/liquidez/reportes).
- Vista **Noticias** añadida al inversionista (mockup 6.3) y **buscador de usuarios** al admin
  (mockup 6.8, filtro cliente sobre `/admin/usuarios`).
- `SecurityConfig`: añadido `UserDetailsService` sobre `UsuarioRepository` + `DaoAuthenticationProvider`
  (el `AuthenticationConfiguration` degeneraba en recursión infinita → `StackOverflowError` → 403 opaco
  vía `/error`); `/error` en `permitAll`. Login validado E2E contra MySQL (hashes BCrypt `$2b$`).
- JDBC: `allowPublicKeyRetrieval=true` (MySQL 8/9 con `useSSL=false`).
- Datos de prueba sembrados (posición Apple/MSFT y alertas Cumplida/Activa) para que las
  pantallas tengan contenido en las capturas.
- `styles.css`: añadidos `.btn`/`.btn-link` (botones del modal salían sin estilo: solo
  existían en `real_theme.css`, que los dashboards no cargan) y `.etiqueta-variacion--positiva/negativa`
  (la variación de tablas salía sin color).

**✅ Gestión de Empresas (CRUD completo, panel Administrador) — 2026-08-28:**
- Backend: `POST /empresas`, `PUT /empresas/{id}`, `DELETE /empresas/{id}` (`EmpresaController`
  + `EmpresaService.crear/actualizar/eliminar` + `EmpresaRequest` DTO con validación).
  Seguridad: matchers `HttpMethod` → solo `ROLE_ADMINISTRADOR` muta; GET sigue para autenticados.
- Crear/editar también gestiona la cotización (tabla `acciones`: precio + fechaActualizacion).
- Eliminar protegido por integridad: si la empresa tiene **inversiones o alertas** de usuarios
  (FK RESTRICT en `query.sql`) responde **400** con mensaje claro; si no, borra acciones + empresa.
- Símbolo único validado (nuevo y al editar: `existsBySimbolo`/`existsBySimboloAndIdEmpresaNot`).
- Frontend `administrador.html`: vista "Empresas" en el sidebar + tabla (Símbolo/Nombre/Sector/País/
  Precio/Variación) + botón "+ Crear empresa" + modal crear/editar + Eliminar con confirmación.
  `dashboard_administrador.js` cableado (cargar/guardar/eliminar + toasts). `node --check` OK.
- Validado E2E contra la API: crear→listar→editar→eliminar; laura (Analista)→403 al crear;
  DELETE Apple → 400 "tiene inversiones de usuarios asociadas" y Apple intacta.

---

## 🧭 Fase 2 — Wiring frontend + montaje (validado)

**Aplicado y validado:**
- **Bug real `auth.js` (crítico en registro):** `.closest(".campo")` → `.closest(".field")` (líneas 144 y 176). Se desactivaba el resaltado de errores y lanzaba `TypeError`. `node --check auth.js` OK.
- **`frontend/index.html` creado** (era la ruta faltante a la que apuntaban los botones "home" de los 3 dashboards). Ahora `frontend/index.html` existe; `README` y el home button la referencian.
- **`registro.html`:** añadidos los 7 contenedores `.field__mensaje-error` + regla CSS. `field__mensaje-error` (CSS:1, HTML:7).
- **CORS `SecurityConfig`:** se añadieron orígenes `:8081` (dev estático) a los existentes `:5500`, y **`PATCH`** a `setAllowedMethods` (los dashboards llaman `PATCH /alertas/{id}/cancelar` y `/admin/usuarios/{id}/suspender`). Compila (`mvn -q -DskipTests compile` → status 0).
- **Credenciales seed corregidas** en README + STARTUP: `query.sql` usa `kike@tourinvest.com`/`marlen@tourinvest.com`/`juan@tourinvest.com` (clave **123456**), no los viejos `inversor@…`/`admin123`. STARTUP había quedado con `Clave123` → corregido.
- **`STARTUP.md` completado** (antes 34 líneas/truncado): secciones 0-7 (requisitos, BD, backend, frontend, Python réplica, tests, smoke, orden de arranque). Ahora 156 líneas.
- **`setup.sh` creado** (era referenciado pero no existía): `check`/`seed`/`run-backend`/`run-frontend`. `bash -n` OK.

**Pendiente (actualizado 2026-08-26):**
- Tests Spring: 11 failures por setup de security-context / mocks null (no bugs de producto);
  además Mockito requiere **JDK 17** en este entorno (con JDK 25 lanza `MockitoException`).
  Con un JDK 17 en `JAVA_HOME` deberían pasar los tests de lógica (los bugs de producto
  listados abajo ya están corregidos).
- ~~Precisión `rendimientoTotal` (8.6100 vs 8.6111)~~ → **RESUELTO/VERIFICADO**: el código
  actual divide con escala 4 `HALF_UP` (`PortafolioService.calcularRendimiento`, con manejo
  de división por cero) y produce exactamente `8.6111` para el caso del test; verificado
  aritméticamente y en vivo (la API devuelve `rendimientoTotalPorcentual` con 4 decimales).
- ~~Cablear los `dashboard_*.js` a los HTML~~ → **HECHO** (ver "✅ Dashboards en vivo (D1/D2)"
  y "🧭 Fase 2"). El apunte quedó obsoleto.
