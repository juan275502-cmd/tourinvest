package com.tourinvest.backend.controller;

import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import com.tourinvest.backend.service.AdministradorService;

@WebMvcTest(AdministradorController.class)
@AutoConfigureMockMvc(addFilters = false) // capa web aislada; la cadena JWT real ya está cubierta por JwtUtilTest
@DisplayName("AdministradorController")
class AdministradorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdministradorService administradorService;

    // Requeridos indirectamente por JwtAuthFilter (ver nota en AuthControllerTest)
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UsuarioRepository usuarioRepository;

    private Usuario administradorAutenticado;

    @BeforeEach
    void setUp() {
        Rol rolAdmin = new Rol(Rol.NombreRol.Administrador);
        rolAdmin.setIdRol(1);

        administradorAutenticado = new Usuario();
        administradorAutenticado.setIdUsuario(1);
        administradorAutenticado.setNombre1("Carlos");
        administradorAutenticado.setCorreo("carlos@tourinvest.com");
        administradorAutenticado.setContrasena("$2a$10$hash");
        administradorAutenticado.setRol(rolAdmin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(administradorAutenticado, null, administradorAutenticado.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /admin/usuarios: responde 200 con la lista de usuarios")
    void listar_devuelve200ConListaDeUsuarios() throws Exception {
        when(administradorService.listarUsuarios()).thenReturn(List.of(
                new UsuarioResumenDTO(3, "Juan Fuentes", "juan@tourinvest.com", "Inversionista", "Activo", LocalDateTime.now())));

        mockMvc.perform(get("/admin/usuarios").with(user(administradorAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rol").value("Inversionista"));
    }

    @Test
    @DisplayName("PATCH /admin/usuarios/{id}/suspender: con usuario distinto, responde 200")
    void suspender_usuarioDistinto_devuelve200() throws Exception {
        when(administradorService.suspender(administradorAutenticado, 3)).thenReturn(
                new UsuarioResumenDTO(3, "Juan Fuentes", "juan@tourinvest.com", "Inversionista", "Inactivo", LocalDateTime.now()));

        mockMvc.perform(patch("/admin/usuarios/3/suspender").with(user(administradorAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Inactivo"));
    }

    @Test
    @DisplayName("PATCH /admin/usuarios/{id}/suspender: al intentar auto-suspenderse, responde 400")
    void suspender_autoSuspension_devuelve400() throws Exception {
        when(administradorService.suspender(administradorAutenticado, 1))
                .thenThrow(new IllegalStateException("No puedes suspender tu propia cuenta de administrador"));

        mockMvc.perform(patch("/admin/usuarios/1/suspender").with(user(administradorAutenticado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("No puedes suspender tu propia cuenta de administrador"));
    }

    @Test
    @DisplayName("PATCH /admin/usuarios/{id}/activar: con usuario existente, responde 200")
    void activar_usuarioExistente_devuelve200() throws Exception {
        when(administradorService.activar(3)).thenReturn(
                new UsuarioResumenDTO(3, "Juan Fuentes", "juan@tourinvest.com", "Inversionista", "Activo", LocalDateTime.now()));

        mockMvc.perform(patch("/admin/usuarios/3/activar").with(user(administradorAutenticado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("Activo"));
    }

    @Test
    @DisplayName("PATCH /admin/usuarios/{id}/activar: con usuario inexistente, responde 404")
    void activar_usuarioInexistente_devuelve404() throws Exception {
        when(administradorService.activar(999)).thenThrow(new NoSuchElementException("Usuario no encontrado"));

        mockMvc.perform(patch("/admin/usuarios/999/activar").with(user(administradorAutenticado)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));
    }
}