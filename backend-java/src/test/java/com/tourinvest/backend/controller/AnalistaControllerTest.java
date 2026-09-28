package com.tourinvest.backend.controller;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourinvest.backend.dto.IndicadorLiquidezResponse;
import com.tourinvest.backend.dto.ReporteResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.ReporteService;

@WebMvcTest(AnalistaController.class)
@AutoConfigureMockMvc(addFilters = false) // capa web aislada; la cadena JWT real ya está cubierta por JwtUtilTest
@DisplayName("AnalistaController")
class AnalistaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReporteService reporteService;

    // Requeridos indirectamente por JwtAuthFilter (ver nota en AuthControllerTest)
    @MockBean
    @SuppressWarnings("unused")
    private JwtUtil jwtUtil;

    @MockBean
    @SuppressWarnings("unused")
    private UsuarioRepository usuarioRepository;

    private Usuario analistaAutenticado;

    @BeforeEach
    void setUp() {
        Rol rolAnalista = new Rol(Rol.NombreRol.Analista);
        rolAnalista.setIdRol(2);

        analistaAutenticado = new Usuario();
        analistaAutenticado.setIdUsuario(2);
        analistaAutenticado.setNombre1("Laura");
        analistaAutenticado.setCorreo("laura@tourinvest.com");
        analistaAutenticado.setContrasena("$2a$10$hash");
        analistaAutenticado.setRol(rolAnalista);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(analistaAutenticado, null, analistaAutenticado.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /analista/reportes: responde 200 con la lista de reportes")
    void listarReportes_devuelve200ConLista() throws Exception {
        when(reporteService.listarTodos()).thenReturn(List.of(
                new ReporteResumenDTO(1, "Reporte Apple", "Tendencia alcista", "Apple", "Laura", LocalDateTime.now())));

        mockMvc.perform(get("/analista/reportes").with(user(analistaAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombreEmpresa").value("Apple"));
    }

    @Test
    @DisplayName("POST /analista/reportes: con datos válidos responde 201")
    void crearReporte_datosValidos_devuelve201() throws Exception {
        when(reporteService.crear(eq(analistaAutenticado), any())).thenReturn(
                new ReporteResumenDTO(5, "Análisis Tesla", "Alta volatilidad", "Tesla", "Laura", LocalDateTime.now()));

        mockMvc.perform(post("/analista/reportes")
                        .with(user(analistaAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "idEmpresa", 4,
                                "titulo", "Análisis Tesla",
                                "descripcion", "Alta volatilidad"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombreEmpresa").value("Tesla"))
                .andExpect(jsonPath("$.autor").value("Laura"));
    }

    @Test
    @DisplayName("POST /analista/reportes: sin título, responde 400 por validación")
    void crearReporte_sinTitulo_devuelve400() throws Exception {
        mockMvc.perform(post("/analista/reportes")
                        .with(user(analistaAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("idEmpresa", 4))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.titulo").exists());
    }

    @Test
    @DisplayName("POST /analista/indicadores/liquidez: con valores válidos responde 200 con el resultado")
    void calcularLiquidez_valoresValidos_devuelve200() throws Exception {
        when(reporteService.calcularLiquidezCorriente(any()))
                .thenReturn(new IndicadorLiquidezResponse(new BigDecimal("1.2000")));

        mockMvc.perform(post("/analista/indicadores/liquidez")
                        .with(user(analistaAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activoCorriente", 45000,
                                "pasivoCorriente", 37500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquidezCorriente").value(1.2));
    }

    @Test
    @DisplayName("POST /analista/indicadores/liquidez: si el servicio lanza ArithmeticException, responde 400")
    void calcularLiquidez_pasivoCero_devuelve400() throws Exception {
        when(reporteService.calcularLiquidezCorriente(any()))
                .thenThrow(new ArithmeticException("El pasivo corriente no puede ser cero"));

        mockMvc.perform(post("/analista/indicadores/liquidez")
                        .with(user(analistaAutenticado))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "activoCorriente", 45000,
                                "pasivoCorriente", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El pasivo corriente no puede ser cero"));
    }
}