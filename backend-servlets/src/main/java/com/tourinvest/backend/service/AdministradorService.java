package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.UsuarioAdminRequest;
import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.RolRepository;
import com.tourinvest.backend.repository.UsuarioRepository;
import com.tourinvest.backend.validation.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AdministradorService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public AdministradorService(UsuarioRepository usuarioRepository,
                                RolRepository rolRepository,
                                PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UsuarioResumenDTO> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::mapearADTO)
                .toList();
    }

    public UsuarioResumenDTO suspender(Usuario administrador, Integer idUsuario) {
        if (administrador.getIdUsuario().equals(idUsuario)) {
            throw new IllegalStateException("No puedes suspender tu propia cuenta de administrador");
        }
        return cambiarEstado(idUsuario, Usuario.EstadoUsuario.Inactivo);
    }

    public UsuarioResumenDTO activar(Integer idUsuario) {
        return cambiarEstado(idUsuario, Usuario.EstadoUsuario.Activo);
    }

    // ---------- CRUD de usuarios (panel de Administrador) ----------

    /**
     * Crea un usuario con el rol que indique el Administrador.
     * A diferencia del registro publico (siempre Inversionista), aqui el
     * administrador decide el rol inicial.
     */
    @Transactional
    public UsuarioResumenDTO crear(UsuarioAdminRequest request) {
        String correo = request.getCorreo().trim().toLowerCase();
        String cedula = request.getCedula().trim();

        if (usuarioRepository.existsByCorreo(correo)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con ese correo");
        }
        if (usuarioRepository.existsByCedula(cedula)) {
            throw new IllegalArgumentException("Ya existe un usuario registrado con esa cédula");
        }
        if (request.getContrasena() == null || request.getContrasena().isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria al crear un usuario");
        }
        exigirContrasenaSegura(request.getContrasena());

        Rol rol = rolRepository.findByNombre(request.getRol())
                .orElseThrow(() -> new IllegalStateException("El rol seleccionado no existe"));

        Usuario nuevo = new Usuario();
        nuevo.setNombre1(request.getNombre1().trim());
        nuevo.setApellido1(request.getApellido1().trim());
        nuevo.setCedula(cedula);
        nuevo.setFechaNacimiento(request.getFechaNacimiento());
        nuevo.setCorreo(correo);
        nuevo.setContrasena(passwordEncoder.encode(request.getContrasena()));
        nuevo.setRol(rol);
        nuevo.setEstado(request.getEstado() != null ? request.getEstado() : Usuario.EstadoUsuario.Activo);

        return mapearADTO(usuarioRepository.save(nuevo));
    }

    /**
     * Edita un usuario. La contrasena SOLO se cambia si viene informed:
     * si se deja vacia, el usuario conserva la que tenia.
     */
    @Transactional
    public UsuarioResumenDTO actualizar(Integer idUsuario, UsuarioAdminRequest request) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        String correo = request.getCorreo().trim().toLowerCase();
        String cedula = request.getCedula().trim();

        // Unicidad excluyendo al propio usuario.
        if (usuarioRepository.existsByCorreoAndIdUsuarioNot(correo, idUsuario)) {
            throw new IllegalArgumentException("Ya existe otro usuario con ese correo");
        }
        if (usuarioRepository.existsByCedulaAndIdUsuarioNot(cedula, idUsuario)) {
            throw new IllegalArgumentException("Ya existe otro usuario con esa cédula");
        }

        // Evita dejar el sistema sin ningun administrador operativo.
        boolean dejaDeSerAdmin = usuario.getRol() != null
                && usuario.getRol().getNombre() == Rol.NombreRol.Administrador
                && request.getRol() != Rol.NombreRol.Administrador;
        if (dejaDeSerAdmin && contarAdministradores() <= 1) {
            throw new IllegalStateException("Debe existir al menos un usuario con rol Administrador");
        }

        usuario.setNombre1(request.getNombre1().trim());
        usuario.setApellido1(request.getApellido1().trim());
        usuario.setCedula(cedula);
        usuario.setFechaNacimiento(request.getFechaNacimiento());
        usuario.setCorreo(correo);
        usuario.setRol(rolRepository.findByNombre(request.getRol())
                .orElseThrow(() -> new IllegalStateException("El rol seleccionado no existe")));
        if (request.getEstado() != null) {
            usuario.setEstado(request.getEstado());
        }

        if (request.getContrasena() != null && !request.getContrasena().isBlank()) {
            exigirContrasenaSegura(request.getContrasena());
            usuario.setContrasena(passwordEncoder.encode(request.getContrasena()));
        }

        return mapearADTO(usuarioRepository.save(usuario));
    }

    /**
     * Aplica la misma politica de contrasena del registro publico
     * ({@link PasswordPolicy}) a las contrasenas que fija el administrador.
     * Se valida aqui, y no con {@code @ContrasenaSegura} en el DTO, porque al
     * EDITAR la contrasena es opcional: si viene vacia debe conservarse la
     * anterior sin exigir la politica.
     */
    private void exigirContrasenaSegura(String contrasena) {
        String mensaje = PasswordPolicy.mensajeError(contrasena);
        if (mensaje != null) {
            throw new IllegalArgumentException(mensaje);
        }
    }

    /**
     * Elimina un usuario. Se bloquea si tiene datos asociados (portafolios,
     * alertas, reportes) porque las FK son ON DELETE RESTRICT: borrarla
     * dejaria datos huerfanos o fallaria la integridad referencial.
     */
    @Transactional
    public void eliminar(Integer idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        if (usuario.getRol() != null
                && usuario.getRol().getNombre() == Rol.NombreRol.Administrador
                && contarAdministradores() <= 1) {
            throw new IllegalStateException("No puedes eliminar el último usuario Administrador");
        }

        if (tieneDatosAsociados(idUsuario)) {
            throw new IllegalStateException(
                    "El usuario tiene datos asociados (portafolios, alertas o reportes). "
                    + "Suspendelo en lugar de eliminarlo.");
        }

        usuarioRepository.delete(usuario);
    }

    /** El usuario tiene portafolios, alertas o reportes asociados. */
    private boolean tieneDatosAsociados(Integer idUsuario) {
        return usuarioRepository.existsByIdUsuario_IdUsuario(idUsuario);
    }

    private long contarAdministradores() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.getRol() != null && u.getRol().getNombre() == Rol.NombreRol.Administrador)
                .count();
    }

    private UsuarioResumenDTO cambiarEstado(Integer idUsuario, Usuario.EstadoUsuario nuevoEstado) {
        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        usuario.setEstado(nuevoEstado);
        Usuario actualizado = usuarioRepository.save(usuario);
        return mapearADTO(actualizado);
    }

    private UsuarioResumenDTO mapearADTO(Usuario usuario) {
        return new UsuarioResumenDTO(
                usuario.getIdUsuario(),
                usuario.getNombre1() + " " + usuario.getApellido1(),
                usuario.getCorreo(),
                usuario.getRol().getNombre().name(),
                usuario.getEstado().name(),
                usuario.getFechaRegistro());
    }
}