package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.IndicadorLiquidezRequest;
import com.tourinvest.backend.dto.IndicadorLiquidezResponse;
import com.tourinvest.backend.dto.ReporteRequest;
import com.tourinvest.backend.dto.ReporteResumenDTO;
import com.tourinvest.backend.model.Empresa;
import com.tourinvest.backend.model.Reporte;
import com.tourinvest.backend.model.Usuario;
import com.tourinvest.backend.repository.EmpresaRepository;
import com.tourinvest.backend.repository.ReporteRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ReporteService {

    private final ReporteRepository reporteRepository;
    private final EmpresaRepository empresaRepository;

    public ReporteService(ReporteRepository reporteRepository, EmpresaRepository empresaRepository) {
        this.reporteRepository = reporteRepository;
        this.empresaRepository = empresaRepository;
    }

    /** RF10 - Crear análisis. El autor es siempre el analista autenticado, nunca un valor del cliente. */
    public ReporteResumenDTO crear(Usuario analista, ReporteRequest request) {
        Empresa empresa = empresaRepository.findById(request.getIdEmpresa())
                .orElseThrow(() -> new NoSuchElementException("La empresa indicada no existe"));

        Reporte reporte = new Reporte();
        reporte.setUsuario(analista);
        reporte.setEmpresa(empresa);
        reporte.setTitulo(request.getTitulo());
        reporte.setDescripcion(request.getDescripcion());

        Reporte guardado = reporteRepository.save(reporte);
        return mapearADTO(guardado);
    }

    public List<ReporteResumenDTO> listarTodos() {
        // Usa la consulta con fetch join: el DTO necesita empresa y usuario, y con
        // open-in-view=false los proxies LAZY no pueden inicializarse al mapear.
        return reporteRepository.findAllConEmpresaYUsuario().stream()
                .map(this::mapearADTO)
                .toList();
    }

    /**
     * Indicador de liquidez corriente = activo corriente / pasivo corriente.
     * No se persiste: es un cálculo puntual que el analista hace desde el panel de Indicadores.
     */
    public IndicadorLiquidezResponse calcularLiquidezCorriente(IndicadorLiquidezRequest request) {
        if (request.getPasivoCorriente().compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("El pasivo corriente no puede ser cero");
        }
        BigDecimal resultado = request.getActivoCorriente()
                .divide(request.getPasivoCorriente(), 4, RoundingMode.HALF_UP);
        return new IndicadorLiquidezResponse(resultado);
    }

    private ReporteResumenDTO mapearADTO(Reporte reporte) {
        return new ReporteResumenDTO(
                reporte.getIdReporte(),
                reporte.getTitulo(),
                reporte.getDescripcion(),
                reporte.getEmpresa().getNombre(),
                reporte.getUsuario().getNombre1(),
                reporte.getFechaGeneracion());
    }
}