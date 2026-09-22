package com.tourinvest.backend.security;

import java.util.List;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                // Orígenes permitidos: VS Code "Live Server" (:5500) y el servidor estático de desarrollo (:8081).
        // Si sirves el frontend en otro puerto, agrégalo aquí o pasa -Dapp.cors.allowed-origins=...
        configuracion.setAllowedOrigins(List.of(
            "http://localhost:5500", "http://127.0.0.1:5500",
            "http://localhost:8081", "http://127.0.0.1:8081"
        ));
        // PATCH es requerido por /alertas/{id}/cancelar, /admin/usuarios/{id}/{suspender|activar} y /admin/**.
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("*"));
        configuracion.setAllowCredentials(true);

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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/error").permitAll()   // deja ver los errores del servidor tal cual (no como 403 opaco)
                // Gestión de Empresas (CRUD) reservada al Administrador; la consulta queda para cualquier autenticado
                .requestMatchers(HttpMethod.POST, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.PUT, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers(HttpMethod.DELETE, "/empresas/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                // GA7-220501096-AA3-EV01: módulo de Gestión de Usuarios (CRUD completo) — solo administrador
                .requestMatchers("/api/usuarios/**").hasRole("ADMINISTRADOR")
                // Paso 6 de la guía: CRUD de usuarios con JDBC puro (UsuarioDAO) — solo administrador
                .requestMatchers("/dao/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/analista/**").hasRole("ANALISTA")
                .requestMatchers("/inversionista/**").hasRole("INVERSIONISTA")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}