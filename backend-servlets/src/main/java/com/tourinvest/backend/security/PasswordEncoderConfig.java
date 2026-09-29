package com.tourinvest.backend.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Bean de PasswordEncoder compartido por toda la aplicacion.
 *
 * Se separa de SecurityConfig para que la validacion de arranque
 * (JwtConfigValidator) pueda construirse sin arrastrar toda la cadena de
 * seguridad, y para que el codigo de BCrypt tenga un unico lugar de verdad.
 */
@Configuration
public class PasswordEncoderConfig {

    /** Coste 10: equilibrio recomendado entre seguridad y latencia de login. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Guard de arranque ACTIVO SOLO en el perfil `prod`.
     *
     * Falla rapido (fail-fast) si la aplicacion llega a produccion con
     * secretos de ejemplo o vacios, en vez de exponer la API con una clave
     * JWT conocida por cualquiera. En desarrollo no hace nada.
     */
    @Bean
    @Profile("prod")
    public JwtConfigValidator jwtConfigValidator(
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${jwt.secret:}") String jwtSecret,
            @Value("${jwt.expiration-ms:0}") long jwtExpirationMs) {
        return new JwtConfigValidator(dbPassword, jwtSecret, jwtExpirationMs);
    }

    /** Resultado de la inspeccion de configuracion; lanza si algo esta mal. */
    public record JwtConfigValidator(String dbPassword, String jwtSecret, long jwtExpirationMs) {

        // Claves de desarrollo que jamais deben llegar a produccion.
        private static final List<String> CLAVES_PROHIBIDAS = Arrays.asList(
                "clave-de-desarrollo-tourinvest-cambiar-en-produccion-0123456789",
                "cambiar-esta-clave-secreta-por-una-aleatoria-de-32+");

        public JwtConfigValidator {
            List<String> errores = new ArrayList<>();

            if (dbPassword == null || dbPassword.isBlank()) {
                errores.add("DB_PASSWORD vacio: define la contrasena de MySQL.");
            }
            if (jwtSecret == null || jwtSecret.isBlank()) {
                errores.add("JWT_SECRET vacio: define la clave de firma de los tokens.");
            } else {
                if (CLAVES_PROHIBIDAS.contains(jwtSecret)) {
                    errores.add("JWT_SECRET es el valor de ejemplo: genera una clave propia.");
                }
                if (jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
                    errores.add("JWT_SECRET debe tener al menos 32 bytes (HMAC-SHA256).");
                }
            }
            if (jwtExpirationMs <= 0) {
                errores.add("JWT_EXPIRATION_MS debe ser un numero positivo de milisegundos.");
            }

            if (!errores.isEmpty()) {
                throw new IllegalStateException(
                        "Configuracion de produccion invalida:\n  - " + String.join("\n  - ", errores));
            }
        }
    }
}