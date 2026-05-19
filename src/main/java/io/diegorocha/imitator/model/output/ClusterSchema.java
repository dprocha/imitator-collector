package io.diegorocha.imitator.model.output;

import java.util.List;

/**
 * Schema extraction report for a single cluster.
 *
 * @param name      cluster display name from the input
 * @param version   detected MongoDB server version (e.g. {@code "7.0.15"})
 * @param databases one entry per collected database
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record ClusterSchema(String name, String version, List<DatabaseSchema> databases) {
}
