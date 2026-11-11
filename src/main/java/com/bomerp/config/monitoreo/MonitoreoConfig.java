package com.bomerp.config.monitoreo;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registra el marcado de conexiones de {@link ModuloOracle} (BD2 S14). */
@Configuration
public class MonitoreoConfig {

    // static: un BeanPostProcessor debe crearse antes que los demás beans (incluido el DataSource)
    @Bean
    static ModuloOracle.Marcador marcadorDeConexiones() {
        return new ModuloOracle.Marcador();
    }

    // Primero de todos: antes que la cadena de Spring Security, que también consulta la base
    @Bean
    FilterRegistrationBean<ModuloOracle.Filtro> filtroModuloOracle() {
        FilterRegistrationBean<ModuloOracle.Filtro> registro = new FilterRegistrationBean<>(new ModuloOracle.Filtro());
        registro.addUrlPatterns("/api/*");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}
