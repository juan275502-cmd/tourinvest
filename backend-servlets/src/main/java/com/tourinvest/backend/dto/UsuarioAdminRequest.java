package com.tourinvest.backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * GA7-220501096-AA3-EV01 — DTO de entrada del módulo de Gestión de Usuarios.
 *
 * Cuerpo de las peticiones POST (crear) y PUT (editar) de /api/usuarios.
 * Centraliza las validaciones básicas exigidas por la evidencia usando
 * Bean Validation (jakarta.validation), el mismo mecanismo que ya usa
 * RegistroRequest en /auth/registro:
 *
 *  · Nombre, apellido, cédula, fecha y correo obligatorios.
 *  · Formato de correo válido (@Email).
 *  · Fecha de nacimiento en el pasado (@Past).
 *  · Contraseña obligatoria y de mínimo 6 caracteres al CREAR; al editar
 *    puede llegar vacía para conservar la actual (lo valida el Service).
 *  · idRol obligatorio (1=Administrador, 2=Analista, 3=Inversionista);
 *    que exista en la tabla roles lo verifica el Service contra la BD.
 */
public class UsuarioAdminRequest {

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

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres")
    private String correo;

    /**
     * En creación: obligatoria (mínimo 6 caracteres).
     * En edición: si llega null o vacía, el Service conserva la contraseña actual.
     * El mínimo se valida en UsuarioAdminService (solo cuando trae una nueva),
     * porque @Size(min=6) rechazaría también el string vacío "conservar".
     */
    @Size(max = 100, message = "La contraseña no puede superar 100 caracteres")
    private String contrasena;

    // Rol del usuario: 1=Administrador, 2=Analista, 3=Inversionista (tabla roles).
    @NotNull(message = "El rol es obligatorio")
    private Integer idRol;

    // Estado del usuario: Activo / Inactivo. Solo se usa en edición.
    private String estado;

    public boolean traeContrasenaNueva() {
        return contrasena != null && !contrasena.isBlank();
    }

    // --- Getters y setters ---

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

    public Integer getIdRol() {
        return idRol;
    }

    public void setIdRol(Integer idRol) {
        this.idRol = idRol;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
