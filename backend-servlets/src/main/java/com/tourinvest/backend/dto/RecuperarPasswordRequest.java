package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.CorreoValido;
import jakarta.validation.constraints.NotBlank;

/** Recuperacion de contrasena: solo se valida el formato del correo. */
public class RecuperarPasswordRequest {

    @NotBlank(message = "El correo es obligatorio")
    @CorreoValido
    private String correo;

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }
} 