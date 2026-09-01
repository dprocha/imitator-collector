package io.diegorocha.imitator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Externalized configuration for the collector, bound from {@code application.properties}
 * under the {@code collector.*} prefix via {@code @ConfigurationProperties}.
 * <p>All fields have safe defaults — the application starts without any {@code collector.*}
 * entries in {@code application.properties}. Nested records group related properties:
 * {@link Mongo} for timeouts, {@link Sampling} for sample size, and {@link CosmosDb} for
 * CosmosDB/DocumentDB compatibility filters.</p>
 *
 * @param mongo             MongoDB connection timeout settings
 * @param sampling          sampling-based estimation settings
 * @param cosmosdb          CosmosDB-specific compatibility filters
 * @param concurrency       thread pool sizing for concurrent database/collection collection
 * @param internalDatabases databases always excluded from collection regardless of input
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "collector")
public record CollectorProperties(Mongo mongo, Sampling sampling, CosmosDb cosmosdb, Concurrency concurrency,
                                   List<String> internalDatabases) {

    public CollectorProperties {
        mongo = mongo != null ? mongo : new Mongo(10, 60, 10);
        sampling = sampling != null ? sampling : new Sampling(50);
        cosmosdb = cosmosdb != null ? cosmosdb : new CosmosDb(
                List.of("DocumentDBDefaultIndex_1"),
                List.of("lection"));
        concurrency = concurrency != null ? concurrency : new Concurrency(20);
        internalDatabases = internalDatabases != null
                ? List.copyOf(internalDatabases)
                : List.of("admin", "local", "config");
    }

    public Set<String> internalDatabasesAsSet() {
        return Set.copyOf(internalDatabases);
    }

    /** MongoDB connection timeouts (seconds). */
    public record Mongo(int connectTimeoutSeconds, int readTimeoutSeconds, int serverSelectionTimeoutSeconds) {
        public Mongo {
            connectTimeoutSeconds = connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 10;
            readTimeoutSeconds = readTimeoutSeconds > 0 ? readTimeoutSeconds : 60;
            serverSelectionTimeoutSeconds = serverSelectionTimeoutSeconds > 0 ? serverSelectionTimeoutSeconds : 10;
        }
    }

    /** Sampling-based stats estimation. */
    public record Sampling(int sampleSize) {
        public Sampling {
            sampleSize = sampleSize > 0 ? sampleSize : 50;
        }
    }

    /**
     * Thread pool sizing for concurrent database/collection collection.
     * <p>Java 17 has no virtual threads, so each database (and, within it, each collection)
     * is collected on a bounded {@link java.util.concurrent.ExecutorService} instead of one
     * thread per task. {@code threadPoolSize} caps how many databases/collections are collected
     * concurrently per cluster request; excess tasks queue rather than spawning new threads.</p>
     */
    public record Concurrency(int threadPoolSize) {
        public Concurrency {
            threadPoolSize = threadPoolSize > 0 ? threadPoolSize : 20;
        }
    }

    /** CosmosDB-specific compatibility filters. */
    public record CosmosDb(List<String> systemIndexes, List<String> phantomCollections) {
        public CosmosDb {
            systemIndexes = systemIndexes != null ? List.copyOf(systemIndexes) : List.of("DocumentDBDefaultIndex_1");
            phantomCollections = phantomCollections != null ? List.copyOf(phantomCollections) : List.of("lection");
        }

        public Set<String> systemIndexesAsSet() {
            return Set.copyOf(systemIndexes);
        }

        public Set<String> phantomCollectionsAsSet() {
            return Set.copyOf(phantomCollections);
        }
    }
}
