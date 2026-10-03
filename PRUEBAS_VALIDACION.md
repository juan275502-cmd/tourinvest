# Pruebas de validación de datos — TourInvest

Alcance: **validación de los formularios de registro, inicio de sesión y
recuperación**, con énfasis en la **política de contraseñas**.

> Los resultados de este documento provienen de las suites automatizadas del
> proyecto, que se ejecutan sin base de datos ni API levantada. La columna
> "Evidencia" indica la prueba automatizada que respalda cada caso.

## 1. Política de contraseñas aplicada

| Requisito | Valor |
|---|---|
| Longitud mínima | 12 caracteres |
| Longitud máxima | 72 caracteres (límite de BCrypt) |
| Letras mayúsculas | mínimo 2 |
| Letras minúsculas | mínimo 2 |
| Números | mínimo 2 |
| Caracteres especiales | mínimo 2 |

Ejemplo válido: `TourInvest2026*!` · Inválido: `123456`

Se exige en: registro público, alta/edición de usuarios del panel de
administrador y cambio de contraseña del perfil. **No** se exige en el login
(allí solo se verifica el hash), para que las cuentas existentes con clave
`123456` puedan seguir entrando.

Detalle completo en `README.md` → *Validación de datos*.

---

## 2. Casos de validación (PV-01 a PV-17)

| Código | Dato de prueba | Resultado esperado | Resultado real | Evidencia |
|---|---|---|---|---|
| PV-01 | Enviar el formulario con campos obligatorios vacíos | Mostrar validación y evitar el envío | **Cumple.** El navegador marca los campos con `required` y `auth.js` pinta el mensaje bajo los 7 campos; no se envía nada a la API. En la API, `POST /auth/registro` con `{}` responde **400** con un error por campo obligatorio | `pv01_sinCampos_...` · `auth_validation_test.js` |
| PV-02 | Ingresar espacios en un campo obligatorio | Rechazar el valor si no contiene información válida | **Cumple.** Un campo con solo espacios se trata como vacío: *"El nombre es obligatorio"*. Ninguna regla de formato duplica ese mensaje | `pv02_soloEspacios_...` · `validarNombre("   ")` |
| PV-03 | Ingresar un nombre con espacios entre palabras | Aceptar si cumple las reglas configuradas | **Cumple.** `Ana María` se acepta y registra (201). También se aceptan el guion y el apóstrofo (`O'Brien-Smith`) | `pv03_nombreConEspacios_...` |
| PV-04 | Ingresar letras con tildes o caracteres propios del español | Aceptar si están contemplados por las reglas del campo | **Cumple.** Se aceptan `Ángela` y `Núñez-Öchoa`; las letras acentuadas cuentan como letra, no como símbolo, en el conteo de la contraseña | `pv04_nombreConAcentos_...` · `lasTildesSonLetras` |
| PV-05 | Ingresar símbolos o caracteres especiales | Aplicar las reglas definidas para el campo, sin producir errores inesperados | **Cumple.** `Juan<script>` se rechaza con el mensaje del campo, sin error 500; el texto vuelve escapado en el JSON | `pv05_nombreConSimbolos_...` |
| PV-06 | `vale.lor@tourinvest.com` | Aceptar el formato si cumple las reglas del sistema | **Cumple.** Formato aceptado en registro, login y recuperación (201 / 200) | `pv06_correoConDominio_...` |
| PV-07 | `vale.lor@tourinvest` | Rechazar por ausencia del formato esperado | **Corregido.** Antes el `@Email` de Hibernate lo aceptaba y terminaba en *"Correo o contraseña incorrectos"*. Ahora se rechaza por **formato**, con 400 y el mensaje del campo, sin llegar a comprobar credenciales | `pv07_correoSinExtension_...` (+ variantes de login y recuperación) |
| PV-08 | `vale.lor` | Rechazar si no cumple la validación de correo | **Cumple.** Se rechaza con un mensaje explícito: *'Incluye un signo "@" en la dirección de correo electrónico.'* | `pv08_correoSinArroba_...` · `validarCorreo` |
| PV-09 | Correo previamente registrado | Informar que ya existe si se implementó la validación de duplicados | **Cumple.** El servicio detecta el duplicado y responde 400 con *"Ya existe un usuario registrado con ese correo"*. Además el correo se normaliza a minúsculas, de modo que `Ana@Tourinvest.com` y `ana@tourinvest.com` cuentan como el mismo | `pv09_correoDuplicado_...` |
| PV-10 | Contraseña vacía | Rechazar si es un campo obligatorio | **Cumple.** El navegador muestra *"Completa este campo"* y la API responde 400 con *"La contraseña es obligatoria"* | `pv10_contrasenaVacia_...` |
| PV-11 | Contraseña inferior a la longitud mínima configurada | Rechazar si no cumple el mínimo | **Corregido.** Antes bastaban 6 caracteres. Ahora `AB12$cd34!x` (11) se rechaza indicando *"mínimo 12 caracteres"*; si además faltan otras reglas, el mensaje las enumera todas | `pv11_contrasenaCorta_...` · `PasswordPolicyTest` |
| PV-12 | Contraseña que cumple la longitud mínima | Aceptar si satisface las demás reglas | **Cumple.** `AB12$cd34!xy` (exactamente 12) y `TourInvest2026*!` se aceptan (201) | `pv12_contrasenaValida_...` |
| PV-13 | Texto que supera la longitud permitida de un campo | Rechazar o limitar según el comportamiento diseñado | **Cumple.** Límites declarados y verificados: nombre 30, apellido 100, cédula 20, correo 120, contraseña 72. El navegador no deja escribir de más (`maxlength`) y la API responde 400 si se saltan los atributos | `pv13_nombreDemasiadoLargo_...` · `pv13_contrasenaDemasiadoLarga_...` |
| PV-14 | Introducir letras en un campo exclusivamente numérico | Rechazar o impedir el dato según la regla implementada | **Cumple.** La cédula solo admite 6–20 dígitos: `1009a876543` se rechaza en el navegador (`pattern` + teclado numérico) y en la API | `pv14_cedulaConLetras_...` · `validarCedula` |
| PV-15 | Introducir un valor negativo en un campo que solo admite positivos | Rechazar si la regla del negocio no permite valores negativos | **No aplica** en registro/login: no hay campos numéricos con signo en estos formularios (la cédula es un identificador de 6–20 dígitos, sin rango de negocio). Los campos numéricos del sistema (cantidad de inversión, activos y pasivos) usan `min` en el HTML y se validan en los DTO de sus endpoints | — |
| PV-16 | Ingresar una fecha con formato incorrecto | Mostrar una validación si el campo controla el formato | **Cumple.** El campo `date` solo admite `AAAA-MM-DD`; si la API recibe `20/03/1999` responde 400 con *"Los datos enviados no son válidos. Revisa el formato de los campos…"* | `pv16_fechaConFormatoInvalido_...` |
| PV-17 | Ingresar una fecha inexistente o fuera del rango permitido | Rechazar si se implementó esa regla | **Cumple.** `2023-02-30` (30 de febrero) y las fechas futuras como `2999-01-01` se rechazan con 400 | `pv17_fechaInexistente_...` · `pv17_fechaFutura_...` |

