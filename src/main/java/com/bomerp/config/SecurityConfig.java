package com.bomerp.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seguridad con Keycloak (S10b): el backend es un Resource Server que solo valida el JWT
 * que emite Keycloak (firma RS256 + emisor, por issuer-uri) y autoriza por rol.
 * Nadie inicia sesión contra el backend: no hay usuarios, contraseñas ni PasswordEncoder aquí.
 *
 * Roles reales de JD Refrigeración (realm "bomerp"):
 *  - ADMIN (Lisandro): todo.
 *  - ASISTENTE (Lucy): registra y consulta; no anula ventas, no modifica el catálogo, no elimina clientes/proveedores.
 */
@Configuration
public class SecurityConfig {

    /** Orígenes permitidos por CORS, separados por coma (app.cors.allowed-origins). */
    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    /**
     * Lee los roles de realm_access.roles (claim de Keycloak) como ROLE_ADMIN / ROLE_ASISTENTE.
     * Keycloak agrega además roles propios (offline_access, uma_authorization...): no estorban.
     * El "nombre" de la autenticación es preferred_username (lisandro, lucy) en vez del UUID.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Object roles = realmAccess == null ? null : realmAccess.get("roles");
            if (!(roles instanceof Collection<?> lista)) {
                return List.<GrantedAuthority>of();
            }
            return lista.stream()
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();
        });
        converter.setPrincipalClaimName("preferred_username");
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origenes = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty())
                .toList();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origenes);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.addAllowedHeader("*");
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
                                           ObjectMapper objectMapper) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())   // API stateless con Bearer: no hay cookie de sesión que proteger
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Abierto: pre-flight de CORS, catálogo público, documentación
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/publico/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/images/**", "/error").permitAll()

                // Catálogo (módulo no transaccional): consultar = cualquier usuario; modificar = ADMIN
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasRole("ADMIN")

                // Operaciones que solo decide el dueño (auditoría C2)
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/ventas/*/anular").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/clientes/**", "/api/v1/admin/proveedores/**").hasRole("ADMIN")
                // Devolver dinero de una venta anulada lo registra quien anula (auditoría A8)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/cobranza/devoluciones/**").hasRole("ADMIN")

                // Resto del panel: cualquiera de los dos roles del negocio
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "ASISTENTE")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                // Mismo formato JSON que GlobalExceptionHandler, para que la SPA muestre el motivo
                .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper, HttpStatus.UNAUTHORIZED,
                        "Falta el token o no es válido (inicie sesión de nuevo)."))
                .accessDeniedHandler((req, res, ex) -> escribir(res, objectMapper, HttpStatus.FORBIDDEN,
                        "Su usuario no tiene permiso para esta operación.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> escribir(res, objectMapper, HttpStatus.UNAUTHORIZED,
                        "Falta el token o no es válido (inicie sesión de nuevo)."))
                .accessDeniedHandler((req, res, ex) -> escribir(res, objectMapper, HttpStatus.FORBIDDEN,
                        "Su usuario no tiene permiso para esta operación.")));
        return http.build();
    }

    private static void escribir(HttpServletResponse res, ObjectMapper objectMapper, HttpStatus status,
                                 String mensaje) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", LocalDateTime.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("message", mensaje);
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(), cuerpo);
    }
}
