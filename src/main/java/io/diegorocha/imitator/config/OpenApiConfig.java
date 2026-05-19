package io.diegorocha.imitator.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Registers global OpenAPI metadata consumed by springdoc-openapi to populate the Swagger UI.
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@OpenAPIDefinition(
        info = @Info(
                title = "imitator-collector",
                version = "1.0.0",
                description = "REST API for collecting sizing statistics and JSON Schemas from " +
                        "MongoDB-compatible clusters (Atlas, CosmosDB, DocumentDB) to support " +
                        "MongoDB Atlas migration planning."
        )
)
@Configuration
public class OpenApiConfig {
}
