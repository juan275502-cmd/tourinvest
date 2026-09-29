package com.tourinvest.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de contrasena del usuario autenticado.
 * Exige la contrasena ACTUAL para confirmar que es el dueno de la cuenta.
 */
public class CambiarContrasenaRequest {

    @NotBlank(message = "Debes escribir tu contraseña actual")
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 6, max = 72, message = "La nueva contraseña debe tener entre 6 y 72 caracteres")
    private String contrasenaNueva;

    public String getContrasenaActual() {
        return contrasenaActual;
    }

    public void setContrasenaActual(String contrasenaActual) {
        this.contrasenaActual = contrasenaActual;
    }

    public String getContrasenaNueva() {
        return contrasenaNueva;
    }

    public void setContrasenaNueva(String contrasenaNueva) {
        this.contrasenaNueva = contrasenaNueva;
    }
}