package com.tourinvest.backend.controller;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourinvest.backend.dto.PosicionDTO;
import com.tourinvest.backend.dto.ResumenPortafolioResponse;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.PortafolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InversionistaController.class)
@AutoConfigureMockMvc(addFilters = false) // probamos el controlador, no la cadena real de JWT (ya cubierta por JwtUtilTest)
@DisplayName("InversionistaController")
class InversionistaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortafolioService portafolioService;

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
    @DisplayName("GET /inversionista/resumen: con usuario autenticado, responde 200 con el resumen del servicio")
    void obtenerResumen_usuarioAutenticado_devuelve200ConResumen() throws Exception {
        ResumenPortafolioResponse resumen = new ResumenPortafolioResponse(
                "Carlos",
                new BigDecimal("1955.00"),
                new BigDecimal("1800.00"),
                new BigDecimal("8.6111"),
                List.of(),
                List.of());

        when(portafolioService.obtenerResumen(any(Usuario.class))).thenReturn(resumen);

        mockMvc.perform(get("/inversionista/resumen").with(user(usuarioAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("Carlos"))
                .andExpect(jsonPath("$.valorTotal").value(1955.00))
                .andExpect(jsonPath("$.totalInvertido").value(1800.00));
    }

    @Test
    @DisplayName("POST /inversionista/portafolio/inversiones: con datos válidos responde 201 con la posición creada")
    void agregarInversion_datosValidos_devuelve201() throws Exception {
        PosicionDTO posicion = new PosicionDTO(
                "Apple", "AAPL", 10,
                new BigDecimal("195.50"), new BigDecimal("195.50"),
                new BigDecimal("1955.00"), BigDecimal.ZERO);

        when(portafolioService.agregarInversion(eq(usuarioAutenticado), any())).thenReturn(posicion);

        mockMvc.perform(post("/inversionista/portafolio/inversiones")
                        .with(user(usuarioAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idAccion", 1, "cantidad", 10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simbolo").value("AAPL"))
                .andExpect(jsonPath("$.cantidad").value(10));
    }

    @Test
    @DisplayName("POST /inversionista/portafolio/inversiones: con cantidad menor a 1, responde 400")
    void agregarInversion_cantidadInvalida_devuelve400() throws Exception {
        mockMvc.perform(post("/inversionista/portafolio/inversiones")
                        .with(user(usuarioAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idAccion", 1, "cantidad", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cantidad").exists());
    }

    @Test
    @DisplayName("POST /inversionista/portafolio/inversiones: si la acción no existe, responde 404")
    void agregarInversion_accionInexistente_devuelve404() throws Exception {
        when(portafolioService.agregarInversion(eq(usuarioAutenticado), any()))
                .thenThrow(new NoSuchElementException("La acción indicada no existe"));

        mockMvc.perform(post("/inversionista/portafolio/inversiones")
                        .with(user(usuarioAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idAccion", 999, "cantidad", 5))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La acción indicada no existe"));
    }
}