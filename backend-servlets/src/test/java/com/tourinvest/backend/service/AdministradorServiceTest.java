package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.UsuarioAdminRequest;
import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.RolRepository;
import com.tourinvest.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdministradorService")
class AdministradorServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdministradorService administradorService;

    private Usuario administrador;
    private Usuario inversionista;

    @BeforeEach
    void setUp() {
        // El servicio ahora recibe RolRepository y PasswordEncoder para el
        // CRUD de usuarios (crear/editar requiere hashear la contrasena).
        administradorService = new AdministradorService(usuarioRepository, rolRepository, passwordEncoder);

        Rol rolAdmin = new Rol(Rol.NombreRol.Administrador);
        rolAdmin.setIdRol(1);

        Rol rolInversionista = new Rol(Rol.NombreRol.Inversionista);
        rolInversionista.setIdRol(3);

        administrador = new Usuario();
        administrador.setIdUsuario(1);
        administrador.setNombre1("Carlos");
        administrador.setApellido1("Ruiz");
        administrador.setCorreo("juan@tourinvest.com");
        administrador.setRol(rolAdmin);
        administrador.setEstado(Usuario.EstadoUsuario.Activo);

        inversionista = new Usuario();
        inversionista.setIdUsuario(3);
        inversionista.setNombre1("Juan");
        inversionista.setApellido1("Fuentes");
        inversionista.setCorreo("kike@tourinvest.com");
        inversionista.setRol(rolInversionista);
        inversionista.setEstado(Usuario.EstadoUsuario.Activo);
    }

    @Test
    @DisplayName("listarUsuarios: mapea nombre completo, rol y estado correctamente")
    void listarUsuarios_devuelveListaMapeadaCorrectamente() {
        when(usuarioRepository.findAll()).thenReturn(List.of(administrador, inversionista));

        List<UsuarioResumenDTO> resultado = administradorService.listarUsuarios();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getNombreCompleto()).isEqualTo("Carlos Ruiz");
        assertThat(resultado.get(0).getRol()).isEqualTo("Administrador");
        assertThat(resultado.get(1).getEstado()).isEqualTo("Activo");
    }

    // ---------- SUSPENDER ----------

    @Test
    @DisplayName("suspender: con usuario distinto al administrador, cambia el estado a Inactivo")
    void suspender_usuarioDistinto_cambiaEstadoAInactivo() {
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResumenDTO resultado = administradorService.suspender(administrador, 3);

        assertThat(resultado.getEstado()).isEqualTo("Inactivo");
        verify(usuarioRepository).save(argThat(u -> u.getEstado() == Usuario.EstadoUsuario.Inactivo));
    }

    @Test
    @DisplayName("suspender: si el administrador intenta suspenderse a sí mismo, lanza IllegalStateException")
    void suspender_intentaAutoSuspenderse_lanzaIllegalStateException() {
        assertThatThrownBy(() -> administradorService.suspender(administrador, administrador.getIdUsuario()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No puedes suspender tu propia cuenta de administrador");

        verify(usuarioRepository, never()).findById(any());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspender: con usuario inexistente, lanza NoSuchElementException")
    void suspender_usuarioInexistente_lanzaNoSuchElementException() {
        when(usuarioRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administradorService.suspender(administrador, 999))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Usuario no encontrado");
    }

    // ---------- ACTIVAR ----------

    @Test
    @DisplayName("activar: cambia el estado del usuario a Activo")
    void activar_usuarioExistente_cambiaEstadoAActivo() {
        inversionista.setEstado(Usuario.EstadoUsuario.Inactivo);
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioResumenDTO resultado = administradorService.activar(3);

        assertThat(resultado.getEstado()).isEqualTo("Activo");
    }

    @Test
    @DisplayName("activar: con usuario inexistente, lanza NoSuchElementException")
    void activar_usuarioInexistente_lanzaNoSuchElementException() {
        when(usuarioRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> administradorService.activar(999))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Usuario no encontrado");
    }

    // ---------- CRUD de usuarios ----------

    private UsuarioAdminRequest peticionCrear() {
        UsuarioAdminRequest request = new UsuarioAdminRequest();
        request.setNombre1("Ana");
        request.setApellido1("Restrepo");
        request.setCedula("1009876543");
        request.setFechaNacimiento(java.time.LocalDate.of(1997, 4, 12));
        request.setCorreo("ana@tourinvest.com");
        request.setContrasena("123456");
        request.setRol(Rol.NombreRol.Analista);
        return request;
    }

    private UsuarioAdminRequest peticionEditar(String correo) {
        UsuarioAdminRequest request = new UsuarioAdminRequest();
        request.setNombre1("Kike");
        request.setApellido1("Aguirre");
        request.setCedula("80760000");
        request.setFechaNacimiento(java.time.LocalDate.of(1983, 8, 10));
        request.setCorreo(correo);
        request.setContrasena("");
        request.setRol(Rol.NombreRol.Inversionista);
        return request;
    }

    @Test
    @DisplayName("crear: hashea la contraseña, asigna el rol elegido y queda Activo")
    void crear_hasheaContrasenaYAsignaRol() {
        Rol rolAnalista = new Rol(Rol.NombreRol.Analista);
        rolAnalista.setIdRol(2);
        when(rolRepository.findByNombre(Rol.NombreRol.Analista)).thenReturn(Optional.of(rolAnalista));
        when(passwordEncoder.encode("123456")).thenReturn("$2b$10$hashNuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResumenDTO dto = administradorService.crear(peticionCrear());

        assertThat(dto.getCorreo()).isEqualTo("ana@tourinvest.com");
        assertThat(dto.getRol()).isEqualTo("Analista");
        assertThat(dto.getEstado()).isEqualTo("Activo");
        verify(passwordEncoder).encode("123456");
    }

    @Test
    @DisplayName("crear: sin contraseña responde 400")
    void crear_sinContrasena_lanzaError() {
        UsuarioAdminRequest request = peticionCrear();
        request.setContrasena("");

        assertThatThrownBy(() -> administradorService.crear(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contraseña es obligatoria");
    }

    @Test
    @DisplayName("crear: correo duplicado responde 400")
    void crear_correoDuplicado_lanzaError() {
        when(usuarioRepository.existsByCorreo("ana@tourinvest.com")).thenReturn(true);

        assertThatThrownBy(() -> administradorService.crear(peticionCrear()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ese correo");
    }

    @Test
    @DisplayName("actualizar: si no viene contraseña, el usuario conserva la suya")
    void actualizar_sinContrasena_conservaLaActual() {
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(rolRepository.findByNombre(Rol.NombreRol.Inversionista))
                .thenReturn(Optional.of(inversionista.getRol()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        String hashOriginal = inversionista.getPassword();

        administradorService.actualizar(3, peticionEditar("kike@tourinvest.com"));

        assertThat(inversionista.getPassword()).isEqualTo(hashOriginal);
        verify(passwordEncoder, never()).encode(any());
    }
    @Test
    @DisplayName("actualizar: si viene contraseña nueva, la hashea y la cambia")
    void actualizar_conContrasena_laCambia() {
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(rolRepository.findByNombre(Rol.NombreRol.Inversionista))
                .thenReturn(Optional.of(inversionista.getRol()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode("claveNueva9")).thenReturn("$2b$10$hashNuevo");

        UsuarioAdminRequest request = peticionEditar("kike@tourinvest.com");
        request.setContrasena("claveNueva9");
        administradorService.actualizar(3, request);

        verify(passwordEncoder).encode("claveNueva9");
        assertThat(inversionista.getPassword()).isEqualTo("$2b$10$hashNuevo");
    }

    @Test
    @DisplayName("eliminar: usuario sin datos asociados se borra")
    void eliminar_sinDatosAsociados_borra() {
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(usuarioRepository.existsByIdUsuario_IdUsuario(3)).thenReturn(false);

        administradorService.eliminar(3);

        verify(usuarioRepository).delete(inversionista);
    }

    @Test
    @DisplayName("eliminar: con datos asociados responde 400 y no borra")
    void eliminar_conDatosAsociados_lanzaError() {
        when(usuarioRepository.findById(3)).thenReturn(Optional.of(inversionista));
        when(usuarioRepository.existsByIdUsuario_IdUsuario(3)).thenReturn(true);

        assertThatThrownBy(() -> administradorService.eliminar(3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("datos asociados");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar: el último Administrador no se puede borrar")
    void eliminar_ultimoAdmin_lanzaError() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.findAll()).thenReturn(List.of(administrador));

        assertThatThrownBy(() -> administradorService.eliminar(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("último usuario Administrador");
        verify(usuarioRepository, never()).delete(any());
    }

    @Test
    @DisplayName("actualizar: no se puede quitar el rol al último Administrador")
    void actualizar_quitarRolUltimoAdmin_lanzaError() {
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(administrador));
        when(usuarioRepository.findAll()).thenReturn(List.of(administrador));

        UsuarioAdminRequest request = peticionEditar("kike@tourinvest.com");
        request.setRol(Rol.NombreRol.Analista);

        assertThatThrownBy(() -> administradorService.actualizar(1, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos un usuario con rol Administrador");
    }
} 