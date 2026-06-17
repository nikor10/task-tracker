package com.nikola.task_tracker_project_spring.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "basicAuth";

        return new OpenAPI()
                // 0. Describe the API itself (shown at the top of the Swagger UI page)
                .info(new Info()
                        .title("Task Tracker API")
                        .version("v1")
                        .description("""
                                REST API for managing projects, their tasks, and users.

                                **Authorization:** every `/api/**` endpoint requires HTTP Basic \
                                authentication. Regular users see only the projects they own or are \
                                assigned a task in; the admin account sees everything. Click \
                                **Authorize** and enter your username/password to try the endpoints.

                                Task edits are captured in an append-only activity log (audit trail) \
                                exposed under `/api/tasks/{id}/activity`."""))
                // 1. Apply the security scheme globally to all API endpoints
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                // 2. Define the configuration for Basic Authentication
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                        )
                );
    }
}