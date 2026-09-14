package com.jdrefrigeracion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Habilitamos acceso REST simple
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/publico/**", "/h2-console/**").permitAll() // Catálogo y H2 libres
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // Documentación Swagger libre
                .requestMatchers("/api/admin/**").authenticated() // El panel requiere login
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults()) // Usaremos HTTP Basic Auth
            .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin())); // Para H2 Console
        return http.build();
    }
}
