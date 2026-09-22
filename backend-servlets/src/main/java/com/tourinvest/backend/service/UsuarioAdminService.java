package com.tourinvest.backend.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tourinvest.backend.dto.UsuarioAdminRequest;
import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.RolRepository;
import com.tourinvest.backend.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * GA7-220501096-AA3-EV01 — Lógica de negocio del módulo de Gestión de Usuarios.
 *
 * Capa Service entre UsuarioAdminController y UsuarioRepository (Spring Data
 * JPA). Concentra las reglas que no deben vivir ni en el controlador ni en
 * la vista:
 *
 *  · Unicidad de correo al crear y al editar (excluyendo al propio usuario).
 *  · Rol válido: debe existir en la tabla roles (no se acepta un número suelto).
 *  · Estado válido: solo Activo/Inactivo (ENUM de la tabla usuarios).
 *  · Contraseñas SIEMPRE con BCrypt (mismo criterio que AuthService), de modo
 *    que los usuarios creados aquí pueden iniciar sesión por /auth/login.
 *  · Protecciones: un administrador no puede eliminar ni suspender su propia
 *    cuenta (evita perder el único acceso administrativo).
 *  · Eliminación controlada: si MySQL rechaza el borrado por llaves foráneas
 *    (alertas, reportes o portafolios del usuario), se informa con 409 en
 *    lugar de una excepción genérica 500.
 */
@Service
public class UsuarioAdminService {

