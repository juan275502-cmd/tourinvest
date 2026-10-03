package com.tourinvest.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.RolRepository;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validaciones de la API para los casos PV-01 a PV-17 del protocolo de pruebas.
 *
 * <p>Cada test replica el "dato de prueba" de la tabla y comprueba el estado y
 * el mensaje devueltos. Al pasar por la capa web ({@code @WebMvcTest}) se ejecuta
 * la validación real de Bean Validation, no una simulación.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController - validaciones PV-01 a PV-17")
class AuthControllerValidacionTest {

    /** 16 caracteres con 2 mayúsculas, 2 minúsculas, 4 números y 2 especiales. */
    private static final String CLAVE_VALIDA = "TourInvest2026*!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    @MockBean
    private RolRepository rolRepository;

    /** Cuerpo de registro válido; cada test altera solo el campo que le importa. */
    private Map<String, String> registroValido() {
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre1", "Ana María");
        cuerpo.put("apellido1", "Núñez Pérez");
        cuerpo.put("cedula", "1009876543");
        cuerpo.put("fechaNacimiento", "1999-03-20");
        cuerpo.put("correo", "vale.lor@tourinvest.com");
        cuerpo.put("contrasena", CLAVE_VALIDA);
        cuerpo.put("confirmarContrasena", CLAVE_VALIDA);
        return cuerpo;
    }

    /** Ejecuta el POST de registro y devuelve el resultado para encadenar aserciones. */
    private ResultActions registrar(Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post("/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions enviar(String url, Map<String, String> cuerpo) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    /** Simula el alta exitosa para los casos "APROBADA". */
    private void stubRegistroExitoso() {
        when(authService.registrar(any(), any())).thenAnswer(inv -> {
            Usuario u = new Usuario();
            u.setCorreo("vale.lor@tourinvest.com");
            return u;
        });
    }

    // ---------- PV-01 / PV-02: obligatorios y espacios ----------

    @Test
    @DisplayName("PV-01: registro sin ningún campo devuelve 400 con un error por campo obligatorio")
    void pv01_sinCampos_devuelve400ConTodosLosErrores() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre1").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errores.apellido1").value("El apellido es obligatorio"))
                .andExpect(jsonPath("$.errores.cedula").value("La cédula es obligatoria"))
                .andExpect(jsonPath("$.errores.fechaNacimiento").value("La fecha de nacimiento es obligatoria"))
                .andExpect(jsonPath("$.errores.correo").value("El correo es obligatorio"))
                .andExpect(jsonPath("$.errores.contrasena").value("La contraseña es obligatoria"));

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("PV-02: un campo obligatorio con solo espacios se rechaza como vacío")
    void pv02_soloEspacios_devuelve400() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("nombre1", "   ");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre1").value("El nombre es obligatorio"));

        verify(authService, never()).registrar(any(), any());
    }

    // ---------- PV-03 / PV-04 / PV-05: reglas del campo nombre ----------

    @Test
    @DisplayName("PV-03: un nombre con espacios entre palabras se acepta")
    void pv03_nombreConEspacios_seAcepta() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("nombre1", "Ana María");
        stubRegistroExitoso();

        registrar(cuerpo).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PV-04: nombres con tildes, eñes y diéresis se aceptan")
    void pv04_nombreConAcentos_seAcepta() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("nombre1", "Ángela");
        cuerpo.put("apellido1", "Núñez-Öchoa");
        stubRegistroExitoso();

        registrar(cuerpo).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PV-05: símbolos y etiquetas HTML en el nombre se rechazan")
    void pv05_nombreConSimbolos_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("nombre1", "Juan<script>");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre1").value(containsString("letras")));

        verify(authService, never()).registrar(any(), any());
    }
