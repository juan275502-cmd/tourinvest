package com.tourinvest.backend.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MayorDeEdad")
class MayorDeEdadValidatorTest {

    private final MayorDeEdadValidator validator = new MayorDeEdadValidator();

    private MayorDeEdad anotacion(int minima) {
        MayorDeEdad a = mock(MayorDeEdad.class);
        when(a.minima()).thenReturn(minima);
        return a;
    }

    private boolean esValida(LocalDate fecha, int minima) {
        validator.initialize(anotacion(minima));
        return validator.isValid(fecha, mock(ConstraintValidatorContext.class));
    }

    // ---------- CASO LÍMITE: CUMPLE 18 HOY ----------

    @Test
    @DisplayName("quien cumple 18 años HOY ya es mayor de edad y se acepta")
    void cumpleDieciochoHoy_seAcepta() {
        LocalDate hoy = LocalDate.now();
        assertThat(esValida(hoy.minusYears(18), 18)).isTrue();
    }

    @Test
    @DisplayName("un día antes de cumplir 18 todavía es menor de edad")
    void unDiaAntesDeCumplirDieciocho_seRechaza() {
        LocalDate mananaCumple = LocalDate.now().minusYears(18).plusDays(1);
        assertThat(esValida(mananaCumple, 18)).isFalse();
    }

    @Test
    @DisplayName("un día después de cumplir 18 ya se acepta")
    void unDiaDespuesDeCumplirDieciocho_seAcepta() {
        LocalDate ayerCumplio = LocalDate.now().minusYears(18).minusDays(1);
        assertThat(esValida(ayerCumplio, 18)).isTrue();
    }

    // ---------- MENORES DE EDAD ----------

    @Test
    @DisplayName("un menor de 17 años se rechaza")
    void menorDeDieciseis_seRechaza() {
        assertThat(esValida(LocalDate.now().minusYears(17), 18)).isFalse();
    }

    @Test
    @DisplayName("un recién nacido se rechaza")
    void recienNacido_seRechaza() {
        assertThat(esValida(LocalDate.now().minusMonths(6), 18)).isFalse();
    }

    @Test
    @DisplayName("un adulto de 30 años se acepta")
    void adulto_seAcepta() {
        assertThat(esValida(LocalDate.now().minusYears(30), 18)).isTrue();
    }

    // ---------- DELEGADOS EN @NotNull Y @Past ----------

    @Test
    @DisplayName("una fecha nula se deja a @NotNull (sin doble mensaje)")
    void nula_seDejaANotNull() {
        assertThat(esValida(null, 18)).isTrue();
    }

    @Test
    @DisplayName("hoy o una fecha futura se dejan a @Past (sin doble mensaje)")
    void hoyOFutura_seDejaAPast() {
        assertThat(esValida(LocalDate.now(), 18)).isTrue();
        assertThat(esValida(LocalDate.now().plusYears(1), 18)).isTrue();
    }

    // ---------- EDAD MÍNIMA CONFIGURABLE ----------

    @Test
    @DisplayName("la edad mínima por defecto de la anotación es 18")
    void edadMinimaPorDefecto() throws Exception {
        // Se lee del propio código de la anotación (una interfaz no tiene
        // implementación que invocar, así que aquí no sirve un mock).
        Object porDefecto = MayorDeEdad.class.getDeclaredMethod("minima").getDefaultValue();

        assertThat(porDefecto).isEqualTo(18);
    }

    @Test
    @DisplayName("se puede exigir otra edad mínima (por ejemplo 21)")
    void edadMinimaConfigurable() {
        assertThat(esValida(LocalDate.now().minusYears(18), 21)).isFalse();
        assertThat(esValida(LocalDate.now().minusYears(21), 21)).isTrue();
    }
}