package com.tourinvest.backend.dto;

import java.time.LocalDate;

import com.tourinvest.backend.model.Rol;
import com.tourinvest.backend.model.Usuario;

import com.tourinvest.backend.validation.CorreoValido;
import com.tourinvest.backend.validation.FormatoValido;
import com.tourinvest.backend.validation.Patrones;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * Alta/edicion de usuarios desde el panel de Administrador (CRUD de usuarios).
 *
 * <p>A diferencia de {@code ActualizarPerfilRequest}, aqui SI se puede elegir
 * el rol, porque el Administrador es quien asigna permisos. En edicion la
 * contrasena es opcional: si no viene, se conserva la existente.</p>
 */
public class UsuarioAdminRequest {

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
     * Contrasena. Obligatoria al CREAR; opcional al EDITAR (si se deja vacia se
     * mantiene la actual). Al no ser obligatoria aqui, NO puede llevar
     * {@code @ContrasenaSegura}: esa politica se aplica en
     * {@code AdministradorService} solo cuando la contrasena viene informada.
     */
    @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
    private String contrasena;

    @NotNull(message = "Debes seleccionar el rol del usuario")
    private Rol.NombreRol rol;

    /** Estado inicial. Por defecto Activo. */
    private Usuario.EstadoUsuario estado;

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

    public Rol.NombreRol getRol() {
        return rol;
    }

    public void setRol(Rol.NombreRol rol) {
        this.rol = rol;
    }

    public Usuario.EstadoUsuario getEstado() {
        return estado;
    }

    public void setEstado(Usuario.EstadoUsuario estado) {
        this.estado = estado;
    }
}