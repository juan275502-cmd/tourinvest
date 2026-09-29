package com.tourinvest.backend.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utilidad de conexión JDBC pura, independiente de Spring Data JPA.
 * Demuestra conexión directa a MySQL
 * mediante java.sql, sin pasar por el ORM (Hibernate) usado en el resto
 * del backend.
 */
public class ConexionJDBC {

    // Convencion de hostname del proyecto: 127.0.0.1, nunca `localhost`
    // (en IPv6 `localhost` resuelve a ::1 y el driver buscaria el socket unix).
    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/tourinvest?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String CONTRASENA = "";

    public static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }
}