    // Estados válidos del ENUM('Activo','Inactivo') definido en query.sql.
    private static final String ESTADO_ACTIVO = "Activo";
    private static final String ESTADO_INACTIVO = "Inactivo";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioAdminService(UsuarioRepository usuarioRepository,
                               RolRepository rolRepository,
                               PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- READ ----------

    /** Consulta todos los usuarios registrados para mostrarlos en la vista administrativa. */
    public List<UsuarioResumenDTO> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioAdminService::mapearADTO)
                .toList();
    }

    /** Consulta un usuario por su id; se usa para poblar el formulario de edición. */
    public UsuarioResumenDTO buscarUsuarioPorId(Integer idUsuario) {
        return mapearADTO(obtenerUsuario(idUsuario));
    }

    // ---------- CREATE ----------

    /** Registra un usuario con el rol indicado (cualquiera de los tres, a diferencia de /auth/registro). */
    @Transactional
    public UsuarioResumenDTO registrarUsuario(UsuarioAdminRequest solicitud) {
        // En creación la contraseña es obligatoria y de mínimo 6 caracteres:
        // se valida aquí porque el vacío también debe poder llegar (edición),
        // y @Size(min=6) del DTO lo rechazaría.
        if (solicitud.getContrasena() == null || solicitud.getContrasena().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña es obligatoria y debe tener al menos 6 caracteres");
        }
        // Evita duplicados por correo: la columna es UNIQUE en la BD, pero se
        // valida antes para dar un mensaje claro en lugar de una excepción SQL.
        if (usuarioRepository.existsByCorreo(solicitud.getCorreo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario registrado con ese correo");
        }
        if (usuarioRepository.existsByCedula(solicitud.getCedula())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario registrado con esa cédula");
        }

        Rol rol = obtenerRolValido(solicitud.getIdRol());

        Usuario usuario = new Usuario();
        aplicarDatosBasicos(usuario, solicitud);
        usuario.setRol(rol);
        usuario.setContrasena(passwordEncoder.encode(solicitud.getContrasena()));
        usuario.setEstado(Usuario.EstadoUsuario.Activo); // todo usuario nuevo entra Activo

        return mapearADTO(usuarioRepository.save(usuario));
    }

    // ---------- UPDATE ----------

    /**
     * Edita un usuario existente. Si la contraseña llega vacía se conserva la
     * actual (así el administrador puede cambiar datos sin reasignar clave).
     * La unicidad de correo se comprueba excluyendo al propio usuario.
     */
    @Transactional
    public UsuarioResumenDTO actualizarUsuario(Integer idUsuario, UsuarioAdminRequest solicitud) {
        Usuario usuario = obtenerUsuario(idUsuario);

        usuarioRepository.findByCorreo(solicitud.getCorreo())
                .filter(otro -> !otro.getIdUsuario().equals(idUsuario))
                .ifPresent(otro -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Ese correo ya pertenece a otro usuario");
                });

        Rol rol = obtenerRolValido(solicitud.getIdRol());

        aplicarDatosBasicos(usuario, solicitud);
        usuario.setRol(rol);
        if (solicitud.traeContrasenaNueva()) {
            // Solo se exige el mínimo cuando el formulario trae una clave nueva.
            if (solicitud.getContrasena().length() < 6) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La nueva contraseña debe tener al menos 6 caracteres");
            }
            usuario.setContrasena(passwordEncoder.encode(solicitud.getContrasena()));
        }
        usuario.setEstado(leerEstadoValido(solicitud.getEstado(), usuario.getEstado()));

        return mapearADTO(usuarioRepository.save(usuario));
    }

    // ---------- DELETE ----------

    /**
     * Elimina un usuario. Nunca borra la propia cuenta del administrador que
     * ejecuta la acción. Si MySQL rechaza el borrado por llaves foráneas
     * (portafolios/alertas/reportes con ON DELETE RESTRICT/CASCADE que
     * involucran al usuario), se traduce a un 409 con mensaje claro.
     */
    @Transactional
    public void eliminarUsuario(Integer idUsuario, Usuario administradorActual) {
        if (administradorActual.getIdUsuario().equals(idUsuario)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puedes eliminar tu propia cuenta de administrador");
        }
        try {
            if (!usuarioRepository.existsById(idUsuario)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
            }
            usuarioRepository.deleteById(idUsuario);
        } catch (DataIntegrityViolationException excepcion) {
            // DELETE RESTRICT en alertas/reportes/portafolios: no se puede borrar con datos asociados.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar: el usuario tiene registros asociados "
                    + "(portafolios, alertas o reportes). Usa suspender en su lugar.");
        }
    }

    // ---------- Estado (suspender / activar) ----------

    /** Cambia el estado de un usuario entre Activo e Inactivo (suspender/activar). */
    @Transactional
    public UsuarioResumenDTO cambiarEstado(Integer idUsuario, boolean activar) {
        Usuario usuario = obtenerUsuario(idUsuario);
        usuario.setEstado(activar ? Usuario.EstadoUsuario.Activo : Usuario.EstadoUsuario.Inactivo);
        return mapearADTO(usuarioRepository.save(usuario));
    }

    /**
     * Suspende (Inactivo) validando que el administrador no se suspenda a sí
     * mismo — misma regla que ya aplicaba AdministradorService.
     */
    @Transactional
    public UsuarioResumenDTO suspenderUsuario(Integer idUsuario, Usuario administradorActual) {
        if (administradorActual.getIdUsuario().equals(idUsuario)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puedes suspender tu propia cuenta de administrador");
        }
        return cambiarEstado(idUsuario, false);
    }

    /** Reactiva un usuario suspendido. */
    @Transactional
    public UsuarioResumenDTO activarUsuario(Integer idUsuario) {
        return cambiarEstado(idUsuario, true);
    }

    // ---------- Helpers privados ----------

    /** Obtiene un usuario o lanza 404 con mensaje claro. */
    private Usuario obtenerUsuario(Integer idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    /** Valida que el rol exista realmente en la tabla roles (evita ids sueltos). */
    private Rol obtenerRolValido(Integer idRol) {
        if (idRol == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El rol es obligatorio");
        }
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El rol indicado no existe (1=Administrador, 2=Analista, 3=Inversionista)"));
    }

    /** Acepta solo Activo/Inactivo; si no llega estado, conserva el actual. */
    private Usuario.EstadoUsuario leerEstadoValido(String estado, Usuario.EstadoUsuario actual) {
        if (estado == null || estado.isBlank()) {
            return actual;
        }
        if (ESTADO_ACTIVO.equalsIgnoreCase(estado)) {
            return Usuario.EstadoUsuario.Activo;
        }
        if (ESTADO_INACTIVO.equalsIgnoreCase(estado)) {
            return Usuario.EstadoUsuario.Inactivo;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Estado inválido: solo se acepta Activo o Inactivo");
    }

    /** Copia los campos personales del DTO hacia la entidad (sin rol ni contraseña). */
    private void aplicarDatosBasicos(Usuario usuario, UsuarioAdminRequest solicitud) {
        usuario.setNombre1(solicitud.getNombre1());
        usuario.setApellido1(solicitud.getApellido1());
        usuario.setCedula(solicitud.getCedula());
        usuario.setFechaNacimiento(solicitud.getFechaNacimiento());
        usuario.setCorreo(solicitud.getCorreo());
    }

    /** Convierte la entidad a DTO: la respuesta JAMÁS incluye el hash de la contraseña. */
    private static UsuarioResumenDTO mapearADTO(Usuario usuario) {
        return new UsuarioResumenDTO(
                usuario.getIdUsuario(),
                usuario.getNombre1() + " " + usuario.getApellido1(),
                usuario.getCorreo(),
                usuario.getRol() != null ? usuario.getRol().getNombre().name() : null,
                usuario.getEstado() != null ? usuario.getEstado().name() : null,
                usuario.getFechaRegistro());
    }
}
