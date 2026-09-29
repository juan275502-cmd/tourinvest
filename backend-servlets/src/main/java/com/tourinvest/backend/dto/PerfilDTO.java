package com.tourinvest.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Datos del perfil del usuario autenticado (RF de perfil).
 * Nunca incluye la contrasena hasheada.
 */
public class PerfilDTO {

    private final Integer idUsuario;
    private final String nombre1;
    private final String apellido1;
    private final String cedula;
    private final LocalDate fechaNacimiento;
    private final String correo;
    private final String rol;
    private final String estado;
    private final LocalDateTime fechaRegistro;

    public PerfilDTO(Integer idUsuario, String nombre1, String apellido1, String cedula,
                     LocalDate fechaNacimiento, String correo, String rol, String estado,
                     LocalDateTime fechaRegistro) {
        this.idUsuario = idUsuario;
        this.nombre1 = nombre1;
        this.apellido1 = apellido1;
        this.cedula = cedula;
        this.fechaNacimiento = fechaNacimiento;
        this.correo = correo;
        this.rol = rol;
        this.estado = estado;
        this.fechaRegistro = fechaRegistro;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getNombre1() {
        return nombre1;
    }

    public String getApellido1() {
        return apellido1;
    }

    public String getCedula() {
        return cedula;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getCorreo() {
        return correo;
    }

    public String getRol() {
        return rol;
    }

    public String getEstado() {
        return estado;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public String getNombreCompleto() {
        return nombre1 + " " + apellido1;
    }
}