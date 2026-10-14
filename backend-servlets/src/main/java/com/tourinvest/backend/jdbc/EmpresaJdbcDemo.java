package com.tourinvest.backend.jdbc;

import java.sql.SQLException;
import java.util.List;

/**
 * Ejecutar de forma independiente al contexto Spring Boot.
 */
public class EmpresaJdbcDemo {

    public static void main(String[] args) {
        EmpresaJdbcDAO dao = new EmpresaJdbcDAO();

        try {
            System.out.println("=== CREATE: Insertando nueva empresa ===");
            int idNueva = dao.insertarEmpresa("Hotel Demo Andino", "Hotelería", "Colombia", "DEMO");
            System.out.println("Empresa insertada con id: " + idNueva);

            System.out.println("\n=== READ: Listado de empresas ===");
            List<String> empresas = dao.listarEmpresas();
            empresas.forEach(System.out::println);

            System.out.println("\n=== READ (individual): Consultando la empresa recién creada ===");
            String empresaEncontrada = dao.buscarEmpresaPorId(idNueva);
            System.out.println(empresaEncontrada != null ? empresaEncontrada : "No encontrada");

            System.out.println("\n=== UPDATE: Actualizando empresa recién creada ===");
            boolean actualizado = dao.actualizarEmpresa(idNueva, "Hotel Demo Andino S.A.S.", "Hotelería", "Colombia");
            System.out.println("¿Actualización exitosa? " + actualizado);

            System.out.println("\n=== DELETE: Eliminando empresa de prueba ===");
            boolean eliminado = dao.eliminarEmpresa(idNueva);
            System.out.println("¿Eliminación exitosa? " + eliminado);

        } catch (SQLException e) {
            System.err.println("Error de conexión JDBC: " + e.getMessage());
        }
    }
}
