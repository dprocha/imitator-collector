package io.diegorocha.imitator.model.output;

/**
 * Aggregated statistics for an entire cluster, computed by summing the {@link DbStats} of
 * every database in the cluster.
 * <p>Size fields are expressed in raw bytes ({@code B}) and in rounded decimal multiples
 * of 1&thinsp;000 ({@code KB}, {@code MB}, {@code GB}, {@code TB}), matching cloud storage
 * billing conventions (1 GB = 1,000,000,000 bytes).</p>
 *
 * @param totalDatabases   number of databases collected
 * @param totalCollections total collections across all databases
 * @param totalDocuments   total document count
 * @param totalIndex       total index count
 * @param totalDataSizeB   total data size in bytes
 * @param totalDataSizeKB  total data size in kilobytes (÷ 1,000), 2 decimal places
 * @param totalDataSizeMB  total data size in megabytes (÷ 1,000,000), 2 decimal places
 * @param totalDataSizeGB  total data size in gigabytes (÷ 1,000,000,000), 2 decimal places
 * @param totalDataSizeTB  total data size in terabytes (÷ 1,000,000,000,000), 2 decimal places
 * @param totalIndexSizeB  total index size in bytes
 * @param totalIndexSizeKB total index size in kilobytes, 2 decimal places
 * @param totalIndexSizeMB total index size in megabytes, 2 decimal places
 * @param totalIndexSizeGB total index size in gigabytes, 2 decimal places
 * @param totalIndexSizeTB total index size in terabytes, 2 decimal places
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record ClusterStats(
        int totalDatabases,
        int totalCollections,
        long totalDocuments,
        int totalIndex,
        long totalDataSizeB,
        double totalDataSizeKB,
        double totalDataSizeMB,
        double totalDataSizeGB,
        double totalDataSizeTB,
        long totalIndexSizeB,
        double totalIndexSizeKB,
        double totalIndexSizeMB,
        double totalIndexSizeGB,
        double totalIndexSizeTB
) {
}
