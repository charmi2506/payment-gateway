package com.charmi.paymentgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Production-Ready Payment Gateway API")
                        .version("1.0.0")
                        .description("REST API for Payment Gateway with Async Processing, Idempotency, and Webhook support.")
                        .contact(new Contact()
                                .name("Payment Gateway Team")
                                .email("support@paymentgateway.local"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
