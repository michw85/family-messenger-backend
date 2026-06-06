package com.familymessenger.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация OpenAPI (Swagger) для документации REST API
 * OpenAPI (Swagger) configuration for REST API documentation
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Family Messenger API")
                        .version("1.0")
                        .description("API для семейного мессенджера / API for family messenger")
                        .contact(new Contact()
                                .name("Family Messenger Team")
                                .email("support@familymessenger.com")));
    }
}