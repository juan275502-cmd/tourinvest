package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.ContrasenaSegura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de contrasena del usuario autenticado.
 * Exige la contrasena ACTUAL para confirmar que es el dueno de la cuenta.
 *
 * <p>La nueva contrasena debe cumplir la misma politica que el registro
 * publico (ver {@link ContrasenaSegura}).</p>
 */
public class CambiarContrasenaRequest {

    @NotBlank(message = "Debes escribir tu contraseña actual")
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @ContrasenaSegura
    @Size(max = 72, message = "La nueva contraseña no puede superar 72 caracteres")
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