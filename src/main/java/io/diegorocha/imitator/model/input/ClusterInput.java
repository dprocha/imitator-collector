package io.diegorocha.imitator.model.input;

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
public record ClusterInput(String name, String connectionString, List<DatabaseInput> databases) {

}