// ---------- PV-06 / PV-07 / PV-08 / PV-09: correo ----------

    @Test
    @DisplayName("PV-06: vale.lor@tourinvest.com cumple el formato y se registra")
    void pv06_correoConDominio_seAcepta() throws Exception {
        stubRegistroExitoso();

        registrar(registroValido()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PV-07: vale.lor@tourinvest se rechaza por falta de extensión de dominio")
    void pv07_correoSinExtension_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("correo", "vale.lor@tourinvest");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("PV-08: vale.lor se rechaza porque no incluye el @")
    void pv08_correoSinArroba_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("correo", "vale.lor");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("PV-09: si el correo ya existe, se informa que está duplicado")
    void pv09_correoDuplicado_informaDuplicado() throws Exception {
        when(authService.registrar(any(), any()))
                .thenThrow(new IllegalArgumentException("Ya existe un usuario registrado con ese correo"));

        registrar(registroValido())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Ya existe un usuario registrado con ese correo"));
    }

    // ---------- Login y recuperación: mismo criterio de correo ----------

    @Test
    @DisplayName("PV-07 en login: vale.lor@tourinvest se rechaza por formato, no por credenciales")
    void pv07_loginCorreoSinExtension_seRechazaPorFormato() throws Exception {
        enviar("/auth/login", Map.of("correo", "vale.lor@tourinvest", "contrasena", "123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("PV-07 en recuperación: vale.lor@tourinvest se rechaza por formato")
    void pv07_recuperarCorreoSinExtension_seRechaza() throws Exception {
        enviar("/auth/recuperar", Map.of("correo", "vale.lor@tourinvest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());

        verify(authService, never()).solicitarRecuperacion(any());
    }

    @Test
    @DisplayName("PF-04: el login sigue aceptando las contraseñas antiguas de 6 caracteres")
    void login_noExigeComplejidad_enLaContrasena() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Correo o contraseña incorrectos"));

        enviar("/auth/login", Map.of("correo", "kike@tourinvest.com", "contrasena", "123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }
// ---------- PV-10 / PV-11 / PV-12: contraseña ----------

    @Test
    @DisplayName("PV-10: contraseña vacía se rechaza como campo obligatorio")
    void pv10_contrasenaVacia_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("contrasena", "");
        cuerpo.put("confirmarContrasena", "");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").value("La contraseña es obligatoria"));

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("PV-11: contraseña de 11 caracteres se rechaza aunque cumpla las demás reglas")
    void pv11_contrasenaCorta_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        // 11 caracteres: 2 mayúsculas, 2 minúsculas, 4 dígitos y 2 especiales.
        cuerpo.put("contrasena", "AB12$cd34!x");
        cuerpo.put("confirmarContrasena", "AB12$cd34!x");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("mínimo 12 caracteres")));

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("PV-11: sin símbolos ni números, el mensaje enumera TODOS los requisitos incumplidos")
    void pv11_sinSimbolosNiNumeros_listaTodosLosFallos() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("contrasena", "abcdefghijkl");
        cuerpo.put("confirmarContrasena", "abcdefghijkl");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("2 letras mayúsculas")))
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("2 números")))
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("2 caracteres especiales")));
    }

    @Test
    @DisplayName("PV-11: sin minúsculas ni especiales se enumeran los dos fallos")
    void pv11_sinMinusculasNiEspeciales_listaLosDosFallos() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("contrasena", "ABCDEFGHIJ12");
        cuerpo.put("confirmarContrasena", "ABCDEFGHIJ12");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("2 letras minúsculas")))
                .andExpect(jsonPath("$.errores.contrasena").value(containsString("2 caracteres especiales")));
    }

    @Test
    @DisplayName("PV-12: contraseña de 12 caracteres que cumple la política se acepta")
    void pv12_contrasenaValida_seAcepta() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("contrasena", "AB12$cd34!xy"); // justo 12 caracteres
        cuerpo.put("confirmarContrasena", "AB12$cd34!xy");
        stubRegistroExitoso();

        registrar(cuerpo).andExpect(status().isCreated());
    }
// ---------- PV-13: textos que superan la longitud permitida ----------

    @Test
    @DisplayName("PV-13: un nombre de más de 30 caracteres se rechaza")
    void pv13_nombreDemasiadoLargo_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("nombre1", "NombreExtremadamenteLargoParaElCampo");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre1").value("El nombre no puede superar 30 caracteres"));
    }

    @Test
    @DisplayName("PV-13: una contraseña de más de 72 caracteres se rechaza")
    void pv13_contrasenaDemasiadoLarga_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        String larga = CLAVE_VALIDA + "a".repeat(70);
        cuerpo.put("contrasena", larga);
        cuerpo.put("confirmarContrasena", larga);

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena").exists());
    }

    // ---------- PV-14: cédula exclusivamente numérica ----------

    @Test
    @DisplayName("PV-14: letras en la cédula se rechazan")
    void pv14_cedulaConLetras_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("cedula", "1009a876543");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cedula")
                        .value("La cédula debe tener entre 6 y 20 dígitos numéricos."));

        verify(authService, never()).registrar(any(), any());
    }

    // ---------- PV-16 / PV-17: fecha ----------

    @Test
    @DisplayName("PV-16: una fecha con formato incorrecto devuelve 400 con mensaje legible")
    void pv16_fechaConFormatoInvalido_devuelve400() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", "20/03/1999");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PV-17: una fecha inexistente (30 de febrero) devuelve 400 con mensaje")
    void pv17_fechaInexistente_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", "2023-02-30");

        // Jackson no puede convertir "2023-02-30" a LocalDate, así que la API
        // responde 400 con el mensaje de cuerpo ilegible.
        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PV-17: una fecha de nacimiento futura se rechaza")
    void pv17_fechaFutura_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", "2999-01-01");

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento")
                        .value("La fecha de nacimiento debe ser anterior a hoy"));
    }

    // ---------- Mayor de edad (18 años) ----------

    @Test
    @DisplayName("Edad: un menor de 17 años se rechaza indicando que debe ser mayor de edad")
    void edad_menorDeDieciseis_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", LocalDate.now().minusYears(17).toString());

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento")
                        .value("Debes ser mayor de edad para registrarte (mínimo 18 años)"));

        verify(authService, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("Edad: quien cumple 18 años mañana todavía se rechaza")
    void edad_cumpleDiecisechoMañana_seRechaza() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", LocalDate.now().minusYears(18).plusDays(1).toString());

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento").exists());
    }

    @Test
    @DisplayName("Edad: quien cumple 18 años hoy ya puede registrarse")
    void edad_cumpleDiecisechoHoy_seAcepta() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", LocalDate.now().minusYears(18).toString());
        stubRegistroExitoso();

        registrar(cuerpo).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Edad: la fecha de hoy se rechaza por el @Past, no por la edad (un solo mensaje)")
    void edad_fechaDeHoy_mensajeDePast() throws Exception {
        Map<String, String> cuerpo = registroValido();
        cuerpo.put("fechaNacimiento", LocalDate.now().toString());

        registrar(cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaNacimiento")
                        .value("La fecha de nacimiento debe ser anterior a hoy"));
    }
}