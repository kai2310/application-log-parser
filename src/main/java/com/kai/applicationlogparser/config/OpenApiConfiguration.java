package com.kai.applicationlogparser.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Application Log Parser API",
                version = "v1",
                description = "API for parsing application logs and generating issue reports.",
                contact = @Contact(name = "Application Log Parser Team"),
                license = @License(name = "Apache License 2.0")
        ),
        servers = {
                @Server(url = "/", description = "Default server")
        }
)
public class OpenApiConfiguration {
}
