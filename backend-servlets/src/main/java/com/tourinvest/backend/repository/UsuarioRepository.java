package com.tourinvest.backend.repository;

import com.tourinvest.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    boolean existsByCedula(String cedula);

    /** Variantes para "actualizar mi perfil": ignoran al propio usuario. */
    boolean existsByCorreoAndIdUsuarioNot(String correo, Integer idUsuario);
    boolean existsByCedulaAndIdUsuarioNot(String cedula, Integer idUsuario);

    /**
     * True si el usuario tiene datos que lo referencian (portafolios, alertas,
     * reportes, notificaciones). El panel de Administrador usa esto para
     * bloquear el borrado y sugerir la suspension en su lugar.
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM Usuario u "
            + "WHERE u.idUsuario = :idUsuario AND ("
            + "EXISTS (SELECT p FROM Portafolio p WHERE p.usuario = u)"
            + " OR EXISTS (SELECT a FROM Alerta a WHERE a.usuario = u)"
            + " OR EXISTS (SELECT r FROM Reporte r WHERE r.usuario = u)"
            + " OR EXISTS (SELECT n FROM Notificacion n WHERE n.usuario = u)"
            + ")")
    boolean existsByIdUsuario_IdUsuario(@Param("idUsuario") Integer idUsuario);
}