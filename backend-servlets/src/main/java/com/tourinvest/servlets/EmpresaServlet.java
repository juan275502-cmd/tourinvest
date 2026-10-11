package com.tourinvest.servlets;

import com.tourinvest.backend.jdbc.ConexionJDBC;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * RF: Gestión de Empresas (versión Servlet + JSP para GA7-AA2-EV02).
 * GET  /empresas  -> lista empresas + muestra formulario de creación (empresas.jsp)
 * POST /empresas  -> crea una empresa nueva a partir de los datos del formulario
 */
@WebServlet(name = "EmpresaServlet", urlPatterns = {"/empresas"})
public class EmpresaServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        List<String[]> empresas = new ArrayList<>();
        String sql = "SELECT id_empresa, nombre, sector, pais, simbolo FROM empresas ORDER BY nombre";

        try (Connection conexion = ConexionJDBC.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql);
             ResultSet resultado = statement.executeQuery()) {

            while (resultado.next()) {
                empresas.add(new String[]{
                        resultado.getString("id_empresa"),
                        resultado.getString("nombre"),
                        resultado.getString("sector"),
                        resultado.getString("pais"),
                        resultado.getString("simbolo")
                });
            }
        } catch (SQLException e) {
            request.setAttribute("mensajeError", "No fue posible conectar con la base de datos: " + e.getMessage());
        }

        request.setAttribute("listaEmpresas", empresas);
        request.getRequestDispatcher("/empresas.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Lectura de parámetros del formulario HTML (name="nombre", etc.)
        String nombre = request.getParameter("nombre");
        String sector = request.getParameter("sector");
        String pais = request.getParameter("pais");
        String simbolo = request.getParameter("simbolo");

        String sql = "INSERT INTO empresas (nombre, sector, pais, simbolo) VALUES (?, ?, ?, ?)";

        if (nombre == null || nombre.isBlank() || simbolo == null || simbolo.isBlank()) {
            request.setAttribute("mensajeError", "Nombre y símbolo son obligatorios.");
            doGet(request, response);
            return;
        }

        try (Connection conexion = ConexionJDBC.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {

            statement.setString(1, nombre.trim());
            statement.setString(2, sector.trim());
            statement.setString(3, pais.trim());
            statement.setString(4, simbolo.trim().toUpperCase());
            statement.executeUpdate();

            request.setAttribute("mensajeExito", "Empresa \"" + nombre + "\" creada correctamente.");
        } catch (SQLException e) {
            request.setAttribute("mensajeError", "Error al guardar la empresa: " + e.getMessage());
        }

        // Redirige (Post/Redirect/Get) para evitar reenvío de formulario al refrescar
        doGet(request, response);
    }
}