package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.LoginRequest;
import com.tourinvest.backend.dto.LoginResponse;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.RolRepository;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private Authentication authentication;

    private AuthService authService;

    private Rol rolInversionista;
    private Usuario usuarioValido;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, rolRepository, passwordEncoder, authenticationManager, jwtUtil);

        rolInversionista = new Rol(Rol.NombreRol.Inversionista);
        rolInversionista.setIdRol(3);

        usuarioValido = new Usuario();
        usuarioValido.setIdUsuario(1);
        usuarioValido.setRol(rolInversionista);
        usuarioValido.setNombre1("Juan");
        usuarioValido.setApellido1("Fuentes");
        usuarioValido.setCedula("1001234567");
        usuarioValido.setFechaNacimiento(LocalDate.of(2002, 11, 27));
        usuarioValido.setCorreo("kike@tourinvest.com");
        usuarioValido.setContrasena("$2a$10$hashSimulado");
    }

    // ---------- LOGIN ----------

    @Test
    @DisplayName("login: con credenciales válidas devuelve token, nombre y rol")
    void login_credencialesValidas_devuelveLoginResponse() {
        LoginRequest request = new LoginRequest();
        request.setCorreo("kike@tourinvest.com");
        request.setContrasena("123456");

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(usuarioRepository.findByCorreo("kike@tourinvest.com")).thenReturn(Optional.of(usuarioValido));
        when(jwtUtil.generarToken("kike@tourinvest.com", "Inversionista")).thenReturn("token-simulado-123");

        LoginResponse respuesta = authService.login(request);

        assertThat(respuesta.getToken()).isEqualTo("token-simulado-123");
        assertThat(respuesta.getNombre1()).isEqualTo("Juan");
        assertThat(respuesta.getCorreo()).isEqualTo("kike@tourinvest.com");
        assertThat(respuesta.getRol()).isEqualTo("Inversionista");

        verify(authenticationManager).authenticate(any());
        verify(jwtUtil).generarToken("kike@tourinvest.com", "Inversionista");
    }

    @Test
    @DisplayName("login: si el AuthenticationManager rechaza las credenciales, lanza BadCredentialsException")
    void login_credencialesInvalidas_lanzaBadCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setCorreo("kike@tourinvest.com");
        request.setContrasena("clave-incorrecta");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Correo o contraseña incorrectos");

        verify(usuarioRepository, never()).findByCorreo(any());
        verify(jwtUtil, never()).generarToken(any(), any());
    }

    @Test
    @DisplayName("login: si el usuario autenticado no existe en el repositorio, lanza BadCredentialsException")
    void login_usuarioNoEncontradoTrasAutenticar_lanzaBadCredentialsException() {
        LoginRequest request = new LoginRequest();
        request.setCorreo("fantasma@tourinvest.com");
        request.setContrasena("123456");

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(usuarioRepository.findByCorreo("fantasma@tourinvest.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtUtil, never()).generarToken(any(), any());
    }

    // ---------- REGISTRO ----------

    @Test
    @DisplayName("registrar: con datos válidos, hashea la contraseña y guarda como Inversionista")
    void registrar_datosValidos_guardaUsuarioConRolInversionista() {
        Usuario nuevo = new Usuario();
        nuevo.setCorreo("kike@tourinvest.com");
        nuevo.setCedula("9998887776");
        nuevo.setContrasena("claveEnTextoPlano");

        when(usuarioRepository.existsByCorreo("kike@tourinvest.com")).thenReturn(false);
        when(usuarioRepository.existsByCedula("9998887776")).thenReturn(false);
        when(rolRepository.findByNombre(Rol.NombreRol.Inversionista)).thenReturn(Optional.of(rolInversionista));
        when(passwordEncoder.encode("claveEnTextoPlano")).thenReturn("$2a$10$hashGenerado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            u.setIdUsuario(99);
            return u;
        });

        Usuario resultado = authService.registrar(nuevo, Rol.NombreRol.Inversionista);

        assertThat(resultado.getIdUsuario()).isEqualTo(99);
        assertThat(resultado.getRol()).isEqualTo(rolInversionista);
        assertThat(resultado.getPassword()).isEqualTo("$2a$10$hashGenerado"); // confirma que se guardó el hash, no el texto plano

        verify(passwordEncoder).encode("claveEnTextoPlano");
        verify(usuarioRepository).save(nuevo);
    }

    @Test
    @DisplayName("registrar: con correo ya existente, lanza IllegalArgumentException y no llega a guardar")
    void registrar_correoDuplicado_lanzaIllegalArgumentException() {
        Usuario nuevo = new Usuario();
        nuevo.setCorreo("kike@tourinvest.com");
        nuevo.setCedula("1112223334");

        when(usuarioRepository.existsByCorreo("kike@tourinvest.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(nuevo, Rol.NombreRol.Inversionista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe un usuario registrado con ese correo");

        verify(usuarioRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("registrar: con cédula ya existente, lanza IllegalArgumentException y no llega a guardar")
    void registrar_cedulaDuplicada_lanzaIllegalArgumentException() {
        Usuario nuevo = new Usuario();
        nuevo.setCorreo("otro@tourinvest.com");
        nuevo.setCedula("1001234567");

        when(usuarioRepository.existsByCorreo("otro@tourinvest.com")).thenReturn(false);
        when(usuarioRepository.existsByCedula("1001234567")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(nuevo, Rol.NombreRol.Inversionista))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe un usuario registrado con esa cédula");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrar: si el rol no existe en BD, lanza IllegalStateException")
    void registrar_rolInexistente_lanzaIllegalStateException() {
        Usuario nuevo = new Usuario();
        nuevo.setCorreo("nuevo2@tourinvest.com");
        nuevo.setCedula("5556667778");

        when(usuarioRepository.existsByCorreo("nuevo2@tourinvest.com")).thenReturn(false);
        when(usuarioRepository.existsByCedula("5556667778")).thenReturn(false);
        when(rolRepository.findByNombre(Rol.NombreRol.Inversionista)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.registrar(nuevo, Rol.NombreRol.Inversionista))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El rol solicitado no existe en la base de datos");

        verify(usuarioRepository, never()).save(any());
    }
}