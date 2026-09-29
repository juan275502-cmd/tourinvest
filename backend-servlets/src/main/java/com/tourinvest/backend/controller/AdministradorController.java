package com.tourinvest.backend.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

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
import com.tourinvest.backend.service.AdministradorService;

@RestController
@RequestMapping("/admin/usuarios")
public class AdministradorController {

    private final AdministradorService administradorService;

    public AdministradorController(AdministradorService administradorService) {
        this.administradorService = administradorService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResumenDTO>> listar() {
        return ResponseEntity.ok(administradorService.listarUsuarios());
    }

    @PatchMapping("/{idUsuario}/suspender")
    public ResponseEntity<UsuarioResumenDTO> suspender(@AuthenticationPrincipal Usuario administrador,
                                                          @PathVariable Integer idUsuario) {
        return ResponseEntity.ok(administradorService.suspender(administrador, idUsuario));
    }

    @PatchMapping("/{idUsuario}/activar")
    public ResponseEntity<UsuarioResumenDTO> activar(@PathVariable Integer idUsuario) {
        return ResponseEntity.ok(administradorService.activar(idUsuario));
    }

    // ---------- CRUD de usuarios ----------

    /** Crea un usuario. El Administrador elige el rol. */
    @PostMapping
    public ResponseEntity<UsuarioResumenDTO> crear(@Valid @RequestBody UsuarioAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(administradorService.crear(request));
    }

    /**
     * Edita un usuario. La contrasena solo se cambia si se envia informed;
     * si viene vacia, el usuario conserva la suya.
     */
    @PutMapping("/{idUsuario}")
    public ResponseEntity<UsuarioResumenDTO> actualizar(@PathVariable Integer idUsuario,
                                                         @Valid @RequestBody UsuarioAdminRequest request) {
        return ResponseEntity.ok(administradorService.actualizar(idUsuario, request));
    }

    /**
     * Elimina un usuario. Responde 400 si tiene datos asociados o si es el
     * ultimo Administrador, para no dejar el sistema inaccesible.
     */
    @DeleteMapping("/{idUsuario}")
    public ResponseEntity<Map<String, String>> eliminar(@PathVariable Integer idUsuario) {
        administradorService.eliminar(idUsuario);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado correctamente."));
    }
} 