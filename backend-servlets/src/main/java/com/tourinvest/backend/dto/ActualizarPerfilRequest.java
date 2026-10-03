package com.tourinvest.backend.dto;

import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.MayorDeEdad;
import com.tourinvest.backend.validation.Patrones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Actualizacion del perfil propio por parte del usuario AUTENTICADO.
 *
 * <p>Importante (seguridad):
 * <ul>
 *   <li>NO acepta {@code idUsuario}, {@code correo} como identidad ni rol/estado:
 *       el usuario solo puede editar SU PROPIO perfil. El correo y el rol se
 *       conservan tal cual para que nadie se autoasigne un rol ni cambie su
 *       identidad de login.</li>
 *   <li>La contrasena se cambia por un endpoint aparte
 *       ({@code /perfil/contrasena}) que exige la contrasena actual.</li>
 * </ul>
 */
public class ActualizarPerfilRequest {

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

    /**
     * Reglas de la fecha de nacimiento (casos PV-16 y PV-17):
     * obligatorio, real en el calendario (lo garantiza el formato AAAA-MM-DD),
     * anterior a hoy y de una persona MAYOR DE EDAD.
     */
    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    @MayorDeEdad(minima = 18, message = "Debes ser mayor de edad para registrarte (mínimo 18 años)")
    private LocalDate fechaNacimiento;

    /**
     * Correo del usuario. ES su identidad de login: al cambiarlo, debera
     * iniciar sesion de nuevo con el nuevo correo. El token emitido antes del
     * cambio deja de ser valido (el backend lo resuelve por correo).
     */
    @NotBlank(message = "El correo es obligatorio")
    @CorreoValido
    private String correo;

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
}