---

## 3. Pruebas funcionales (PF)

| Prueba | Funcionalidad evaluada | Resultado obtenido | Estado |
|---|---|---|---|
| PF-01 | Visualización de la página principal | La portada carga su maquetación y estilos (`index.html` + `landing.css`) sin errores de consola | **APROBADA** |
| PF-02 | Registro con datos válidos | `POST /auth/registro` con nombre con tilde y espacio, cédula numérica, fecha válida, correo con dominio y contraseña que cumple la política → **201**, y la interfaz confirma *"Cuenta creada correctamente"* | **APROBADA** |
| PF-03 | Registro con datos inválidos | Los errores se pintan bajo cada campo y **no** se realiza la petición; si se fuerza la llamada, la API responde 400 con el detalle por campo | **APROBADA** |
| PF-04 | Inicio de sesión con credenciales válidas | `POST /auth/login` → 200 con token, nombre y rol; redirige al dashboard del rol. Las contraseñas antiguas de 6 caracteres siguen funcionando | **APROBADA** |
| PF-05 | Inicio de sesión con credenciales incorrectas | 401 con *"Correo o contraseña incorrectos"*; un correo mal formado se rechaza antes, con 400 y el mensaje de formato | **APROBADA** |
| PF-06 | Recuperación de contraseña | `POST /auth/recuperar` → 200 con mensaje genérico (no revela si el correo existe). **El envío del correo no está implementado**: requiere configurar un servidor SMTP | **NO IMPLEMENTADA** (parcial) |
| PF-07 | Navegación por las interfaces disponibles | Los tres dashboards cargan su driver, cambian de vista por el menú lateral y exigen el token para las consultas a la API | **APROBADA** |
| PV-01 a PV-17 | Validaciones de los campos aplicables | 16 de 17 casos automatizados y conformes; PV-15 no aplica a estos formularios | **APROBADA** |

