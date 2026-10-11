package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.ContrasenaSegura;
import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.MayorDeEdad;
import com.tourinvest.backend.validation.Patrones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RegistroRequest extends DatosPersonalesRequest {
    @NotBlank(message = "La contraseña es obligatoria")
    @ContrasenaSegura
    @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
    private String contrasena;

    @NotBlank(message = "Debes confirmar la contraseña")
    @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
    private String confirmarContrasena;

    public boolean lasContrasenasCoinciden() {
        return contrasena != null && contrasena.equals(confirmarContrasena);
    }

    // Getters y setters
    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getConfirmarContrasena() {
        return confirmarContrasena;
    }

    public void setConfirmarContrasena(String confirmarContrasena) {
        this.confirmarContrasena = confirmarContrasena;
    }
}