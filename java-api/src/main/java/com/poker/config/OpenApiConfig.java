package com.poker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI 3.1 spec served at {@code /v3/api-docs}.
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html}.  The spec is
 * public and includes a Bearer token security scheme so API calls can be
 * tested directly from the browser after logging in via {@code POST /auth/login}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pokerEngineOpenApi() {
        final String bearerSchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("Poker Engine API")
                .description("""
                    Server-authoritative No-Limit Texas Hold'em practice platform.

                    **Quick start**
                    1. `POST /auth/register` to create an account and receive a JWT.
                    2. Click **Authorize** and paste the token to unlock authenticated endpoints.
                    3. Create a table, seat 2–6 players, and start a hand.
                    4. After every action the response includes live coaching feedback
                       (equity, pot odds, recommended action, explanation).
                    """)
                .version("1.0.0"))
            // Declare a global Bearer security scheme so every locked endpoint
            // shows a padlock icon and the "Authorize" button appears at the top.
            .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
            .components(new Components()
                .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                    .name(bearerSchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the token returned by POST /auth/login")));
    }
}
