package com.wattwise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI/Swagger. Añade el esquema de seguridad
 * "bearerAuth" para que los endpoints autenticados puedan probarse directamente
 * desde Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI wattwiseOpenApi() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("WattWise API")
                .description("Personal electricity price optimizer for the Spanish PVPC market. "
                        + "Consumes REE/ESIOS price data, classifies slots with a traffic-light "
                        + "methodology and produces appliance recommendations + alerts.")
                .version("v0.1.0")
                .contact(new Contact().name("WattWise Team")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new Components().addSecuritySchemes(schemeName,
                new SecurityScheme()
                    .name(schemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}