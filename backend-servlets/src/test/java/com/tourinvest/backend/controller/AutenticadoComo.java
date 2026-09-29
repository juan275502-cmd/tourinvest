package com.tourinvest.backend.controller;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.tourinvest.backend.model.Usuario;

/**
 * Utilidad para los tests {@code @WebMvcTest} con
 * {@code @AutoConfigureMockMvc(addFilters = false)}.
 *
 * <p>Por que existe: al desactivar los filtros, el
 * {@code SecurityMockMvcRequestPostProcessors.user(...)} NO llega a poblar el
 * {@link SecurityContextHolder}, porque esa propagacion la hace justamente el
 * filtro {@code SecurityContextHolderFilter} de la cadena de Spring Security.
 * Sin filtros, {@code @AuthenticationPrincipal Usuario} llega a {@code null} en
 * el controller y los stubs de Mockito ({@code eq(usuario)}) no casan,
 * produciendo fallos como {@code No value at JSON path "$.nombreUsuario"}.
 *
 * <p>Este post-processor fija el contexto directamente en el
 * {@link SecurityContextHolder}, que si lee el resolver de Spring. Como MockMvc
 * ejecuta la peticion en el mismo hilo, el controller ya lo encuentra.
 *
 * <p>Uso: {@code .with(AutenticadoComo.usuario(u))} y limpiar con
 * {@link #limpiar()} en un {@code @AfterEach}.
 */
final class AutenticadoComo {

    private AutenticadoComo() {
    }

    /** Post-processor que autentica la peticion como el usuario dado. */
    static RequestPostProcessor usuario(Usuario usuario) {
        return peticion -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
            return peticion;
        };
    }

    /** Limpia el contexto para que un test no contamine al siguiente. */
    static void limpiar() {
        SecurityContextHolder.clearContext();
    }
}