package com.tourinvest.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourinvest.backend.dto.LoginResponse;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // /auth/** es público; desactivamos filtros para aislar la capa web
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // JwtAuthFilter es un Filter y Spring lo instancia igual dentro del slice de @WebMvcTest,
    // aunque addFilters=false evite que se ejecute. Sin estos mocks el contexto no levanta.
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    // ---------- LOGIN ----------

    @Test
    @DisplayName("POST /auth/login: con credenciales válidas responde 200 con token, nombre y rol")
    void login_credencialesValidas_devuelve200() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("token-abc", "Juan", "kike@tourinvest.com", "Inversionista"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "correo", "kike@tourinvest.com",
                                "contrasena", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-abc"))
                .andExpect(jsonPath("$.nombre1").value("Juan"))
                .andExpect(jsonPath("$.rol").value("Inversionista"));
    }

    @Test
    @DisplayName("POST /auth/login: con correo en formato inválido responde 400 con el error de validación")
    void login_correoInvalido_devuelve400ConErroresDeValidacion() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "correo", "no-es-un-correo",
                                "contrasena", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());
    }

    @Test
    @DisplayName("POST /auth/login: si el servicio lanza BadCredentialsException, responde 401")
    void login_credencialesInvalidas_devuelve401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Correo o contraseña incorrectos"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "correo", "kike@tourinvest.com",
                                "contrasena", "clave-mala"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Correo o contraseña incorrectos"));
    }

    // ---------- REGISTRO ----------

    @Test
    @DisplayName("POST /auth/registro: con datos válidos responde 201")
    void registro_datosValidos_devuelve201() throws Exception {
        when(authService.registrar(any(), any())).thenAnswer(invocation -> {
            var usuario = new com.tourinvest.backend.model.Usuario();
            usuario.setCorreo("kike@tourinvest.com");
            return usuario;
        });

        mockMvc.perform(post("/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nombre1", "Ana",
                                "apellido1", "Torres",
                                "cedula", "1112223334",
                                "fechaNacimiento", LocalDate.of(1999, 3, 20).toString(),
                                "correo", "kike@tourinvest.com",
                                "contrasena", "123456",
                                "confirmarContrasena", "123456"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /auth/registro: si las contraseñas no coinciden, responde 400 sin llamar al servicio")
    void registro_contrasenasNoCoinciden_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nombre1", "Ana",
                                "apellido1", "Torres",
                                "cedula", "1112223334",
                                "fechaNacimiento", LocalDate.of(1999, 3, 20).toString(),
                                "correo", "kike@tourinvest.com",
                                "contrasena", "123456",
                                "confirmarContrasena", "otraClave"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Las contraseñas no coinciden"));
    }

    @Test
    @DisplayName("POST /auth/registro: con campos faltantes responde 400 con el detalle de cada error")
    void registro_camposFaltantes_devuelve400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/auth/registro")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "correo", "kike@tourinvest.com",
                                "contrasena", "123456",
                                "confirmarContrasena", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre1").exists())
                .andExpect(jsonPath("$.errores.cedula").exists());
    }

    // ---------- RECUPERAR CONTRASEÑA ----------

    @Test
    @DisplayName("POST /auth/recuperar: con correo válido responde 200 con mensaje genérico")
    void recuperar_correoValido_devuelve200ConMensajeGenerico() throws Exception {
        mockMvc.perform(post("/auth/recuperar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("correo", "kike@tourinvest.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(
                        "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."));

        verify(authService).solicitarRecuperacion("kike@tourinvest.com");
    }

    @Test
    @DisplayName("POST /auth/recuperar: con correo en formato inválido responde 400")
    void recuperar_correoInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/auth/recuperar")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("correo", "no-es-un-correo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").exists());

        verify(authService, never()).solicitarRecuperacion(any());
    }
}