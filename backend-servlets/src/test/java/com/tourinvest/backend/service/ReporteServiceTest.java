package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.IndicadorLiquidezRequest;
import com.tourinvest.backend.dto.IndicadorLiquidezResponse;
import com.tourinvest.backend.dto.ReporteRequest;
import com.tourinvest.backend.dto.ReporteResumenDTO;
import com.tourinvest.backend.model.*;
import com.tourinvest.backend.repository.EmpresaRepository;
import com.tourinvest.backend.repository.ReporteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReporteService")
class ReporteServiceTest {

    @Mock
    private ReporteRepository reporteRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    private ReporteService reporteService;

    private Usuario analista;
    private Empresa ghl;

    @BeforeEach
    void setUp() {
        reporteService = new ReporteService(reporteRepository, empresaRepository);

        Rol rolAnalista = new Rol(Rol.NombreRol.Analista);
        rolAnalista.setIdRol(2);

        analista = new Usuario();
        analista.setIdUsuario(2);
        analista.setNombre1("Laura");
        analista.setRol(rolAnalista);

        ghl = new Empresa();
        ghl.setIdEmpresa(1);
        ghl.setNombre("GHL Hoteles");
        ghl.setSimbolo("GHL");
        ghl.setSector("Hotelería");
        ghl.setPais("Colombia");
    }

    // ---------- CREAR ----------

    @Test
    @DisplayName("crear: con empresa existente, guarda el reporte con el analista autenticado como autor")
    void crear_empresaExistente_guardaReporteConAutorCorrecto() {
        ReporteRequest request = new ReporteRequest();
        request.setIdEmpresa(1);
        request.setTitulo("Análisis de liquidez Q3");
        request.setDescripcion("La empresa presenta buena salud financiera.");

        when(empresaRepository.findById(1)).thenReturn(Optional.of(ghl));
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(invocation -> {
            Reporte r = invocation.getArgument(0);
            r.setIdReporte(10);
            return r;
        });

        ReporteResumenDTO resultado = reporteService.crear(analista, request);

        assertThat(resultado.getIdReporte()).isEqualTo(10);
        assertThat(resultado.getTitulo()).isEqualTo("Análisis de liquidez Q3");
        assertThat(resultado.getNombreEmpresa()).isEqualTo("GHL Hoteles");
        assertThat(resultado.getAutor()).isEqualTo("Laura");

        verify(reporteRepository).save(any(Reporte.class));
    }

    @Test
    @DisplayName("crear: con empresa inexistente, lanza NoSuchElementException y no guarda nada")
    void crear_empresaInexistente_lanzaNoSuchElementException() {
        ReporteRequest request = new ReporteRequest();
        request.setIdEmpresa(999);
        request.setTitulo("Reporte fantasma");

        when(empresaRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reporteService.crear(analista, request))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("La empresa indicada no existe");
    }

    // ---------- LISTAR ----------

    @Test
    @DisplayName("listarTodos: mapea correctamente cada reporte a su DTO")
    void listarTodos_devuelveListaMapeadaCorrectamente() {
        Reporte reporte = new Reporte();
        reporte.setIdReporte(1);
        reporte.setUsuario(analista);
        reporte.setEmpresa(ghl);
        reporte.setTitulo("Reporte GHL Hoteles");
        reporte.setDescripcion("Tendencia alcista.");

        when(reporteRepository.findAllConEmpresaYUsuario()).thenReturn(List.of(reporte));

        List<ReporteResumenDTO> resultado = reporteService.listarTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getNombreEmpresa()).isEqualTo("GHL Hoteles");
        assertThat(resultado.get(0).getAutor()).isEqualTo("Laura");
    }

    // ---------- INDICADOR DE LIQUIDEZ ----------

    @Test
    @DisplayName("calcularLiquidezCorriente: con valores válidos, devuelve activo/pasivo con 4 decimales")
    void calcularLiquidezCorriente_valoresValidos_calculaCorrectamente() {
        IndicadorLiquidezRequest request = new IndicadorLiquidezRequest();
        request.setActivoCorriente(new BigDecimal("45000"));
        request.setPasivoCorriente(new BigDecimal("37500"));

        IndicadorLiquidezResponse resultado = reporteService.calcularLiquidezCorriente(request);

        // 45000 / 37500 = 1.2
        assertThat(resultado.getLiquidezCorriente()).isEqualByComparingTo("1.2000");
    }

    @Test
    @DisplayName("calcularLiquidezCorriente: con pasivo corriente en cero, lanza ArithmeticException")
    void calcularLiquidezCorriente_pasivoCorrienteEnCero_lanzaArithmeticException() {
        IndicadorLiquidezRequest request = new IndicadorLiquidezRequest();
        request.setActivoCorriente(new BigDecimal("45000"));
        request.setPasivoCorriente(BigDecimal.ZERO);

        assertThatThrownBy(() -> reporteService.calcularLiquidezCorriente(request))
                .isInstanceOf(ArithmeticException.class)
                .hasMessage("El pasivo corriente no puede ser cero");
    }
} 