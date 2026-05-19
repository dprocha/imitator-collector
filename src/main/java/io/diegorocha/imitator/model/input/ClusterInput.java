package io.diegorocha.imitator.model.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Input descriptor for a single MongoDB-compatible cluster.
 * <p>{@code databases} may be {@code null} or empty to collect all databases, excluding
 * internal ones such as {@code admin}, {@code local}, and {@code config}.</p>
 *
 * @param name             display name used in the output report
 * @param connectionString full MongoDB URI including credentials ({@code mongodb://} or
 *                         {@code mongodb+srv://})
 * @param databases        specific databases to collect; {@code null} or empty to collect all
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Schema(description = "Descriptor for a single MongoDB-compatible cluster")
public record ClusterInput(
        @Schema(description = "Display name used in the output report", example = "my-cluster", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Full MongoDB URI including credentials (mongodb:// or mongodb+srv://)",
                example = "mongodb://user:pass@host:27017",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String connectionString,

        @Schema(description = "Specific databases to collect. Omit or set to null to collect all databases " +
                "(admin, local and config are always skipped)")
        List<DatabaseInput> databases
) {
}