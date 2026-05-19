package io.diegorocha.imitator.model.output;

/**
 * Aggregated statistics for a single database, computed by summing the {@link CollStats}
 * of every collection in the database. All size fields are in raw bytes.
 *
 * @param totalCollections number of collections in the database
 * @param totalDocuments   total document count across all collections
 * @param totalIndex       total index count across all collections
 * @param totalDataSize    total uncompressed data size in bytes
 * @param totalIndexSize   sum of all index sizes in bytes
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record DbStats(
        int totalCollections,
        long totalDocuments,
        int totalIndex,
        long totalDataSize,
        long totalIndexSize
) {
}
