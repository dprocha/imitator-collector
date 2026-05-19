package io.diegorocha.imitator.model.output;

import java.util.List;

/**
 * Per-collection statistics as reported by native commands or estimated via BSON sampling.
 *
 * @param count          number of documents in the collection
 * @param avgObjSize     average document size in bytes ({@code 0} for empty collections)
 * @param dataSize       total uncompressed data size in bytes
 * @param totalIndex     number of indexes on the collection
 * @param totalIndexSize sum of all index sizes in bytes
 * @param indexes        index definitions with per-index sizes
 * @param estimated      {@code true} if stats were estimated via BSON sampling rather than
 *                       native {@code collStats} or {@code $collStats} commands
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record CollStats(
        long count,
        long avgObjSize,
        long dataSize,
        int totalIndex,
        long totalIndexSize,
        List<IndexOutput> indexes,
        boolean estimated
) {
    public static CollStats empty() {
        return new CollStats(0, 0, 0, 0, 0, List.of(), false);
    }
}
