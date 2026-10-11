package com.tourinvest.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DatosPersonalesRequest")
class DatosPersonalesRequestTest {

    private final DatosPersonalesRequest request = new ActualizarPerfilRequest();

    @Test
    @DisplayName("el correo se recorta y pasa a minúsculas (PV-09)")
    void correo_seNormaliza() {
        request.setCorreo("  Ana@TourInvest.COM ");
        assertThat(request.getCorreo()).isEqualTo("ana@tourinvest.com");
    }

    @Test
    @DisplayName("nombre y cédula se recortan; los espacios internos se conservan")
    void nombreYCedula_seRecortan() {
        request.setNombre1("  María José ");
        request.setCedula(" 1009876543 ");
        assertThat(request.getNombre1()).isEqualTo("María José");
        assertThat(request.getCedula()).isEqualTo("1009876543");
    }

    @Test
    @DisplayName("solo espacios queda vacío, para que @NotBlank lo rechace (PV-02)")
    void soloEspacios_quedaVacio() {
        request.setNombre1("   ");
        assertThat(request.getNombre1()).isEmpty();
    }

    @Test
    @DisplayName("null se conserva: lo reporta @NotBlank y no lanza NullPointerException")
    void nulo_seConserva() {
        request.setCorreo(null);
        request.setCedula(null);
        assertThat(request.getCorreo()).isNull();
        assertThat(request.getCedula()).isNull();
    }
}