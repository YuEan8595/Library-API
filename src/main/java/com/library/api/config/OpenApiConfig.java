package com.library.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String OAUTH2_SCHEME = "library-oauth2";

    @Bean
    public OpenAPI libraryOpenApi() {
        // client_credentials only: it needs no browser redirect, so it works straight from
        // the Swagger UI "Authorize" button. Testing the member authorization_code+PKCE flow
        // is documented in the README for Postman/a browser instead - see ASSUMPTIONS.md.
        SecurityScheme oauth2Scheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("Staff login (client_credentials). Client id: library-staff")
                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                        .tokenUrl("/oauth2/token")
                        .scopes(new Scopes().addString("library.api", "Access the library API"))));

        return new OpenAPI()
                .info(new Info()
                        .title("Library API")
                        .version("v1")
                        .description("""
                                A RESTful API for a simple library system: register borrowers, register book
                                copies, list the catalogue, and lend copies out one borrower at a time.
                                """)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(OAUTH2_SCHEME, oauth2Scheme))
                .addSecurityItem(new SecurityRequirement().addList(OAUTH2_SCHEME));
    }
}
