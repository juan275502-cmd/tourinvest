package com.tourinvest.backend.repository;

import com.tourinvest.backend.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReporteRepository extends JpaRepository<Reporte, Integer> {
    List<Reporte> findByUsuario_IdUsuario(Integer idUsuario);
    List<Reporte> findByEmpresa_IdEmpresa(Integer idEmpresa);

    /**
     * Listado con fetch join de las dos asociaciones que necesita el DTO
     * ({@code nombreEmpresa} y {@code autor}).
     *
     * <p>Sin el JOIN, {@code empresa} y {@code usuario} se cargan como proxies
     * LAZY y el mapeo a DTO ocurre fuera de la sesion de JPA, lo que produce
     * {@code LazyInitializationException} (HTTP 500) cuando
     * {@code spring.jpa.open-in-view=false}, que es la configuracion correcta
     * para produccion.
     */
    @Query("SELECT r FROM Reporte r JOIN FETCH r.empresa e JOIN FETCH r.usuario u ORDER BY r.idReporte")
    List<Reporte> findAllConEmpresaYUsuario();
}
