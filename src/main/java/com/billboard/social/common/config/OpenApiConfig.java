package com.billboard.social.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Social Service API",
                version = "1.0.0",
                description = "RESTful API for social features including follows, blocks, and user relationships",
                contact = @Contact(
                        name = "Billboard Team",
                        email = "support@billboard.com",
                        url = "https://billboard.com"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8082", description = "Local Development Server"),
                @Server(url = "https://api.billboard.com/social", description = "Production Server")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT Authentication. Get token from POST /api/v1/auth/login on identity-service",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenApiConfig {
}