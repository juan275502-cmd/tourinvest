package com.tourinvest.backend.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtUtil")
class JwtUtilTest {

    // JwtUtil no tiene dependencias externas (solo criptografía), así que no usamos
    // @ExtendWith(MockitoExtension.class) ni mocks: se instancia directo y se inyectan
    // los campos @Value manualmente, sin levantar el contexto de Spring.
    private JwtUtil jwtUtil;

    private static final String SECRETO_PRUEBA = "clave-secreta-de-pruebas-de-al-menos-32-caracteres";

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRETO_PRUEBA);
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3_600_000L); // 1 hora
    }

    @Test
    @DisplayName("generarToken: produce un token JWT no vacío con 3 segmentos (header.payload.signature)")
    void generarToken_devuelveTokenConFormatoValido() {
        String token = jwtUtil.generarToken("kike@tourinvest.com", "Inversionista");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extraerCorreo: recupera exactamente el correo usado al generar el token")
    void extraerCorreo_devuelveElCorreoOriginal() {
        String token = jwtUtil.generarToken("marlen@tourinvest.com", "Analista");

        String correoExtraido = jwtUtil.extraerCorreo(token);

        assertThat(correoExtraido).isEqualTo("marlen@tourinvest.com");
    }

    @Test
    @DisplayName("esTokenValido: true cuando el correo coincide y el token no ha expirado")
    void esTokenValido_correoCoincideYNoExpirado_devuelveTrue() {
        String token = jwtUtil.generarToken("juan@tourinvest.com", "Administrador");

        boolean esValido = jwtUtil.esTokenValido(token, "juan@tourinvest.com");

        assertThat(esValido).isTrue();
    }

    @Test
    @DisplayName("esTokenValido: false cuando el correo no coincide con el del token")
    void esTokenValido_correoNoCoincide_devuelveFalse() {
        String token = jwtUtil.generarToken("juan@tourinvest.com", "Administrador");

        boolean esValido = jwtUtil.esTokenValido(token, "otro@tourinvest.com");

        assertThat(esValido).isFalse();
    }

    @Test
    @DisplayName("esTokenValido: un token ya expirado lanza ExpiredJwtException al intentar leerlo")
    void token_yaExpirado_lanzaExpiredJwtExceptionAlValidar() {
        // Expiración en el pasado (-1 minuto) para forzar el vencimiento inmediato.
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -60_000L);
        String tokenExpirado = jwtUtil.generarToken("kike@tourinvest.com", "Inversionista");

        // jjwt lanza la excepción al parsear un token vencido, incluso solo para extraer el correo.
        assertThatThrownBy(() -> jwtUtil.esTokenValido(tokenExpirado, "kike@tourinvest.com"))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("dos tokens generados para el mismo usuario en el mismo instante son idénticos en su claim de correo")
    void generarToken_conMismosDatos_extraeSiempreElMismoCorreo() {
        String token1 = jwtUtil.generarToken("repetido@tourinvest.com", "Inversionista");
        String token2 = jwtUtil.generarToken("repetido@tourinvest.com", "Inversionista");

        assertThat(jwtUtil.extraerCorreo(token1)).isEqualTo(jwtUtil.extraerCorreo(token2));
    }
}