package io.diegorocha.imitator.collector;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.diegorocha.imitator.config.CollectorProperties;
import io.diegorocha.imitator.exception.CollectorException;
import io.diegorocha.imitator.model.input.ClusterInput;
import io.diegorocha.imitator.model.input.DatabaseInput;
import io.diegorocha.imitator.model.output.ClusterSchema;
import io.diegorocha.imitator.model.output.CollectionSchema;
import io.diegorocha.imitator.model.output.DatabaseSchema;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Collects JSON schemas for all collections in a single MongoDB-compatible cluster.
 * <p>Opens one {@link com.mongodb.client.MongoClient} per request, enumerates databases,
 * and dispatches each database to {@link CollectionSchemaCollector} on a bounded thread pool
 * for concurrent schema extraction. Internal databases and CosmosDB phantom collections are
 * filtered out before dispatch.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class ClusterSchemaCollector {

    private static final Logger log = LoggerFactory.getLogger(ClusterSchemaCollector.class);

    private final MongoConnectionFactory connectionFactory;
    private final CollectionSchemaCollector collectionSchemaCollector;
    private final Set<String> internalDatabases;
    private final Set<String> phantomCollections;
    private final int threadPoolSize;

    public ClusterSchemaCollector(MongoConnectionFactory connectionFactory,
                                  CollectionSchemaCollector collectionSchemaCollector,
                                  CollectorProperties properties) {
        this.connectionFactory = connectionFactory;
        this.collectionSchemaCollector = collectionSchemaCollector;
        this.internalDatabases = properties.internalDatabasesAsSet();
        this.phantomCollections = properties.cosmosdb().phantomCollectionsAsSet();
        this.threadPoolSize = properties.concurrency().threadPoolSize();
    }

    public ClusterSchema collect(ClusterInput clusterInput) throws CollectorException {
        try (MongoClient client = connectionFactory.create(clusterInput)) {
            String version = getServerVersion(client);
            log.info("Schema collection started on cluster '{}' — server version: {}",
                    clusterInput.name(), version);
            List<DatabaseSchema> databases = collectDatabasesConcurrently(client, clusterInput);
            int totalCollections = databases.stream().mapToInt(d -> d.collections().size()).sum();
            log.info("Schema collection complete for cluster '{}' — {} databases, {} collections",
                    clusterInput.name(), databases.size(), totalCollections);
            return new ClusterSchema(clusterInput.name(), version, databases);
        } catch (Exception e) {
            throw new CollectorException("Schema collection failed for cluster: " + clusterInput.name(), e);
        }
    }

    private List<DatabaseSchema> collectDatabasesConcurrently(MongoClient client, ClusterInput clusterInput) {
        List<String> databaseNames = getDatabaseNames(client, clusterInput);
        log.debug("Cluster '{}' — {} database(s) to extract schema: {}",
                clusterInput.name(), databaseNames.size(), databaseNames);
        List<DatabaseSchema> databases = new ArrayList<>();
        // L2: ExecutorService only implements AutoCloseable since Java 19 — Java 17 needs
        // an explicit shutdown() in a finally block instead of try-with-resources.
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            List<Future<DatabaseSchema>> futures = databaseNames.stream()
                    .map(name -> executor.submit(() -> {
                        log.info("Extracting schema for database '{}' in cluster '{}'",
                                name, clusterInput.name());
                        try {
                            return collectDatabase(client.getDatabase(name), clusterInput, name);
                        } catch (Exception e) {
                            log.error("Failed to extract schema for database '{}' in cluster '{}'",
                                    name, clusterInput.name(), e);
                            throw e;
                        }
                    }))
                    .toList();
            for (Future<DatabaseSchema> future : futures) {
                try {
                    databases.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Schema extraction interrupted for cluster '{}'", clusterInput.name());
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

    private DatabaseSchema collectDatabase(MongoDatabase db, ClusterInput clusterInput, String databaseName) {
        List<String> collectionNames = getCollectionNames(db, clusterInput, databaseName);
        log.debug("Database '{}' — {} collection(s) to extract schema", databaseName, collectionNames.size());
        List<CollectionSchema> collections = new ArrayList<>();
        // L2: ExecutorService only implements AutoCloseable since Java 19 — Java 17 needs
        // an explicit shutdown() in a finally block instead of try-with-resources.
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        try {
            List<Future<CollectionSchema>> futures = collectionNames.stream()
                    .filter(name -> !name.startsWith("system."))
                    .map(name -> executor.submit(() -> {
                        log.debug("Extracting schema for '{}.{}'", databaseName, name);
                        try {
                            return collectionSchemaCollector.collect(db, name);
                        } catch (Exception e) {
                            log.error("Failed to extract schema for collection '{}.{}'",
                                    databaseName, name, e);
                            throw e;
                        }
                    }))
                    .toList();
            for (Future<CollectionSchema> future : futures) {
                try {
                    collections.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Schema extraction interrupted for database '{}'", databaseName);
                    break;
                } catch (ExecutionException e) {
                    // already logged inside the task with the collection name
                }
            }
        } finally {
            executor.shutdown();
        }
        log.info("Database '{}' schema complete — {} collections", databaseName, collections.size());
        return new DatabaseSchema(databaseName, collections);
    }

    private List<String> getDatabaseNames(MongoClient client, ClusterInput clusterInput) {
        List<String> databases = new ArrayList<>();
        if (clusterInput.databases() != null && !clusterInput.databases().isEmpty()) {
            databases.addAll(clusterInput.databases().stream().map(DatabaseInput::name).toList());
            log.debug("Cluster '{}' — using explicit database list: {}", clusterInput.name(), databases);
        } else {
            for (String name : client.listDatabaseNames()) {
                databases.add(name);
            }
            log.debug("Cluster '{}' — {} database(s) discovered: {}",
                    clusterInput.name(), databases.size(), databases);
        }
        databases.removeIf(internalDatabases::contains);
        log.debug("Cluster '{}' — {} database(s) to collect after filtering internals: {}",
                clusterInput.name(), databases.size(), databases);
        return databases;
    }

    private List<String> getCollectionNames(MongoDatabase db, ClusterInput clusterInput, String databaseName) {
        if (clusterInput.databases() != null) {
            for (DatabaseInput dbInput : clusterInput.databases()) {
                if (dbInput.name().equals(databaseName)
                        && dbInput.collections() != null
                        && !dbInput.collections().isEmpty()) {
                    log.debug("Database '{}' — using explicit collection list: {}",
                            databaseName, dbInput.collections());
                    return dbInput.collections();
                }
            }
        }

        List<String> names = new ArrayList<>();
        try {
            Document cmdResult = db.runCommand(new Document("listCollections", 1).append("nameOnly", true));
            for (Document doc : CursorHelper.exhaustCursor(db, cmdResult, log)) {
                String name = doc.getString("name");
                String type = doc.getString("type");
                if (name == null || name.isBlank()) {
                    log.warn("Database '{}' — skipping listCollections entry with null/blank name", databaseName);
                    continue;
                }
                if (type != null && !"collection".equals(type)) {
                    log.debug("Database '{}' — skipping '{}' of type '{}' (not a plain collection)",
                            databaseName, name, type);
                    continue;
                }
                if (phantomCollections.contains(name)) {
                    log.warn("Database '{}' — skipping phantom collection '{}'", databaseName, name);
                    continue;
                }
                names.add(name);
            }
        } catch (MongoException e) {
            log.error("Could not list collections in '{}' — database schema will be empty: {}",
                    databaseName, e.getMessage(), e);
        }
        log.debug("Database '{}' — {} collection(s) discovered", databaseName, names.size());
        return names;
    }

    private String getServerVersion(MongoClient client) {
        Document buildInfo = client.getDatabase("admin").runCommand(new Document("buildInfo", 1));
        return buildInfo.getString("version");
    }
}
