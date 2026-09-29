package com.tourinvest.backend.controller;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud (RF de operación). Es publico (ver SecurityConfig) para
 * que un smoke test en Postman o un monitor externo confirme que la API esta
 * viva y que la base de datos responde, sin necesitar un token JWT.
 *
 * Contrato:
 *   GET /api/health -> 200 {"estado":"UP","baseDatos":"UP",...}
 *                    -> 503 {"estado":"DOWN","baseDatos":"DOWN",...}
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean baseDatosUp = probarBaseDatos();

        body.put("estado", baseDatosUp ? "UP" : "DOWN");
        body.put("baseDatos", baseDatosUp ? "UP" : "DOWN");
        body.put("servicio", "tourinvest-backend");
        body.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity
                .status(baseDatosUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(body);
    }

    /** Abre una conexion minima contra la BD configurada para confirmar que responde. */
    private boolean probarBaseDatos() {
        try (java.sql.Connection conexion = dataSource.getConnection()) {
            return conexion.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}