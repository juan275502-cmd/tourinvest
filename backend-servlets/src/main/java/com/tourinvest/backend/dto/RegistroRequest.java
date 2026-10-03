package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.ContrasenaSegura;
import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.Patrones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de usuario desde el registro publico ({@code POST /auth/registro}).
 *
 * <p>Cada campo declara su propia regla; los mensajes van en español porque se
 * muestran tal cual en la interfaz (ver los casos PV-01 a PV-17 del protocolo de
 * pruebas).</p>
 */
public class RegistroRequest {

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
    private LocalDate fechaNacimiento;

    @NotBlank(message = "El correo es obligatorio")
    @CorreoValido
    private String correo;

    /**
     * Politica de seguridad: minimo 12 caracteres con al menos 2 mayusculas,
     * 2 minusculas, 2 numeros y 2 caracteres especiales. El maximo de 72 es el
     * limite de BCrypt (a partir de ahi se trunca en silencio).
     */
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

    public String getNombre1() {
        return nombre1;
    }

    public void setNombre1(String nombre1) {
        this.nombre1 = nombre1;
    }

    public String getApellido1() {
        return apellido1;
    }

    public void setApellido1(String apellido1) {
        this.apellido1 = apellido1;
    }

    public String getCedula() {
        return cedula;
    }

    public void setCedula(String cedula) {
        this.cedula = cedula;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

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

    public String getConfirmarContrasena() {
        return confirmarContrasena;
    }

    public void setConfirmarContrasena(String confirmarContrasena) {
        this.confirmarContrasena = confirmarContrasena;
    }
}