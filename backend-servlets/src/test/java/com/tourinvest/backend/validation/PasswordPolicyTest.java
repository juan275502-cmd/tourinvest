package com.tourinvest.backend.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordPolicy")
class PasswordPolicyTest {

    // ---------- LA POLÍTICA PEDIDA ----------

    @Test
    @DisplayName("la política es 12 caracteres con 2 mayúsculas, 2 minúsculas, 2 números y 2 especiales")
    void politica_configurada() {
        assertThat(PasswordPolicy.LONGITUD_MINIMA).isEqualTo(12);
        assertThat(PasswordPolicy.MINIMO_MAYUSCULAS).isEqualTo(2);
        assertThat(PasswordPolicy.MINIMO_MINUSCULAS).isEqualTo(2);
        assertThat(PasswordPolicy.MINIMO_NUMEROS).isEqualTo(2);
        assertThat(PasswordPolicy.MINIMO_ESPECIALES).isEqualTo(2);
    }

    @Test
    @DisplayName("el máximo es 72, el límite de BCrypt")
    void maximo_esElLimiteDeBcrypt() {
        assertThat(PasswordPolicy.LONGITUD_MAXIMA).isEqualTo(72);
    }

    @Test
    @DisplayName("una contraseña que cumple la política pasa")
    void cumpleLaPolitica() {
        assertThat(PasswordPolicy.esSegura("TourInvest2026*!")).isTrue();
    }

    @Test
    @DisplayName("con 12 caracteres justos y las cuatro reglas ya es válida")
    void cumple_conDoceCaracteresJustos() {
        // A B 1 2 $ c d 3 4 ! x y  -> 2 mayús, 4 díg., 2 especiales, 4 minús
        assertThat("AB12$cd34!xy").hasSize(12);
        assertThat(PasswordPolicy.esSegura("AB12$cd34!xy")).isTrue();
    }

    // ---------- PV-11: LONGITUD ----------

    @Test
    @DisplayName("con 11 caracteres se rechaza aunque cumpla las demás reglas")
    void onceCaracteres_seRechaza() {
        assertThat("AB12$cd34!x").hasSize(11);
        assertThat(PasswordPolicy.esSegura("AB12$cd34!x")).isFalse();
        assertThat(PasswordPolicy.errores("AB12$cd34!x")).containsExactly("mínimo 12 caracteres");
    }

    @Test
    @DisplayName("más de 72 caracteres se rechaza")
    void masDeSetentaYDos_seRechaza() {
        String larga = "TourInvest2026*!" + "a".repeat(70);
        assertThat(larga).hasSize(86);
        assertThat(PasswordPolicy.esSegura(larga)).isFalse();
        assertThat(PasswordPolicy.errores(larga)).contains("máximo 72 caracteres");
    }

    // ---------- CADA REQUISITO POR SEPARADO ----------

    @Test
    @DisplayName("sin mayúsculas se informa de las mayúsculas")
    void sinMayusculas() {
        assertThat(PasswordPolicy.errores("abcdefghijkl"))
                .containsExactly("al menos 2 letras mayúsculas", "al menos 2 números",
                        "al menos 2 caracteres especiales");
    }

    @Test
    @DisplayName("sin minúsculas se informa de las minúsculas")
    void sinMinusculas() {
        assertThat(PasswordPolicy.errores("ABCDEFGHIJ12"))
                .contains("al menos 2 letras minúsculas", "al menos 2 caracteres especiales");
    }

    @Test
    @DisplayName("sin números se informa de los números")
    void sinNumeros() {
        assertThat(PasswordPolicy.errores("ABcdefghijk!!"))
                .containsExactly("al menos 2 números");
    }

    @Test
    @DisplayName("sin especiales se informa de los especiales")
    void sinEspeciales() {
        assertThat(PasswordPolicy.errores("ABcdefghij12"))
                .containsExactly("al menos 2 caracteres especiales");
    }

    @Test
    @DisplayName("con un solo elemento de cada categoría NO alcanza")
    void conUnoSoloNoAlcanza() {
        assertThat(PasswordPolicy.esSegura("Ab1$efghij1")).isFalse();
    }

    // ---------- TIPOS DE CARÁCTER ----------

    @Test
    @DisplayName("un espacio en blanco no cuenta como carácter especial")
    void elEspacioNoEsEspecial() {
        assertThat(PasswordPolicy.errores("Ab cd efgh12"))
                .contains("al menos 2 caracteres especiales");
    }

    @Test
    @DisplayName("las tildes y la eñe cuentan como letra, no como símbolo")
    void lasTildesSonLetras() {
        // Á, Ñ y A como mayúsculas; ñ, á, b, c y d como minúsculas; $ y % como especiales.
        String conTildes = "ÁñÑáAbcd12$%";
        assertThat(conTildes).hasSize(12);
        assertThat(PasswordPolicy.esSegura(conTildes)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TourInvest2026*!", "TourInvest2026#%", "AB12$cd34!xy", "aB12$cd34!XY"})
    @DisplayName("cualquier variante que cumpla los mínimos se acepta")
    void variantesValidas(String clave) {
        assertThat(PasswordPolicy.esSegura(clave)).isTrue();
    }

    // ---------- CAMPO VACÍO (PV-10) ----------

    @Test
    @DisplayName("nulo, vacío o en blanco no generan errores: los reporta @NotBlank")
    void vacio_loReportaNotBlank() {
        assertThat(PasswordPolicy.errores(null)).isEmpty();
        assertThat(PasswordPolicy.errores("")).isEmpty();
        assertThat(PasswordPolicy.errores("    ")).isEmpty();
        assertThat(PasswordPolicy.mensajeError(null)).isNull();
    }

    // ---------- MENSAJE ----------

    @Test
    @DisplayName("el mensaje enumera todos los requisitos incumplidos")
    void mensajeEnumeraTodosLosFallos() {
        assertThat(PasswordPolicy.mensajeError("abc"))
                .isEqualTo("La contraseña debe cumplir: mínimo 12 caracteres; al menos 2 letras "
                        + "mayúsculas; al menos 2 números; al menos 2 caracteres especiales.");
    }

    @Test
    @DisplayName("una contraseña válida no produce mensaje")
    void mensajeEsNullSiEsSegura() {
        assertThat(PasswordPolicy.mensajeError("TourInvest2026*!")).isNull();
    }
}