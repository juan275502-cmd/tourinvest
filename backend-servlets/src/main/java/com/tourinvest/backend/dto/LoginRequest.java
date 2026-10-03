package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.CorreoValido;
import jakarta.validation.constraints.NotBlank;

/**
 * Inicio de sesion. El correo usa la validacion estricta ({@link CorreoValido})
 * para que "vale.lor@tourinvest" se rechace por formato (PV-07) en vez de
 * reportarse como "correo o contraseña incorrectos".
 *
 * <p>La contrasena NO lleva politica de complejidad: en el login solo se
 * verifica contra el hash guardado, y los usuarios creados antes de esta regla
 * (clave {@code 123456}) deben poder seguir entrando.</p>
 */
public class LoginRequest {

    @NotBlank(message = "El correo es obligatorio")
    @CorreoValido
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }
}