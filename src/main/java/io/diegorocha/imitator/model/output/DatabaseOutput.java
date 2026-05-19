package io.diegorocha.imitator.model.output;

import java.util.List;

/**
 * Sizing report for a single database.
 *
 * @param name        database name
 * @param dbStats     rolled-up totals computed from the collection-level stats
 * @param collections one entry per collected collection
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record DatabaseOutput(
        String name,
        DbStats dbStats,
        List<CollectionOutput> collections
) {
}
