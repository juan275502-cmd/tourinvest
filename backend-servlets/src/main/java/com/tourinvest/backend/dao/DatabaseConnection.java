package com.tourinvest.backend.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Paso 6 de la guía — Conexión JDBC pura para el módulo DAO.
 *
 * Apunta a la MISMA base de datos 'tourinvest' que usa el backend Spring Boot
 * (ver src/main/resources/application.properties), de modo que los usuarios
 * creados aquí sean los mismos que valida /auth/login y que lista
 * /admin/usuarios.
 *
 * Si cambian las credenciales de MySQL, actualízalas también en
 * application.properties.
 */
public class DatabaseConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/tourinvest?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String USUARIO = "root";

    /**
     * Configuración segura (GA7-220501096-AA3-EV01): se prioriza la variable
     * de entorno DB_PASSWORD; el fallback es la clave local de este equipo.
     */
    private static final String CONTRASENA =
            System.getenv().getOrDefault("DB_PASSWORD", "Ju4nd1eg0fuentes*");

    private DatabaseConnection() {
        // Uso exclusivo vía getConnection(): no se instancia.
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CONTRASENA);
    }
}
