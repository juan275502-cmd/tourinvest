package com.tourinvest.backend.dto;

import java.time.LocalDate;

import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.MayorDeEdad;
import com.tourinvest.backend.validation.Patrones;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Datos personales compartidos por registro público, perfil propio y alta de usuarios del admin. */
public abstract class DatosPersonalesRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 30, message = "El nombre no puede superar 30 caracteres")
    @FormatoValido(regexp = Patrones.NOMBRE, message = Patrones.MSG_NOMBRE)
    private String nombre1;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    @FormatoValido(regexp = Patrones.NOMBRE, message = Patrones.MSG_APELLIDO)
    private String apellido1;

    @NotBlank(message = "La cédula es obligatoria")
    @FormatoValido(regexp = Patrones.CEDULA, message = Patrones.MSG_CEDULA)
    private String cedula;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    @MayorDeEdad(minima = 18, message = "Debes ser mayor de edad para registrarte (mínimo 18 años)")
    private LocalDate fechaNacimiento;

    @NotBlank(message = "El correo es obligatorio")
    @CorreoValido
    private String correo;

    public String getNombre1() { return nombre1; }
    public void setNombre1(String nombre1) { this.nombre1 = limpiar(nombre1); }

    public String getApellido1() { return apellido1; }
    public void setApellido1(String apellido1) { this.apellido1 = limpiar(apellido1); }

    public String getCedula() { return cedula; }
    public void setCedula(String cedula) { this.cedula = limpiar(cedula); }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }

    public String getCorreo() { return correo; }
    /** El correo es la identidad de login: se guarda siempre en minúsculas. */
    public void setCorreo(String correo) {
        String limpio = limpiar(correo);
        this.correo = limpio == null ? null : limpio.toLowerCase();
    }

    /** Recorta los extremos; conserva null para que lo reporte @NotBlank. */
    private static String limpiar(String valor) {
        return valor == null ? null : valor.trim();
    }
}