package io.diegorocha.imitator.model.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Input descriptor for a single database within a cluster.
 * <p>{@code collections} may be {@code null} or empty to collect all collections in the
 * database, excluding {@code system.*} collections.</p>
 *
 * @param name        database name
 * @param collections specific collection names to collect; {@code null} or empty to collect all
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Schema(description = "Descriptor for a single database within a cluster")
public record DatabaseInput(
        @Schema(description = "Database name", example = "mydb", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Specific collection names to collect. Omit or set to null to collect all collections")
        List<String> collections
) {
}