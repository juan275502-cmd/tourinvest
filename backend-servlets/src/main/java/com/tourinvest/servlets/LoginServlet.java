package com.tourinvest.servlets;

import com.tourinvest.backend.jdbc.ConexionJDBC;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * RF01: Autenticar usuario (versión Servlet + JSP).
 * GET  /login -> muestra el formulario (login.jsp)
 * POST /login -> valida correo/contraseña contra la tabla 'usuarios'
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String correoParam = request.getParameter("correo");
        String correo = correoParam == null ? "" : correoParam.trim().toLowerCase();
        String contrasena = request.getParameter("contrasena");
        String sql = "SELECT nombre1, contrasena FROM usuarios WHERE correo = ? AND estado = 'Activo'";

        try (Connection conexion = ConexionJDBC.obtenerConexion();
             PreparedStatement statement = conexion.prepareStatement(sql)) {

            statement.setString(1, correo);

            try (ResultSet resultado = statement.executeQuery()) {
                if (resultado.next() && contrasena != null
                        && ENCODER.matches(contrasena, resultado.getString("contrasena"))) {
                    HttpSession sesion = request.getSession();
                    sesion.setAttribute("nombreUsuario", resultado.getString("nombre1"));
                    response.sendRedirect(request.getContextPath() + "/empresas");
                    return;
                }
            }
            request.setAttribute("mensajeError", "Correo o contraseña incorrectos.");
        } catch (SQLException e) {
            request.setAttribute("mensajeError", "Error de conexión: " + e.getMessage());
        }

        request.getRequestDispatcher("/login.jsp").forward(request, response);
    }
    
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);
}