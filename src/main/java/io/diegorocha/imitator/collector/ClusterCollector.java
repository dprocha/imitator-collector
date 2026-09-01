package io.diegorocha.imitator.collector;

import com.mongodb.client.MongoClient;
import io.diegorocha.imitator.config.CollectorProperties;
import io.diegorocha.imitator.exception.CollectorException;
import io.diegorocha.imitator.model.input.ClusterInput;
import io.diegorocha.imitator.model.input.DatabaseInput;
import io.diegorocha.imitator.model.output.ClusterOutput;
import io.diegorocha.imitator.model.output.ClusterStats;
import io.diegorocha.imitator.model.output.DatabaseOutput;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Collects sizing statistics for a single MongoDB-compatible cluster.
 * <p>Opens one {@link com.mongodb.client.MongoClient} per request (closed via
 * try-with-resources), detects the server version via {@code buildInfo}, enumerates
 * databases, and dispatches each database to {@link DatabaseCollector} on a bounded thread pool
 * for concurrent collection. Internal databases ({@code admin}, {@code local}, {@code config})
 * are always excluded.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class ClusterCollector {

    private static final Logger log = LoggerFactory.getLogger(ClusterCollector.class);

    private final MongoConnectionFactory connectionFactory;
    private final DatabaseCollector databaseCollector;
    private final Set<String> internalDatabases;
    private final int threadPoolSize;

    public record ServerVersion(String version, long major, long minor) {}

    public ClusterCollector(MongoConnectionFactory connectionFactory, DatabaseCollector databaseCollector,
                            CollectorProperties properties) {
        this.connectionFactory = connectionFactory;
        this.databaseCollector = databaseCollector;
        this.internalDatabases = properties.internalDatabasesAsSet();
        this.threadPoolSize = properties.concurrency().threadPoolSize();
    }

    public ClusterOutput collect(ClusterInput clusterInput) throws CollectorException {
        try (MongoClient client = connectionFactory.create(clusterInput)) {
            ServerVersion serverVersion = getServerVersion(client);
            log.info("Connected to cluster '{}' — server version: {}", clusterInput.name(), serverVersion.version());

            List<String> databaseNames = getDatabaseNames(client, clusterInput);
            log.debug("Cluster '{}' — databases to collect: {}", clusterInput.name(), databaseNames);
            // M5: databases are collected concurrently on a bounded thread pool
            List<DatabaseOutput> databases = collectDatabasesConcurrently(client, clusterInput, databaseNames, serverVersion);

            ClusterStats clusterStats = buildClusterStats(databases);
            boolean estimated = databases.stream()
                    .flatMap(db -> db.collections().stream())
                    .anyMatch(c -> c.collStats().estimated());
            log.info("Cluster '{}' complete — {} databases, {} collections, {} docs, {} bytes data, {} bytes indexes",
                    clusterInput.name(),
                    clusterStats.totalDatabases(),
                    clusterStats.totalCollections(),
                    clusterStats.totalDocuments(),
                    clusterStats.totalDataSizeB(),
                    clusterStats.totalIndexSizeB());
            return new ClusterOutput(clusterInput.name(), serverVersion.version(), estimated, clusterStats, databases);

        } catch (Exception e) {
            // H3: use a sanitized message — e.getMessage() may contain the connection string with credentials
            throw new CollectorException("Collection failed for cluster: " + clusterInput.name(), e);
        }
    }

    // M5: databases are collected on a bounded thread pool; failures are logged and skipped
    private List<DatabaseOutput> collectDatabasesConcurrently(MongoClient client, ClusterInput clusterInput,
                                                               List<String> databaseNames,
                                                               ServerVersion serverVersion) {
        List<DatabaseOutput> databases = new ArrayList<>();
        // L2: ExecutorService only implements AutoCloseable since Java 19 — Java 17 needs
        // an explicit shutdown() in a finally block instead of try-with-resources.
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            List<Future<DatabaseOutput>> futures = databaseNames.stream()
                    .map(name -> executor.submit(() -> {
                        log.info("Collecting database '{}' in cluster '{}'", name, clusterInput.name());
                        try {
                            return databaseCollector.collect(
                                    client.getDatabase(name),
                                    getDatabaseInput(clusterInput, name),
                                    serverVersion);
                        } catch (Exception e) {
                            log.error("Failed to collect database '{}' in cluster '{}'",
                                    name, clusterInput.name(), e);
                            throw e;
                        }
                    }))
                    .toList();

            for (Future<DatabaseOutput> future : futures) {
                try {
                    databases.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Collection interrupted for cluster '{}'", clusterInput.name());
                    break;
                } catch (ExecutionException e) {
                    // already logged inside the task with the database name
                }
            }
        } finally {
            executor.shutdown();
        }
        return databases;
    }

    private ClusterStats buildClusterStats(List<DatabaseOutput> databases) {
        int totalCollections = databases.stream().mapToInt(d -> d.dbStats().totalCollections()).sum();
        long totalDocuments = databases.stream().mapToLong(d -> d.dbStats().totalDocuments()).sum();
        int totalIndex = databases.stream().mapToInt(d -> d.dbStats().totalIndex()).sum();
        long totalDataSizeB = databases.stream().mapToLong(d -> d.dbStats().totalDataSize()).sum();
        long totalIndexSizeB = databases.stream().mapToLong(d -> d.dbStats().totalIndexSize()).sum();

        return new ClusterStats(
                databases.size(),
                totalCollections,
                totalDocuments,
                totalIndex,
                totalDataSizeB,
                round2(totalDataSizeB / 1_000.0),
                round2(totalDataSizeB / 1_000_000.0),
                round2(totalDataSizeB / 1_000_000_000.0),
                round2(totalDataSizeB / 1_000_000_000_000.0),
                totalIndexSizeB,
                round2(totalIndexSizeB / 1_000.0),
                round2(totalIndexSizeB / 1_000_000.0),
                round2(totalIndexSizeB / 1_000_000_000.0),
                round2(totalIndexSizeB / 1_000_000_000_000.0)
        );
    }

    private ServerVersion getServerVersion(MongoClient client) {
        Document buildInfo = client.getDatabase("admin").runCommand(new Document("buildInfo", 1));
        String version = buildInfo.getString("version");
        return new ServerVersion(
                version,
                Long.parseLong(version.split("\\.")[0]),
                Long.parseLong(version.split("\\.")[1])
        );
    }

    private List<String> getDatabaseNames(MongoClient client, ClusterInput clusterInput) {
        List<String> databases = new ArrayList<>();
        if (clusterInput.databases() != null && !clusterInput.databases().isEmpty()) {
            databases.addAll(clusterInput.databases().stream().map(DatabaseInput::name).toList());
            log.debug("Cluster '{}' — using explicit database list: {}", clusterInput.name(), databases);
        } else {
            for (String database : client.listDatabaseNames()) {
                databases.add(database);
            }
            log.debug("Cluster '{}' — {} database(s) discovered: {}", clusterInput.name(), databases.size(), databases);
        }
        databases.removeIf(internalDatabases::contains);
        log.debug("Cluster '{}' — {} database(s) to collect after filtering internals: {}",
                clusterInput.name(), databases.size(), databases);
        return databases;
    }

    private DatabaseInput getDatabaseInput(ClusterInput clusterInput, String databaseName) {
        if (clusterInput.databases() == null || clusterInput.databases().isEmpty()) {
            return new DatabaseInput(databaseName, List.of());
        }
        return clusterInput.databases().stream()
                .filter(db -> db.name().equals(databaseName))
                .findFirst()
                .orElse(new DatabaseInput(databaseName, List.of()));
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
