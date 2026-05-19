package io.diegorocha.imitator.model.output;

import java.util.List;

/**
 * Sizing report for a single cluster, produced by
 * {@link io.diegorocha.imitator.collector.ClusterCollector}.
 *
 * @param name         cluster display name from the input
 * @param version      detected MongoDB server version (e.g. {@code "7.0.15"})
 * @param estimated    {@code true} if any collection in the cluster used the sampling fallback
 * @param clusterStats rolled-up totals for the entire cluster
 * @param databases    one entry per collected database
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record ClusterOutput(
        String name,
        String version,
        Boolean estimated,
        ClusterStats clusterStats,
        List<DatabaseOutput> databases
) {
}