---

## 4. Observaciones e incidencias

| Código | Descripción de la incidencia | Acción realizada o propuesta | Estado |
|---|---|---|---|
| INC-01 | La contraseña solo exigía 6 caracteres, sin reglas de complejidad (incumple PV-11 y PV-12) | Se creó `validation/PasswordPolicy.java` (12/2/2/2/2) con la anotación `@ContrasenaSegura`, aplicada al registro, al panel de administrador y al cambio de contraseña. Se añadió el checklist de reglas en vivo en `registro.html` | **CORREGIDA** |
| INC-02 | `vale.lor@tourinvest` se aceptaba como correo válido (fallo de PV-07): el mensaje que veía el usuario era "Correo o contraseña incorrectos" | Se sustituyó `@Email` por la restricción propia `@CorreoValido`, que exige extensión de dominio. Aplicada en registro, login, recuperación, perfil y panel de administrador | **CORREGIDA** |
| INC-03 | Un campo obligatorio con solo espacios generaba dos mensajes encadenados ("…Solo se permiten letras… El nombre es obligatorio") | Se creó `@FormatoValido`, que considera válido el valor vacío y deja que `@NotBlank` informe de la obligatoriedad: un error, una causa | **CORREGIDA** |
| INC-04 | Un JSON con una fecha imposible ("2023-02-30") devolvía la página de error por defecto de Spring, sin el campo `mensaje` que el frontend espera | Se añadió el manejador `HttpMessageNotReadableException` en `GlobalExceptionHandler`: responde 400 con un mensaje legible | **CORREGIDA** |
| INC-05 | Si un campo incumplía varias reglas a la vez, el manejador global solo conservaba el último mensaje (los demás se perdían en el `put` del mapa) | Se acumulan con `Map.merge`, de modo que el usuario ve todos los requisitos incumplidos en una sola línea | **CORREGIDA** |
| INC-06 | El mensaje "Las contraseñas no coinciden" se devolvía como texto plano, no como JSON, así que la interfaz mostraba un texto genérico en vez del motivo | `AuthController.registrar` responde ahora `{"mensaje": "Las contraseñas no coinciden."}` | **CORREGIDA** |
| INC-07 | El correo se guardaba sin normalizar: `Ana@TourInvest.com` y `ana@tourinvest.com` podían registrarse como dos cuentas distintas | `AuthController.registrar` normaliza el correo a minúsculas y recorta los espacios de nombre, apellido y cédula | **CORREGIDA** |
| INC-08 | Los mensajes de error de un campo quedaban visibles después de corregirlo | `limpiarErroresDeCampos` borra también el texto del mensaje, no solo la clase | **CORREGIDA** |
| INC-09 | Los formularios de login y recuperación no tenían dónde mostrar el error por campo | Se añadió el contenedor `.field__mensaje-error` en `login.html` y `recuperar.html` | **CORREGIDA** |
| INC-10 | Los paneles no avisan de la política antes de enviar (el error llega del servidor tras un viaje de ida y vuelta) | `minlength="12" maxlength="72"` en los campos de contraseña de los tres dashboards. La validación completa en cliente de esos formularios queda como mejora propuesta: reutilizar `TourInvestAuth.validarContrasena` desde `perfil.js` y `usuarios.js` | **PENDIENTE** (no bloqueante) |
| INC-11 | El envío del correo de recuperación no existe (PF-06) | Requiere configurar un servidor SMTP y un token de un solo uso. La API ya responde el mensaje genérico correcto | **PENDIENTE** |

> Durante la ejecución de las pruebas automatizadas **no se identificaron
> errores en los casos evaluados**: las cuatro suites quedan en verde
> (143 pruebas JUnit · 16 pruebas Python · `auth_validation_test.js` en Node).

---

## 5. Cómo reproducir

```bash
cd backend-servlets && mvn test                                # 143 pruebas
python3 -m unittest discover -s frontend/tests -p 'test_*.py'  # 16 pruebas
node frontend/tests/auth_validation_test.js                    # PV-01 a PV-17
```

Prueba manual sobre la aplicación real (requiere MySQL y la API levantada):

```bash
sudo systemctl start mysql && ./setup.sh run
```

La colección de Postman (`tourinvest_postman_collection.json`) cubre además los
35 casos de la API; sus cuerpos ya usan una contraseña que cumple la política.


git add .
git commit -m "Se Corrige formulario de registro y se hacen validaciones con postman"
git push origin main