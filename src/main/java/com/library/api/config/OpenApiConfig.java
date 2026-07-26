package com.library.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI libraryOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Library API")
                .version("v1")
                .description("""
                        A RESTful API for a simple library system: register borrowers, register book
                        copies, list the catalogue, and lend copies out one borrower at a time.
                        """)
                .license(new License().name("MIT")));
    }
}
