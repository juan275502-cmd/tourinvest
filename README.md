# TourInvest — Implementación en HTML, CSS, Python y Java

Este proyecto traduce la documentación de TourInvest Mobile (originalmente en
Flutter/Dart) a una pila **web + backend Python + núcleo de dominio en Java**,
conservando la arquitectura, navegación, roles y prototipos definidos en los
documentos fuente (`arquitectura.md`, `navegacion.md`, `prototipos.md`,
`usabilidad_accesibilidad.md`, `xml_android.md`, `layouts_android.md`,
`maquetacion_android_ev08.md`, `README.md`).

## Estructura del proyecto

```
tourinvest/
├── frontend/                     # HTML + CSS (Presentation)
│   ├── css/styles.css            # Paleta: #1E3A5F, #2E8B57, #FFFFFF + responsive
│   ├── login.html                # RF01 - Autenticar usuario
│   ├── registro.html             # RF04 - Registrar usuario
│   ├── recuperar.html            # Recuperar / restablecer contraseña
│   ├── inversionista.html        # RF07 - Portafolio, Alertas, Noticias, Empresas, Perfil
│   ├── analista.html             # RF10 - Empresas, Indicadores, Reportes, Recomendaciones
│   └── administrador.html        # RF13 - Usuarios, Roles, Auditorías, Configuración
│
├── backend-python/               # API REST (Clean Architecture + Features First)
│   ├── app.py                    # Punto de entrada Flask
│   ├── requirements.txt
│   ├── core/database.py          # Datos compartidos (simula MySQL de README.md)
│   └── features/
│       ├── authentication/{domain,data,presentation}   # login, registro
│       ├── investments/{domain,data,presentation}      # portafolio, alertas, empresas
│       ├── analysis/{domain,data,presentation}         # indicadores, reportes
│       └── dashboard/presentation                      # usuarios, roles, auditorías
│
└── backend-servlets/                 # Núcleo de dominio (Entities + Use Cases)
    └── src/main/java/com/tourinvest/
        ├── domain/entities/      # Usuario, Empresa, PosicionPortafolio, Alerta, Reporte
        ├── domain/repositories/  # Contratos (interfaces)
        ├── domain/usecases/      # AutenticarUsuario, RegistrarUsuario, GestionarUsuarios, CrearAnalisis
        ├── data/                 # Implementación en memoria del repositorio
        └── Main.java             # Demo ejecutable de la capa de dominio
```

## Cómo se mapea cada documento

| Documento | Dónde se refleja |
|---|---|
| `arquitectura.md` | Carpetas `domain/`, `data/`, `presentation/` en Python y Java; `core/` compartido |
| `navegacion.md` | Menús laterales (`sidebar`) de cada dashboard HTML, con las mismas secciones por rol |
| `prototipos.md` | Componentes exactos de cada pantalla (Login, Registro, 3 dashboards) y sus RF |
| `usabilidad_accesibilidad.md` | Alto contraste, tipografía legible, `@media` responsive, validaciones de formulario |
| `README.md` (original) | Paleta de colores, roles y tecnologías (Python, Java, REST API) |
| `xml_android.md` / `layouts_android.md` | Inspiraron los formularios HTML (mismos campos: email, password, nombre, apellido, cédula) |

## Cómo ejecutar

### 1. Frontend (HTML/CSS)
El frontend (HTML/CSS/JS) consume la API Spring Boot de Java (`http://www.tourinvest.com:8080`) con JWT
y persiste el token en `sessionStorage`. Ábrelo directamente en el navegador
(`frontend/login.html`); también puedes abrir `frontend/index.html` (landing).

| Rol | Correo | Contraseña |
|---|---|---|
| Administrador | kike@tourinvest.com | 123456 |
| Analista | marlen@tourinvest.com | 123456 |
| Inversionista | juan@tourinvest.com | 123456 |

### 2. Backend Java Spring Boot (API REST canónica — :8080)
Requiere MySQL corriendo y la base `tourinvest` (`query.sql`):
```bash
cd backend-servlets
# 1) crear la base y el seed de desarrollo:
mysql -u root < query.sql
# 2) arrancar la API Spring Boot (+ JWT + MySQL) en :8080
mvn spring-boot:run
```
Contrato único de la API (lo consumen los dashboards y las páginas de auth):
- `POST /auth/login`      → `{ token, nombre1, correo, rol }` (200)
- `POST /auth/registro`   → `RegistroRequest` → `UsuarioResumenDTO` (201)
- `POST /auth/recuperar`  → `{ mensaje }` (200)
- `GET  /inversionista/resumen`, `POST /inversionista/portafolio/inversiones`,
  `GET /inversionista/alertas`, `PATCH /inversionista/alertas/{id}/cancelar`
- `GET /empresas`, `GET /empresas/{id}`
- `GET /analista/reportes`, `POST /analista/reportes`, `POST /analista/indicadores/liquidez`
- `GET /admin/usuarios`, `PATCH /admin/usuarios/{id}/suspender`, `PATCH /admin/usuarios/{id}/activar`

