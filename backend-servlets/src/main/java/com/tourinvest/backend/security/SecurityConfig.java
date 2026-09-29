package com.tourinvest.backend.security;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tourinvest.backend.repository.UsuarioRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String origenesCors;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}")
                          String origenesCors) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.origenesCors = origenesCors;
    }

    // El PasswordEncoder vive en PasswordEncoderConfig (coste 10) para que el
    // validador de arranque de produccion pueda reutilizarlo sin crear un
    // segundo bean con el mismo nombre.

    /**
     * UserDetailsService respaldado en la BD (tabla usuarios). Usuario ya
     * implementa UserDetails. Sin este bean el login no valida contra la BD
     * y AuthenticationConfiguration degenera en recursion infinita
     * (StackOverflowError -> /error -> 403 vacio).
     */
    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return correo -> usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correo));
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider proveedor = new DaoAuthenticationProvider();
        proveedor.setUserDetailsService(userDetailsService);
        proveedor.setPasswordEncoder(passwordEncoder);
        return proveedor;
    }

    /** Manager construido DIRECTAMENTE sobre el provider (evita la recursion de config.getAuthenticationManager()). */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracion = new CorsConfiguration();
        // Origenes configurables (ver application.properties / CORS_ALLOWED_ORIGINS).
        // Por defecto: el sitio en el puerto 80 (`http://www.tourinvest.com`,
        // sin ":80" porque es el puerto por defecto) y VS Code "Live Server" (:5500).
        List<String> permitidos = Arrays.stream(origenesCors.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .toList();
        configuracion.setAllowedOrigins(permitidos);
        // PATCH es requerido por /alertas/{id}/cancelar, /admin/usuarios/{id}/{suspender|activar} y /admin/**.
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setAllowCredentials(true);
        // Exponer Content-Length permite que el cliente (fetch/Postman) lea el tamano de la respuesta.
        configuracion.setExposedHeaders(List.of("Content-Length"));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", configuracion);
        return fuente;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Respuestas JSON coherentes para 401/403 en vez de la pagina de error HTML.
            .exceptionHandling(excepciones -> excepciones
                    .authenticationEntryPoint((peticion, respuesta, excepcion) -> {
                        respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        respuesta.setContentType("application/json;charset=UTF-8");
                        responseBodyWriter(respuesta,
                                com.tourinvest.backend.exception.GlobalExceptionHandler.cuerpoNoAutenticado(
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        "Credenciales ausentes o token invalido. Enviar 'Authorization: Bearer <token>'."));
                    })
                    .accessDeniedHandler((peticion, respuesta, excepcion) -> {
                        respuesta.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        respuesta.setContentType("application/json;charset=UTF-8");
                        responseBodyWriter(respuesta,
                                com.tourinvest.backend.exception.GlobalExceptionHandler.cuerpoNoAutenticado(
                                        HttpServletResponse.SC_FORBIDDEN,
                                        "Tu rol no tiene permisos para esta operacion."));
                    }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/error").permitAll()   // deja ver los errores del servidor tal cual (no como 403 opaco)
                // Sondas de salud: publicas para monitorizacion y smoke tests sin token.
                .requestMatchers("/api/health", "/actuator/health").permitAll()
                // Gestión de Empresas (CRUD) reservada al Administrador; la consulta queda para cualquier autenticado
                .requestMatchers(HttpMethod.POST, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                // El CRUD de usuarios cuelga de /admin/**, asi que hereda el rol
                // de Administrador por la regla anterior. Se deja explicito para
                // que quede claro quien puede crear/editar/eliminar usuarios.
                .requestMatchers(HttpMethod.POST, "/admin/usuarios").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/admin/usuarios/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/admin/usuarios/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/analista/**").hasRole("ANALISTA")
                .requestMatchers("/inversionista/**").hasRole("INVERSIONISTA")
                // Perfil propio: lo puede usar CUALQUIER usuario autenticado
                // (los 3 roles). No lleva id en la URL, asi que nadie puede
                // consultar ni editar el perfil de otra persona.
                .requestMatchers("/perfil/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Escribe un mapa como JSON en la respuesta de seguridad. */
    private static void responseBodyWriter(jakarta.servlet.http.HttpServletResponse respuesta,
                                           java.util.Map<String, Object> cuerpo) throws java.io.IOException {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        // Las fechas LocalDateTime deben serializarse igual que en el resto de la API.
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        respuesta.getWriter().write(mapper.writeValueAsString(cuerpo));
    }
}