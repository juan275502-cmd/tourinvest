package com.tourinvest.backend.service;

import com.tourinvest.backend.dto.ResumenPortafolioResponse;
import com.tourinvest.backend.model.*;
import com.tourinvest.backend.repository.AccionRepository;
import com.tourinvest.backend.repository.AlertaRepository;
import com.tourinvest.backend.repository.InversionRepository;
import com.tourinvest.backend.repository.PortafolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortafolioService")
class PortafolioServiceTest {

    @Mock
    private InversionRepository inversionRepository;

    @Mock
    private AlertaRepository alertaRepository;

    @Mock
    private PortafolioRepository portafolioRepository;

    @Mock
    private AccionRepository accionRepository;

    private PortafolioService portafolioService;

    private Usuario usuario;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        portafolioService = new PortafolioService(inversionRepository, alertaRepository, portafolioRepository, accionRepository);

        Rol rol = new Rol(Rol.NombreRol.Inversionista);
        rol.setIdRol(3);

        usuario = new Usuario();
        usuario.setIdUsuario(3);
        usuario.setNombre1("Carlos");
        usuario.setRol(rol);
    }

    private Accion crearAccion(String simbolo, String nombreEmpresa, String precioActual) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(1);
        empresa.setNombre(nombreEmpresa);
        empresa.setSimbolo(simbolo);
        empresa.setSector("Hotelería");
        empresa.setPais("Colombia");

        Accion accion = new Accion();
        accion.setIdAccion(1);
        accion.setEmpresa(empresa);
        accion.setPrecio(new BigDecimal(precioActual));
        accion.setFechaActualizacion(LocalDateTime.now());
        return accion;
    }

    private Inversion crearInversion(Accion accion, int cantidad, String precioCompra) {
        Inversion inversion = new Inversion();
        inversion.setIdInversion(1);
        inversion.setAccion(accion);
        inversion.setCantidad(cantidad);
        inversion.setPrecioCompra(new BigDecimal(precioCompra));
        inversion.setFechaCompra(LocalDate.now());
        return inversion;
    }

    @Test
    @DisplayName("obtenerResumen: con inversiones activas, calcula valor total, invertido y rendimiento correctamente")
    void obtenerResumen_conInversiones_calculaTotalesCorrectamente() {
        // Compra: 10 acciones a $180 = $1800 invertidos. Precio actual: $195.50 -> valor actual $1955
        Accion ghl = crearAccion("GHL", "GHL Hoteles", "195.50");
        Inversion inversion = crearInversion(ghl, 10, "180.00");

        when(inversionRepository.findByUsuario(3)).thenReturn(List.of(inversion));
        when(alertaRepository.findByUsuario_IdUsuarioAndEstado(3, Alerta.EstadoAlerta.Activa))
                .thenReturn(List.of());

        ResumenPortafolioResponse resumen = portafolioService.obtenerResumen(usuario);

        assertThat(resumen.getNombreUsuario()).isEqualTo("Carlos");
        assertThat(resumen.getValorTotal()).isEqualByComparingTo("1955.00");
        assertThat(resumen.getTotalInvertido()).isEqualByComparingTo("1800.00");
        // Rendimiento: (1955 - 1800) / 1800 * 100 = 8.6111%
        assertThat(resumen.getRendimientoTotalPorcentual()).isEqualByComparingTo("8.6111");

        assertThat(resumen.getPosiciones()).hasSize(1);
        assertThat(resumen.getPosiciones().get(0).getSimbolo()).isEqualTo("GHL");
        assertThat(resumen.getPosiciones().get(0).getValorActual()).isEqualByComparingTo("1955.00");
    }

    @Test
    @DisplayName("obtenerResumen: sin inversiones, devuelve totales en cero sin lanzar excepción (evita división por cero)")
    void obtenerResumen_sinInversiones_devuelveTotalesEnCero() {
        when(inversionRepository.findByUsuario(3)).thenReturn(List.of());
        when(alertaRepository.findByUsuario_IdUsuarioAndEstado(3, Alerta.EstadoAlerta.Activa))
                .thenReturn(List.of());

        ResumenPortafolioResponse resumen = portafolioService.obtenerResumen(usuario);

        assertThat(resumen.getValorTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumen.getTotalInvertido()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumen.getRendimientoTotalPorcentual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumen.getPosiciones()).isEmpty();
    }

    @Test
    @DisplayName("obtenerResumen: mapea correctamente las alertas activas del usuario")
    void obtenerResumen_conAlertasActivas_lasIncluyeEnLaRespuesta() {
        Accion dec = crearAccion("DEC", "Decameron", "250.80");

        Alerta alerta = new Alerta();
        alerta.setIdAlerta(7);
        alerta.setUsuario(usuario);
        alerta.setAccion(dec);
        alerta.setPrecioObjetivo(new BigDecimal("300.00"));
        alerta.setEstado(Alerta.EstadoAlerta.Activa);

        when(inversionRepository.findByUsuario(3)).thenReturn(List.of());
        when(alertaRepository.findByUsuario_IdUsuarioAndEstado(3, Alerta.EstadoAlerta.Activa))
                .thenReturn(List.of(alerta));

        ResumenPortafolioResponse resumen = portafolioService.obtenerResumen(usuario);

        assertThat(resumen.getAlertasActivas()).hasSize(1);
        assertThat(resumen.getAlertasActivas().get(0).getSimbolo()).isEqualTo("DEC");
        assertThat(resumen.getAlertasActivas().get(0).getPrecioObjetivo()).isEqualByComparingTo("300.00");
        assertThat(resumen.getAlertasActivas().get(0).getPrecioActual()).isEqualByComparingTo("250.80");
        assertThat(resumen.getAlertasActivas().get(0).getEstado()).isEqualTo("Activa");
    }

    @Test
    @DisplayName("obtenerResumen: con múltiples posiciones, suma correctamente cada una al total")
    void obtenerResumen_conMultiplesPosiciones_sumaCorrectamente() {
        Accion ghl = crearAccion("GHL", "GHL Hoteles", "200.00");
        Accion dec = crearAccion("DEC", "Decameron", "250.00");

        Inversion inv1 = crearInversion(ghl, 10, "180.00"); // valor actual: 2000
        Inversion inv2 = crearInversion(dec, 5, "260.00");  // valor actual: 1250 (pérdida)

        when(inversionRepository.findByUsuario(3)).thenReturn(List.of(inv1, inv2));
        when(alertaRepository.findByUsuario_IdUsuarioAndEstado(3, Alerta.EstadoAlerta.Activa))
                .thenReturn(List.of());

        ResumenPortafolioResponse resumen = portafolioService.obtenerResumen(usuario);

        assertThat(resumen.getPosiciones()).hasSize(2);
        assertThat(resumen.getValorTotal()).isEqualByComparingTo("3250.00"); // 2000 + 1250
        assertThat(resumen.getTotalInvertido()).isEqualByComparingTo("3100.00"); // 1800 + 1300
    }

    // ---------- AGREGAR INVERSION ----------

    @Test
    @DisplayName("agregarInversion: con portafolio existente, usa ese portafolio y toma el precio de mercado actual")
    void agregarInversion_conPortafolioExistente_usaElPrimeroYPrecioDeMercado() {
        Accion ghl = crearAccion("GHL", "GHL Hoteles", "195.50");
        com.tourinvest.backend.dto.InversionRequest request = new com.tourinvest.backend.dto.InversionRequest();
        request.setIdAccion(1);
        request.setCantidad(10);

        Portafolio portafolioExistente = new Portafolio();
        portafolioExistente.setIdPortafolio(1);
        portafolioExistente.setUsuario(usuario);
        portafolioExistente.setNombre("Portafolio Principal");

        when(portafolioRepository.findByUsuario_IdUsuario(3)).thenReturn(List.of(portafolioExistente));
        when(accionRepository.findById(1)).thenReturn(java.util.Optional.of(ghl));
        when(inversionRepository.save(org.mockito.ArgumentMatchers.any(Inversion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var posicion = portafolioService.agregarInversion(usuario, request);

        assertThat(posicion.getSimbolo()).isEqualTo("GHL");
        assertThat(posicion.getCantidad()).isEqualTo(10);
        // El precio de compra debe ser el precio de MERCADO actual, no uno inventado por el cliente
        assertThat(posicion.getPrecioCompra()).isEqualByComparingTo("195.50");

        org.mockito.Mockito.verify(portafolioRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("agregarInversion: sin portafolio previo, crea uno por defecto llamado 'Portafolio Principal'")
    void agregarInversion_sinPortafolioPrevio_creaPortafolioPorDefecto() {
        Accion dec = crearAccion("DEC", "Decameron", "250.80");
        com.tourinvest.backend.dto.InversionRequest request = new com.tourinvest.backend.dto.InversionRequest();
        request.setIdAccion(1);
        request.setCantidad(3);

        Portafolio portafolioCreado = new Portafolio();
        portafolioCreado.setIdPortafolio(99);
        portafolioCreado.setUsuario(usuario);
        portafolioCreado.setNombre("Portafolio Principal");

        when(portafolioRepository.findByUsuario_IdUsuario(3)).thenReturn(List.of());
        when(portafolioRepository.save(org.mockito.ArgumentMatchers.any(Portafolio.class))).thenReturn(portafolioCreado);
        when(accionRepository.findById(1)).thenReturn(java.util.Optional.of(dec));
        when(inversionRepository.save(org.mockito.ArgumentMatchers.any(Inversion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var posicion = portafolioService.agregarInversion(usuario, request);

        assertThat(posicion.getSimbolo()).isEqualTo("DEC");
        org.mockito.Mockito.verify(portafolioRepository).save(org.mockito.ArgumentMatchers.argThat(
                p -> p.getNombre().equals("Portafolio Principal") && p.getUsuario().equals(usuario)));
    }

    @Test
    @DisplayName("agregarInversion: si la acción no existe, lanza NoSuchElementException y no guarda nada")
    void agregarInversion_accionInexistente_lanzaNoSuchElementException() {
        com.tourinvest.backend.dto.InversionRequest request = new com.tourinvest.backend.dto.InversionRequest();
        request.setIdAccion(999);
        request.setCantidad(1);

        Portafolio portafolioExistente = new Portafolio();
        portafolioExistente.setIdPortafolio(1);
        portafolioExistente.setUsuario(usuario);

        when(portafolioRepository.findByUsuario_IdUsuario(3)).thenReturn(List.of(portafolioExistente));
        when(accionRepository.findById(999)).thenReturn(java.util.Optional.empty());

        NoSuchElementException excepcion = org.junit.jupiter.api.Assertions.assertThrows(
                NoSuchElementException.class,
                () -> portafolioService.agregarInversion(usuario, request));

        assertThat(excepcion.getMessage()).isEqualTo("La acción indicada no existe");

        org.mockito.Mockito.verify(inversionRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }
}