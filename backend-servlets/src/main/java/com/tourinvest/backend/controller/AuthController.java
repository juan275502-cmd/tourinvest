package com.tourinvest.backend.controller;

import com.tourinvest.backend.dto.LoginRequest;
import com.tourinvest.backend.dto.LoginResponse;
import com.tourinvest.backend.dto.RecuperarPasswordRequest;
import com.tourinvest.backend.dto.RegistroRequest;
import com.tourinvest.backend.dto.UsuarioResumenDTO;
import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroRequest request) {
        if (!request.lasContrasenasCoinciden()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "mensaje", "Las contraseñas no coinciden."));
        }

        Usuario nuevoUsuario = new Usuario();
        // La normalización (trim, correo en minúsculas, caso PV-09) vive en DatosPersonalesRequest.
        nuevoUsuario.setNombre1(request.getNombre1());
        nuevoUsuario.setApellido1(request.getApellido1());
        nuevoUsuario.setCedula(request.getCedula());
        nuevoUsuario.setFechaNacimiento(request.getFechaNacimiento());
        nuevoUsuario.setCorreo(request.getCorreo());
        nuevoUsuario.setContrasena(request.getContrasena()); // se hashea dentro de AuthService.registrar

        // Registro público = siempre Inversionista. Admin/Analista se crean desde el panel de administrador.
        Usuario creado = authService.registrar(nuevoUsuario, Rol.NombreRol.Inversionista);

        UsuarioResumenDTO dto = new UsuarioResumenDTO(
                creado.getIdUsuario(),
                creado.getNombre1() + " " + creado.getApellido1(),
                creado.getCorreo(),
                creado.getRol() != null ? creado.getRol().getNombre().name() : null,
                creado.getEstado() != null ? creado.getEstado().name() : null,
                creado.getFechaRegistro());

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/recuperar")
    public ResponseEntity<Map<String, String>> recuperar(@Valid @RequestBody RecuperarPasswordRequest request) {
        authService.solicitarRecuperacion(request.getCorreo());
        return ResponseEntity.ok(Map.of(
                "mensaje", "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña."));
    }
}