### 3. Núcleo de dominio Java (Clean Architecture — demo)
Capa pura sin Spring/JPA/JWT (reglas de negocio reutilizables):
```bash
cd backend-servlets
javac -d out \
  $(find src/main/java/com/tourinvest/domain -name "*.java") \
  $(find src/main/java/com/tourinvest/data -name "*.java") \
  src/main/java/com/tourinvest/Main.java
java -cp out com.tourinvest.Main
```
Ejecuta una demo de los casos de uso (autenticación, registro, gestión de
usuarios y creación de análisis) sobre las entidades de dominio, independiente
del framework web.

### 4. Backend Python (réplica coherente — :5000)
Réplica del vocabulario y del modelo de datos sobre MySQL; útil para pruebas
unitarias del dominio `features/` sin levantar Spring. Usa datos en memoria
(`core/database.py`) con el mismo vocabulario que MySQL:
```bash
cd backend-python
pip install -r requirements.txt
python app.py
# http://127.0.0.1:5000/api/health
```

## Validación de datos (registro, login y recuperación)

La regla vive en el **backend** (`com.tourinvest.backend.validation`) y el
frontend la replica en `frontend/js/auth.js` para mostrar el error bajo cada
campo sin llegar al servidor. Ambos lados aplican los mismos criterios y una
prueba (`frontend/tests/test_dashboard_wiring.py`) verifica que no se separen.

### Contraseña

Mínimo **12 caracteres** y máximo 72 (límite de BCrypt), con al menos:

| Requisito | Mínimo |
|---|---|
| Letras mayúsculas | 2 |
| Letras minúsculas | 2 |
| Números | 2 |
| Caracteres especiales | 2 |

Ejemplo válido: `TourInvest2026*!` · Inválido: `123456`

Se exige al **registro público**, al **alta/edición de usuarios del panel de
administrador** y al **cambio de contraseña del perfil**. El **login no** exige
complejidad: ahí la contraseña solo se verifica contra el hash, de modo que las
cuentas creadas antes de esta regla (las de `query.sql`, clave `123456`) siguen
pudiendo entrar.

Si faltan varios requisitos, el mensaje los enumera todos:
*"La contraseña debe cumplir: mínimo 12 caracteres; al menos 2 letras
mayúsculas; al menos 2 números."*

### Correo, nombre, cédula y fecha

- **Correo:** exige `@` **y** una extensión de dominio (`usuario@dominio.com`).
  El `@Email` de Hibernate acepta `usuario@dominio` sin extensión, por eso el
  proyecto usa la restricción propia `@CorreoValido`.
- **Nombre / apellido:** letras del español (tildes, `ñ`, diéresis) separadas
  por espacios, guiones o apóstrofos. Se acepta `María José`; se rechaza
  `Juan123` o `Juan@`. Tope de 30 y 100 caracteres.
- **Cédula:** solo dígitos, entre 6 y 20.
- **Fecha de nacimiento:** obligatoria, con formato `AAAA-MM-DD` y anterior a hoy.

### Pruebas

```bash
cd backend-servlets && mvn test                                # 143 pruebas
node frontend/tests/auth_validation_test.js                    # casos PV-01 a PV-17
python3 -m unittest discover -s frontend/tests -p 'test_*.py'  # 16 pruebas
```

El detalle de los casos de prueba (PV-01 a PV-17, PF e incidencias) está en
[`PRUEBAS_VALIDACION.md`](PRUEBAS_VALIDACION.md).

## Notas de diseño (coherencia)

- **Una sola pila canónica:** el frontend y los dashboards consumen el backend
  Spring Boot de Java (`:8080`) sobre **MySQL** (`tourinvest`) con **JWT**.
- **Backend único de datos:** `backend-servlets/src/main/resources/application.properties`
  apunta a `jdbc:mysql://127.0.0.1:3306/tourinvest` y `query.sql` es el esquema
  fuente de verdad. El vocabulario (`EstadoUsuario.Activo/Inactivo`,
  `Rol.NombreRol.Administrador/Analista/Inversionista`) coincide en Java, MySQL
  y la réplica Python.
- **Contrato único de autenticación:** `POST /auth/login` devuelve
  `{ token, nombre1, correo, rol }`; el frontend persiste el token en
  `sessionStorage` y lo envía como `Authorization: Bearer <token>`.
- **Réplica Python (`:5000`):** `backend-python` mantiene el mismo vocabulario y
  modelo de datos que MySQL (`core/database.py`), de modo que sigue
  ejecutándose sin MySQL real; en producción su capa `data` se sustituye por
  conexiones a MySQL.
- Clean Architecture (`domain/`, `data/`, `features/`) se mantiene expresada en
  Python y en Java; el backend Java Spring reúne controllers/services/security.
