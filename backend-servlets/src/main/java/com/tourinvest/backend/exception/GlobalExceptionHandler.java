package com.tourinvest.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return construirRespuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return construirRespuesta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

        @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // Pasivo corriente = 0 en el indicador de liquidez -> 400 (no 500)
    @ExceptionHandler(ArithmeticException.class)
    public ResponseEntity<Map<String, Object>> handleArithmetic(ArithmeticException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Un mismo campo puede acumular varias violaciones (p. ej. "123456"
        // incumple longitud, mayusculas, numeros y especiales). Se concatenan
        // en un solo mensaje para que el frontend pueda pintarlo tal cual bajo
        // el input, en vez de perder silenciosamente todas menos la ultima.
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errores.merge(err.getField(), err.getDefaultMessage(),
                        (anterior, nuevo) -> anterior + " " + nuevo));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errores", errores);

        return ResponseEntity.badRequest().body(body);
    }

    // JSON ilegible o con un tipo/fecha que no se puede convertir
    // (p. ej. "fechaNacimiento": "1999-13-45" o "20/03/1999"). Sin este
    // bloque Spring responderia con su cuerpo de error por defecto, que el
    // frontend no entiende al no traer el campo "mensaje".
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBodyIlegible(HttpMessageNotReadableException ex) {
        return construirRespuesta(HttpStatus.BAD_REQUEST,
                "Los datos enviados no son válidos. Revisa el formato de los campos "
                        + "(por ejemplo, la fecha debe ser AAAA-MM-DD).");
    }

    // 403 - autenticado pero sin permisos para el recurso (p. ej. un Analista
    // intentando POST /empresas, que exige ROLE_ADMINISTRADOR).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return construirRespuesta(HttpStatus.FORBIDDEN,
                "No tienes permisos para acceder a este recurso con tu rol.");
    }

    // 401 - sin token, con token invalido o con token caducado.
    // Spring Security lanza esto antes de entrar al controller, asi que se
    // configura en el entry point de la cadena (ver SecurityConfig) para que la
    // respuesta sea JSON y no la pagina de error por defecto.
    public static Map<String, Object> cuerpoNoAutenticado(int status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("mensaje", mensaje);
        return body;
    }

    private ResponseEntity<Map<String, Object>> construirRespuesta(HttpStatus status, String mensaje) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}