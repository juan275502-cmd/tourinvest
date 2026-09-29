package com.tourinvest.backend.controller;

import java.util.Map;

import com.tourinvest.backend.dto.ActualizarPerfilRequest;
import com.tourinvest.backend.dto.CambiarContrasenaRequest;
import com.tourinvest.backend.dto.PerfilDTO;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil del usuario AUTENTICADO.
 *
 * <p>Disponible para los TRES roles (Administrador, Analista, Inversionista):
 * no exige un rol concreto, solo estar autenticado. Cada usuario ve y modifica
 * unicamente SU propio perfil, porque el {@link Usuario} se obtiene del token
 * JWT validado por {@code JwtAuthFilter} y nunca de un parametro de la URL.
 * Asi es imposible consultar o editar el perfil de otra persona.
 */
@RestController
@RequestMapping("/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    /** GET /perfil -> datos del usuario autenticado. */
    @GetMapping
    public ResponseEntity<PerfilDTO> obtener(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(perfilService.obtenerPerfil(usuario));
    }

    /** PUT /perfil -> actualiza nombre, apellido, cédula, fecha de nacimiento y correo. */
    @PutMapping
    public ResponseEntity<PerfilDTO> actualizar(@AuthenticationPrincipal Usuario usuario,
                                                @Valid @RequestBody ActualizarPerfilRequest request) {
        return ResponseEntity.ok(perfilService.actualizarPerfil(usuario, request));
    }

    /** PATCH /perfil/contrasena -> cambia la contraseña exigiendo la actual. */
    @PatchMapping("/contrasena")
    public ResponseEntity<Map<String, String>> cambiarContrasena(
            @AuthenticationPrincipal Usuario usuario,
            @Valid @RequestBody CambiarContrasenaRequest request) {
        perfilService.cambiarContrasena(usuario, request);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Contraseña actualizada. Por seguridad, vuelve a iniciar sesión."));
    }
}