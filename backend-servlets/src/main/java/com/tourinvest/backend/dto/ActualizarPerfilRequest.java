package com.tourinvest.backend.dto;

import jakarta.validation.constraints.Email;
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
    private String nombre1;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido no puede superar 100 caracteres")
    private String apellido1;

    @NotBlank(message = "La cédula es obligatoria")
    @Size(max = 20, message = "La cédula no puede superar 20 caracteres")
    private String cedula;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe ser anterior a hoy")
    private LocalDate fechaNacimiento;

    /**
     * Correo del usuario. ES su identidad de login: al cambiarlo, debera
     * iniciar sesion de nuevo con el nuevo correo. El token emitido antes del
     * cambio deja de ser valido (el backend lo resuelve por correo).
     */
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
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