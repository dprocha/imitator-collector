package io.diegorocha.imitator.model.input;

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
public record DatabaseInput(String name, List<String> collections) {
}