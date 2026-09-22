package com.tourinvest.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tourinvest.backend.dto.UsuarioAdminRequest;
import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.service.UsuarioAdminService;

import jakarta.validation.Valid;

/**
 * GA7-220501096-AA3-EV01 — Controlador REST del módulo de Gestión de Usuarios.
 *
 * Capa Controller entre la vista (frontend/view/usuarios.html) y
 * UsuarioAdminService. Expone el CRUD completo en /api/usuarios.
 *
 * Acceso: SOLO rol Administrador (regla declarada en SecurityConfig), por lo
 * que toda petición llega ya autenticada con su token Bearer.
 *
 * Rutas:
 *   GET    /api/usuarios            → listar todos
 *   GET    /api/usuarios/{id}       → consultar uno (formulario de edición)
 *   POST   /api/usuarios            → registrar
 *   PUT    /api/usuarios/{id}       → editar (contraseña vacía = conservar)
 *   DELETE /api/usuarios/{id}       → eliminar
 *   PATCH  /api/usuarios/{id}/activar    → estado Activo
 *   PATCH  /api/usuarios/{id}/suspender  → estado Inactivo
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    public UsuarioAdminController(UsuarioAdminService usuarioAdminService) {
        this.usuarioAdminService = usuarioAdminService;
    }

    /** Lista todos los usuarios registrados (READ). */
    @GetMapping
    public List<UsuarioResumenDTO> listar() {
        return usuarioAdminService.listarUsuarios();
    }

    /** Consulta un usuario por id para poblar el formulario de edición (READ). */
    @GetMapping("/{idUsuario}")
    public ResponseEntity<UsuarioResumenDTO> consultar(@PathVariable Integer idUsuario) {
        return ResponseEntity.ok(usuarioAdminService.buscarUsuarioPorId(idUsuario));
    }

    /** Registra un usuario nuevo con el rol seleccionado (CREATE). */
    @PostMapping
    public ResponseEntity<?> registrar(@Valid @RequestBody UsuarioAdminRequest solicitud) {
        UsuarioResumenDTO creado = usuarioAdminService.registrarUsuario(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of("mensaje", "Usuario registrado correctamente.", "usuario", creado));
    }

    /** Edita datos, rol y estado de un usuario (UPDATE). */
    @PutMapping("/{idUsuario}")
    public ResponseEntity<?> actualizar(@PathVariable Integer idUsuario,
                                        @Valid @RequestBody UsuarioAdminRequest solicitud) {
        UsuarioResumenDTO actualizado = usuarioAdminService.actualizarUsuario(idUsuario, solicitud);
        return ResponseEntity.ok(
                Map.of("mensaje", "Usuario actualizado correctamente.", "usuario", actualizado));
    }

    /** Elimina un usuario (DELETE). Rechaza borrarse a sí mismo y datos asociados (409). */
    @DeleteMapping("/{idUsuario}")
    public ResponseEntity<?> eliminar(@AuthenticationPrincipal Usuario administradorActual,
                                      @PathVariable Integer idUsuario) {
        usuarioAdminService.eliminarUsuario(idUsuario, administradorActual);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado correctamente."));
    }

    /** Reactiva un usuario suspendido (estado → Activo). */
    @PatchMapping("/{idUsuario}/activar")
    public ResponseEntity<?> activar(@PathVariable Integer idUsuario) {
        return respuestaEstado(usuarioAdminService.activarUsuario(idUsuario), "Usuario activado.");
    }

    /** Suspende un usuario (estado → Inactivo). No permite auto-suspensión. */
    @PatchMapping("/{idUsuario}/suspender")
    public ResponseEntity<?> suspender(@AuthenticationPrincipal Usuario administradorActual,
                                       @PathVariable Integer idUsuario) {
        return respuestaEstado(
                usuarioAdminService.suspenderUsuario(idUsuario, administradorActual), "Usuario suspendido.");
    }

    /** Respuesta uniforme para los cambios de estado. */
    private ResponseEntity<Map<String, Object>> respuestaEstado(UsuarioResumenDTO usuario, String mensaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("mensaje", mensaje);
        cuerpo.put("usuario", usuario);
        return ResponseEntity.ok(cuerpo);
    }
}
