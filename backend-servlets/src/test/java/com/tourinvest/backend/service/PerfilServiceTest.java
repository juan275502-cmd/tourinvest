package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.ActualizarPerfilRequest;
import com.tourinvest.backend.dto.CambiarContrasenaRequest;
import com.tourinvest.backend.dto.PerfilDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * PerfilService: cada usuario edita SOLO su propio perfil.
 * El usuario llega del token JWT, nunca de un parametro, por eso los tests
 * fijan siempre el mismo usuario autenticado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerfilService")
class PerfilServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private PerfilService perfilService;
    private Usuario inversionista;

    @BeforeEach
    void setUp() {
        perfilService = new PerfilService(usuarioRepository, passwordEncoder);

        Rol rol = new Rol(Rol.NombreRol.Inversionista);
        rol.setIdRol(3);

        inversionista = new Usuario();
        inversionista.setIdUsuario(3);
        inversionista.setNombre1("Kike");
        inversionista.setApellido1("Aguirre");
        inversionista.setCedula("80760000");
        inversionista.setFechaNacimiento(LocalDate.of(1983, 8, 10));
        inversionista.setCorreo("kike@tourinvest.com");
        inversionista.setContrasena("$2b$10$hash");
        inversionista.setRol(rol);
        inversionista.setEstado(Usuario.EstadoUsuario.Activo);

        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ActualizarPerfilRequest peticionValida() {
        ActualizarPerfilRequest request = new ActualizarPerfilRequest();
        request.setNombre1("Kike");
        request.setApellido1("Aguirre Soto");
        request.setCedula("80760000");
        request.setFechaNacimiento(LocalDate.of(1983, 8, 10));
        request.setCorreo("kike@tourinvest.com");
        return request;
    }

    @Test
    @DisplayName("obtenerPerfil: devuelve los datos del usuario autenticado")
    void obtenerPerfil_mapeaCampos() {
        PerfilDTO dto = perfilService.obtenerPerfil(inversionista);

        assertThat(dto.getIdUsuario()).isEqualTo(3);
        assertThat(dto.getNombreCompleto()).isEqualTo("Kike Aguirre");
        assertThat(dto.getCorreo()).isEqualTo("kike@tourinvest.com");
        assertThat(dto.getRol()).isEqualTo("Inversionista");
        assertThat(dto.getEstado()).isEqualTo("Activo");
    }

    @Test
    @DisplayName("actualizarPerfil: guarda los datos nuevos y conserva rol y estado")
    void actualizarPerfil_actualizaDatosYConservaRol() {
        ActualizarPerfilRequest request = peticionValida();
        request.setCedula("80999999");
        request.setCorreo("kike.nuevo@tourinvest.com");
        when(usuarioRepository.existsByCorreoAndIdUsuarioNot(any(), anyInt())).thenReturn(false);
        when(usuarioRepository.existsByCedulaAndIdUsuarioNot(any(), anyInt())).thenReturn(false);

        PerfilDTO dto = perfilService.actualizarPerfil(inversionista, request);

        assertThat(dto.getApellido1()).isEqualTo("Aguirre Soto");
        assertThat(dto.getCedula()).isEqualTo("80999999");
        assertThat(dto.getCorreo()).isEqualTo("kike.nuevo@tourinvest.com");
        // Rol y estado NO se tocan: es territorio del Administrador.
        assertThat(dto.getRol()).isEqualTo("Inversionista");
        assertThat(dto.getEstado()).isEqualTo("Activo");
    }

    @Test
    @DisplayName("actualizarPerfil: el correo se normaliza a minusculas")
    void actualizarPerfil_normalizaCorreo() {
        ActualizarPerfilRequest request = peticionValida();
        request.setCorreo("Kike.Nuevo@TourInvest.COM");
        when(usuarioRepository.existsByCorreoAndIdUsuarioNot(any(), anyInt())).thenReturn(false);
        when(usuarioRepository.existsByCedulaAndIdUsuarioNot(any(), anyInt())).thenReturn(false);

        PerfilDTO dto = perfilService.actualizarPerfil(inversionista, request);

        assertThat(dto.getCorreo()).isEqualTo("kike.nuevo@tourinvest.com");
    }

    @Test
    @DisplayName("actualizarPerfil: si el correo ya existe en otro usuario, responde 400")
    void actualizarPerfil_correoDuplicado_lanzaError() {
        when(usuarioRepository.existsByCorreoAndIdUsuarioNot(any(), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> perfilService.actualizarPerfil(inversionista, peticionValida()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo");
    }

    @Test
    @DisplayName("actualizarPerfil: si la cedula ya existe en otro usuario, responde 400")
    void actualizarPerfil_cedulaDuplicada_lanzaError() {
        when(usuarioRepository.existsByCorreoAndIdUsuarioNot(any(), anyInt())).thenReturn(false);
        when(usuarioRepository.existsByCedulaAndIdUsuarioNot(any(), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> perfilService.actualizarPerfil(inversionista, peticionValida()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cédula");
    }
    @Test
    @DisplayName("cambiarContrasena: con la contrasena actual correcta, la cambia")
    void cambiarContrasena_actualCorrecta_cambia() {
        CambiarContrasenaRequest request = new CambiarContrasenaRequest();
        request.setContrasenaActual("123456");
        request.setContrasenaNueva("nuevaClave9");
        when(passwordEncoder.matches("123456", "$2b$10$hash")).thenReturn(true);
        when(passwordEncoder.encode("nuevaClave9")).thenReturn("$2b$10$hashNuevo");

        perfilService.cambiarContrasena(inversionista, request);

        verify(usuarioRepository).save(inversionista);
        assertThat(inversionista.getPassword()).isEqualTo("$2b$10$hashNuevo");
    }

    @Test
    @DisplayName("cambiarContrasena: con la contrasena actual incorrecta, responde 400")
    void cambiarContrasena_actualIncorrecta_lanzaError() {
        CambiarContrasenaRequest request = new CambiarContrasenaRequest();
        request.setContrasenaActual("malaClave");
        request.setContrasenaNueva("nuevaClave9");
        when(passwordEncoder.matches("malaClave", "$2b$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> perfilService.cambiarContrasena(inversionista, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actual");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarContrasena: no permite repetir la misma contrasena")
    void cambiarContrasena_iguales_lanzaError() {
        CambiarContrasenaRequest request = new CambiarContrasenaRequest();
        request.setContrasenaActual("123456");
        request.setContrasenaNueva("123456");
        when(passwordEncoder.matches("123456", "$2b$10$hash")).thenReturn(true);

        assertThatThrownBy(() -> perfilService.cambiarContrasena(inversionista, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinta");
    }
}
