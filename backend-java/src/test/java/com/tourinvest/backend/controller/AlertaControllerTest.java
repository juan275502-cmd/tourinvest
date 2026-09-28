package com.tourinvest.backend.controller;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourinvest.backend.dto.AlertaResumenDTO;
import com.tourinvest.backend.model.Alerta;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.AlertaService;

@WebMvcTest(AlertaController.class)
@AutoConfigureMockMvc(addFilters = false) // capa web aislada; la cadena JWT real ya está cubierta por JwtUtilTest
@DisplayName("AlertaController")
class AlertaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertaService alertaService;

    // Requeridos indirectamente por JwtAuthFilter (ver nota en AuthControllerTest)
    @MockBean
    @SuppressWarnings("unused")
    private JwtUtil jwtUtil;

    @MockBean
    @SuppressWarnings("unused")
    private UsuarioRepository usuarioRepository;

    private Usuario usuarioAutenticado;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        Rol rolInversionista = new Rol(Rol.NombreRol.Inversionista);
        rolInversionista.setIdRol(3);

        usuarioAutenticado = new Usuario();
        usuarioAutenticado.setIdUsuario(3);
        usuarioAutenticado.setNombre1("Carlos");
        usuarioAutenticado.setCorreo("carlos@tourinvest.com");
        usuarioAutenticado.setContrasena("$2a$10$hash");
        usuarioAutenticado.setRol(rolInversionista);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuarioAutenticado, null, usuarioAutenticado.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /inversionista/alertas: responde 200 con las alertas del usuario autenticado")
    void listar_devuelve200ConAlertasDelUsuario() throws Exception {
        when(alertaService.listarPorUsuario(eq(usuarioAutenticado))).thenReturn(List.of(
                new AlertaResumenDTO(1, "AAPL", new BigDecimal("210.00"), new BigDecimal("195.50"), Alerta.EstadoAlerta.Activa)));

        mockMvc.perform(get("/inversionista/alertas").with(user(usuarioAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].simbolo").value("AAPL"))
                .andExpect(jsonPath("$[0].estado").value("Activa"));
    }

    @Test
    @DisplayName("POST /inversionista/alertas: con datos válidos responde 201 con la alerta creada")
    void crear_datosValidos_devuelve201() throws Exception {
        when(alertaService.crear(eq(usuarioAutenticado), any())).thenReturn(
                new AlertaResumenDTO(5, "TSLA", new BigDecimal("300.00"), new BigDecimal("250.80"), Alerta.EstadoAlerta.Activa));

        mockMvc.perform(post("/inversionista/alertas")
                        .with(user(usuarioAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idAccion", 4, "precioObjetivo", 300.00))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simbolo").value("TSLA"))
                .andExpect(jsonPath("$.precioObjetivo").value(300.00));
    }

    @Test
    @DisplayName("POST /inversionista/alertas: con precio objetivo en cero, responde 400 sin llamar al servicio")
    void crear_precioObjetivoInvalido_devuelve400() throws Exception {
        mockMvc.perform(post("/inversionista/alertas")
                        .with(user(usuarioAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idAccion", 4, "precioObjetivo", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioObjetivo").exists());

        verify(alertaService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("PATCH /inversionista/alertas/{id}/cancelar: con alerta propia, responde 204")
    void cancelar_alertaPropia_devuelve204() throws Exception {
        mockMvc.perform(patch("/inversionista/alertas/1/cancelar").with(user(usuarioAutenticado)))
                .andExpect(status().isNoContent());

        verify(alertaService).cancelar(usuarioAutenticado, 1);
    }

    @Test
    @DisplayName("PATCH /inversionista/alertas/{id}/cancelar: si la alerta es de otro usuario, responde 400")
    void cancelar_alertaAjena_devuelve400() throws Exception {
        doThrow(new IllegalStateException("No puedes cancelar una alerta de otro usuario"))
                .when(alertaService).cancelar(usuarioAutenticado, 99);

        mockMvc.perform(patch("/inversionista/alertas/99/cancelar").with(user(usuarioAutenticado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No puedes cancelar una alerta de otro usuario"));
    }

}