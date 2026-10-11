package com.tourinvest.backend.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utilidad de conexión JDBC pura, independiente de Spring Data JPA. Demuestra
 * conexión directa a MySQL mediante java.sql, sin pasar por el ORM (Hibernate)
 * usado en el resto del backend.
 */
public class ConexionJDBC {

    // Convencion de hostname del proyecto: 127.0.0.1, nunca `localhost`
    // (en IPv6 `localhost` resuelve a ::1 y el driver buscaria el socket unix).
    private static String env(String clave, String porDefecto) {
        String valor = System.getenv(clave);
        return (valor == null || valor.isBlank()) ? porDefecto : valor;
    }

    public static Connection obtenerConexion() throws SQLException {
        String url = "jdbc:mysql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "3306")
                + "/" + env("DB_NAME", "tourinvest")
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, env("DB_USER", "root"), env("DB_PASSWORD", ""));
    }
}
