package com.bomerp.config;

import com.bomerp.auditoria.AuditoriaFilter;
import com.bomerp.auditoria.service.AuditoriaService;
import com.bomerp.config.permisos.Permiso;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
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

    /** Roles que habilitan una operación del dueño: ADMIN o el permiso delegado (sin el prefijo ROLE_). */
    private static String[] conPermiso(Permiso permiso) {
        return new String[]{"ADMIN", permiso.rol()};
    }

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
                                           ObjectMapper objectMapper, AuditoriaService auditoriaService) throws Exception {
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

                // Solo el dueño: quién puede qué (permisos) y el historial de cambios. No se delega.
                .requestMatchers("/api/v1/admin/permisos/**", "/api/v1/admin/auditoria/**").hasRole("ADMIN")

                // Operaciones del dueño (auditoría C2). Cada una acepta además su permiso delegable
                // (Permiso.rol(), p. ej. PERM_ANULAR_VENTAS): Lisandro puede dárselo a un asistente sin hacerlo ADMIN.
                // Catálogo (módulo no transaccional): consultar = cualquier usuario; modificar = ADMIN o PERM_CATALOGO
                .requestMatchers(HttpMethod.GET, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasAnyRole(conPermiso(Permiso.CATALOGO))
                .requestMatchers(HttpMethod.PUT, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasAnyRole(conPermiso(Permiso.CATALOGO))
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/productos/**", "/api/v1/admin/categorias/**").hasAnyRole(conPermiso(Permiso.CATALOGO))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/ventas/*/anular").hasAnyRole(conPermiso(Permiso.ANULAR_VENTAS))
                .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/clientes/**", "/api/v1/admin/proveedores/**").hasAnyRole(conPermiso(Permiso.ELIMINAR))
                // Devolver dinero de una venta anulada (auditoría A8)
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/cobranza/devoluciones/**").hasAnyRole(conPermiso(Permiso.DEVOLUCIONES))
                // Anular un cobro mal registrado reabre el saldo de sus ventas
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/cobranza/cobros/*/anular").hasAnyRole(conPermiso(Permiso.ANULAR_COBROS))
                // Servicios: contratar o cancelar un contrato, cancelar una orden y dar de baja un equipo
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/contratos", "/api/v1/admin/contratos/*/cancelar",
                        "/api/v1/admin/ordenes/*/cancelar", "/api/v1/admin/equipos/*/retirar").hasAnyRole(conPermiso(Permiso.SERVICIOS))

                // Anular un pago a proveedor: mismo permiso que anular un cobro
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/cuentas-por-pagar/pagos/*/anular").hasAnyRole(conPermiso(Permiso.ANULAR_COBROS))
                // Ajustes de inventario: consultar cualquiera; registrar ADMIN o AJUSTAR_INVENTARIO
                .requestMatchers(HttpMethod.POST, "/api/v1/admin/inventario/ajustes/**").hasAnyRole(conPermiso(Permiso.AJUSTAR_INVENTARIO))
                // Costos y márgenes: ADMIN o VER_RENTABILIDAD
                .requestMatchers("/api/v1/admin/reportes/rentabilidad/**").hasAnyRole(conPermiso(Permiso.VER_RENTABILIDAD))

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
            // Historial de cambios: después de leer el token (conoce al usuario) y antes de autorizar (ve los 403)
            .addFilterAfter(new AuditoriaFilter(auditoriaService, objectMapper), BearerTokenAuthenticationFilter.class)
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
