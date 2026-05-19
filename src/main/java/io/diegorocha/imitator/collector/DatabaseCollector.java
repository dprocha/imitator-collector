package io.diegorocha.imitator.collector;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import io.diegorocha.imitator.model.input.DatabaseInput;
import io.diegorocha.imitator.model.output.CollectionOutput;
import io.diegorocha.imitator.model.output.DatabaseOutput;
import io.diegorocha.imitator.model.output.DbStats;
import org.bson.Document;
import io.diegorocha.imitator.config.CollectorProperties;
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
 * Collects sizing statistics for all collections in a single MongoDB database.
 * <p>Enumerates collections via {@code runCommand({listCollections: 1})} and
 * {@link CursorHelper#exhaustCursor}, then dispatches each collection to
 * {@link CollectionCollector} on a virtual thread for concurrent collection. CosmosDB phantom
 * collections and {@code system.*} collections are excluded before dispatch.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class DatabaseCollector {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCollector.class);

    private final CollectionCollector collectionCollector;
    // L1: loaded from CollectorProperties so they can be overridden without a code change
    private final Set<String> phantomCollections;

    public DatabaseCollector(CollectionCollector collectionCollector, CollectorProperties properties) {
        this.collectionCollector = collectionCollector;
        this.phantomCollections = properties.cosmosdb().phantomCollectionsAsSet();
    }

    public DatabaseOutput collect(MongoDatabase db, DatabaseInput databaseInput,
                                  ClusterCollector.ServerVersion serverVersion) {
        List<String> collectionNames = getCollectionNames(db, databaseInput);
        log.debug("Database '{}' — {} collection(s) to collect", db.getName(), collectionNames.size());
        // M5: collections within a database are collected concurrently using virtual threads
        List<CollectionOutput> collections = collectCollectionsConcurrently(db, collectionNames, serverVersion);

        DbStats dbStats = new DbStats(
                collections.size(),
                collections.stream().mapToLong(c -> c.collStats().count()).sum(),
                collections.stream().mapToInt(c -> c.collStats().totalIndex()).sum(),
                collections.stream().mapToLong(c -> c.collStats().dataSize()).sum(),
                collections.stream().mapToLong(c -> c.collStats().totalIndexSize()).sum()
        );
        log.info("Database '{}' complete — {} collections, {} docs, {} bytes data",
                db.getName(), dbStats.totalCollections(), dbStats.totalDocuments(), dbStats.totalDataSize());
        return new DatabaseOutput(db.getName(), dbStats, collections);
    }

    // M5: each collection is collected on its own virtual thread; failures are logged and skipped
    private List<CollectionOutput> collectCollectionsConcurrently(MongoDatabase db, List<String> names,
                                                                   ClusterCollector.ServerVersion serverVersion) {
        List<CollectionOutput> collections = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<CollectionOutput>> futures = names.stream()
                    .filter(name -> !name.startsWith("system."))
                    .map(name -> executor.submit(() -> {
                        log.debug("Collecting collection '{}.{}'", db.getName(), name);
                        try {
                            return collectionCollector.collect(db, name, serverVersion);
                        } catch (Exception e) {
                            log.error("Failed to collect collection '{}.{}'", db.getName(), name, e);
                            throw e;
                        }
                    }))
                    .toList();

            for (Future<CollectionOutput> future : futures) {
                try {
                    collections.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Collection interrupted for database '{}'", db.getName());
                    break;
                } catch (ExecutionException e) {
                    // already logged inside the task with the collection name
                }
            }
        }
        return collections;
    }

    private List<String> getCollectionNames(MongoDatabase db, DatabaseInput config) {
        if (config != null && config.collections() != null && !config.collections().isEmpty()) {
            return config.collections();
        }

        List<String> collectionNames = new ArrayList<>();

        // C1: runCommand() + CursorHelper.exhaustCursor() to fully enumerate databases with
        // more than 100 collections. The driver's db.listCollections() iterable has an internal
        // skip-count bug against CosmosDB 3.2 cursors that throws "Range [X, Y) out of bounds".
        try {
            Document cmdResult = db.runCommand(new Document("listCollections", 1)
                    .append("nameOnly", true));
            for (Document doc : CursorHelper.exhaustCursor(db, cmdResult, log)) {
                String name = doc.getString("name");
                String type = doc.getString("type"); // "collection" or "view"; absent on MongoDB 3.2

                if (name == null || name.isBlank()) {
                    log.warn("Database '{}' — skipping listCollections entry with null/blank name", db.getName());
                    continue;
                }
                // H2: skip views and other non-collection types to avoid collStats failures;
                // treat null type (MongoDB 3.2) as a plain collection for compatibility
                if (type != null && !"collection".equals(type)) {
                    log.debug("Database '{}' — skipping '{}' of type '{}' (not a plain collection)",
                            db.getName(), name, type);
                    continue;
                }
                if (phantomCollections.contains(name)) {
                    log.warn("Database '{}' — skipping phantom collection '{}'", db.getName(), name);
                    continue;
                }
                collectionNames.add(name);
            }
        } catch (MongoException e) {
            log.error("Could not list collections in '{}' — database will appear empty: {}",
                    db.getName(), e.getMessage(), e);
        }
        log.debug("Database '{}' — {} collection(s) discovered", db.getName(), collectionNames.size());
        return collectionNames;
    }
}
