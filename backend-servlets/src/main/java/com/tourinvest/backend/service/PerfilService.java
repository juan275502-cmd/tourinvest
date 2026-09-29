package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.ActualizarPerfilRequest;
import com.tourinvest.backend.dto.CambiarContrasenaRequest;
import com.tourinvest.backend.dto.PerfilDTO;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de perfil (RF de perfil).
 *
 * <p>REGLA CENTRAL: cada usuario edita UNICAMENTE su propio perfil. El
 * usuario llega del token JWT ({@code @AuthenticationPrincipal}), nunca de un
 * parametro de la peticion, de modo que es imposible editar el perfil de otro
 * usuario desde estos endpoints. El rol y el estado NO son editables aqui:
 * eso es competencia del panel de Administrador.
 */
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public PerfilService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PerfilDTO obtenerPerfil(Usuario usuario) {
        return mapear(usuario);
    }

    /**
     * Actualiza los datos del usuario autenticado.
     * NO toca: id, rol, estado, contrasena ni fecha de registro.
     */
    @Transactional
    public PerfilDTO actualizarPerfil(Usuario usuario, ActualizarPerfilRequest request) {
        Integer idUsuario = usuario.getIdUsuario();

        // Unicidad validada EXCLUYENDO al propio usuario: si el correo no cambia,
        // "existsBy..." debe dar false, no true por el registro del propio usuario.
        if (usuarioRepository.existsByCorreoAndIdUsuarioNot(request.getCorreo(), idUsuario)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con ese correo");
        }
        if (usuarioRepository.existsByCedulaAndIdUsuarioNot(request.getCedula(), idUsuario)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con esa cédula");
        }

        // Se conservan rol, estado, contrasena y fecha de registro: aqui solo
        // cambian los datos personales declarados en la peticion.
        usuario.setNombre1(request.getNombre1().trim());
        usuario.setApellido1(request.getApellido1().trim());
        usuario.setCedula(request.getCedula().trim());
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        // El correo tambien es la identidad de login. Se normaliza a minusculas
        // para que el login no falle por mayusculas/minusculas.
        usuario.setCorreo(request.getCorreo().trim().toLowerCase());

        return mapear(usuarioRepository.save(usuario));
    }

    /**
     * Cambia la contrasena del usuario autenticado exigiendo la actual.
     * Tras cambiarla se recomienda volver a iniciar sesion.
     */
    @Transactional
    public void cambiarContrasena(Usuario usuario, CambiarContrasenaRequest request) {
        if (!passwordEncoder.matches(request.getContrasenaActual(), usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }
        if (request.getContrasenaActual().equals(request.getContrasenaNueva())) {
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta de la actual");
        }
        usuario.setContrasena(passwordEncoder.encode(request.getContrasenaNueva()));
        usuarioRepository.save(usuario);
    }

    private PerfilDTO mapear(Usuario usuario) {
        return new PerfilDTO(
                usuario.getIdUsuario(),
                usuario.getNombre1(),
                usuario.getApellido1(),
                usuario.getCedula(),
                usuario.getFechaNacimiento(),
                usuario.getCorreo(),
                usuario.getRol() != null ? usuario.getRol().getNombre().name() : null,
                usuario.getEstado() != null ? usuario.getEstado().name() : null,
                usuario.getFechaRegistro());
    }
}