package com.bomerp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Candado "Authorize" de Swagger UI: se pega el access_token de Keycloak y cada prueba lo envía. */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    OpenAPI sgeJdOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SGE-JD / BomERP API")
                        .version("v1")
                        .description("Backend de JD Refrigeración S.A.C. Tokens emitidos por Keycloak (realm bomerp)."))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
