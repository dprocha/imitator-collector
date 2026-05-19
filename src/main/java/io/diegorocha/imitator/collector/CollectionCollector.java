package io.diegorocha.imitator.collector;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.diegorocha.imitator.model.output.CollStats;
import io.diegorocha.imitator.model.output.CollectionOutput;
import io.diegorocha.imitator.model.output.IndexOutput;
import org.bson.BsonBinaryWriter;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import io.diegorocha.imitator.config.CollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Collects storage statistics and index definitions for a single MongoDB collection.
 * <p>Branches on server major version: uses the {@code collStats} command for MongoDB 3.2–7.x
 * and the {@code $collStats} aggregation for 8.0+. Falls back to BSON document sampling when
 * native stats are unavailable or return incomplete data (e.g. CosmosDB, DocumentDB, MongoDB
 * 3.2 partial implementations). Sampling path sets {@code estimated = true} on the resulting
 * {@link io.diegorocha.imitator.model.output.CollStats}.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class CollectionCollector {

    private static final Logger log = LoggerFactory.getLogger(CollectionCollector.class);
    private static final DocumentCodec DOCUMENT_CODEC = new DocumentCodec();

    // L1: loaded from CollectorProperties so they can be overridden without a code change
    private final Set<String> cosmosdbSystemIndexes;
    private final int sampleSize;

    public CollectionCollector(CollectorProperties properties) {
        this.cosmosdbSystemIndexes = properties.cosmosdb().systemIndexesAsSet();
        this.sampleSize = properties.sampling().sampleSize();
    }

    /**
     * Pairs a partial {@link CollStats} (no indexes populated yet) with the
     * per-index size map needed to build {@link IndexOutput} entries.
     */
    private record CollectionContext(CollStats stats, Map<String, Long> indexSizes) {
        static CollectionContext empty() {
            return new CollectionContext(CollStats.empty(), Map.of());
        }
    }

    public CollectionOutput collect(MongoDatabase db, String collectionName,
                                    ClusterCollector.ServerVersion serverVersion) {
        CollectionContext ctx = resolveStats(db, collectionName, serverVersion);

        if (ctx.stats().estimated()) {
            log.info("Size stats for '{}.{}' are estimated from a {}-document sample",
                    db.getName(), collectionName, sampleSize);
        }

        List<IndexOutput> indexes = buildIndexOutputs(db, collectionName, ctx.indexSizes());

        CollStats collStats = new CollStats(
                ctx.stats().count(),
                ctx.stats().avgObjSize(),
                ctx.stats().dataSize(),
                indexes.size(),
                ctx.stats().totalIndexSize(),
                indexes,
                ctx.stats().estimated()
        );
        return new CollectionOutput(collectionName, collStats);
    }

    // -------------------------------------------------------------------------
    // Stats resolution — native paths then sampling fallback
    // -------------------------------------------------------------------------

    private CollectionContext resolveStats(MongoDatabase db, String collectionName,
                                           ClusterCollector.ServerVersion serverVersion) {
        try {
            CollectionContext ctx = serverVersion.major() >= 8
                    ? collectViaAggregation(db, collectionName)
                    : collectViaCommand(db, collectionName);

            if (ctx.stats().count() > 0 && ctx.stats().avgObjSize() == 0) {
                log.warn("Stats incomplete for '{}.{}' (count={}, avgObjSize=0) — falling back to sampling",
                        db.getName(), collectionName, ctx.stats().count());
                return collectViaSampling(db, collectionName, ctx.stats().count());
            }
            return ctx;

        } catch (MongoException e) {
            log.error("Could not get stats for '{}.{}': {} — falling back to sampling",
                    db.getName(), collectionName, e.getMessage());
            return collectViaSampling(db, collectionName, -1);
        }
    }

    private CollectionContext collectViaAggregation(MongoDatabase db, String collectionName) {
        log.debug("Stats path: $collStats aggregation for '{}.{}'", db.getName(), collectionName);
        Document statsDoc = db.getCollection(collectionName)
                .aggregate(List.of(new Document("$collStats",
                        new Document("storageStats", new Document("scale", 1)))))
                .first();
        if (statsDoc == null) return CollectionContext.empty();

        Document storageStats = statsDoc.get("storageStats", Document.class);
        if (storageStats == null) return CollectionContext.empty();

        Map<String, Long> indexSizes = extractIndexSizes(storageStats.get("indexSizes", Document.class));
        long count = toLong(storageStats.get("count"));
        long avgObjSize = toLong(storageStats.get("avgObjSize"));
        long dataSize = toLong(storageStats.get("size"));
        log.debug("$collStats '{}.{}' — count={}, avgObjSize={} B, dataSize={} B",
                db.getName(), collectionName, count, avgObjSize, dataSize);
        CollStats stats = new CollStats(count, avgObjSize, dataSize, 0,
                toLong(storageStats.get("totalIndexSize")), List.of(), false);
        return new CollectionContext(stats, indexSizes);
    }

    private CollectionContext collectViaCommand(MongoDatabase db, String collectionName) {
        log.debug("Stats path: collStats command for '{}.{}'", db.getName(), collectionName);
        Document result = db.runCommand(new Document("collStats", collectionName));
        Map<String, Long> indexSizes = extractIndexSizes(result.get("indexSizes", Document.class));
        long count = toLong(result.get("count"));
        long avgObjSize = toLong(result.get("avgObjSize"));
        long dataSize = toLong(result.get("size"));
        log.debug("collStats '{}.{}' — count={}, avgObjSize={} B, dataSize={} B",
                db.getName(), collectionName, count, avgObjSize, dataSize);
        CollStats stats = new CollStats(count, avgObjSize, dataSize, 0,
                toLong(result.get("totalIndexSize")), List.of(), false);
        return new CollectionContext(stats, indexSizes);
    }

    // -------------------------------------------------------------------------
    // Sampling-based estimation fallback
    // -------------------------------------------------------------------------

    /**
     * Estimates size statistics by sampling up to {@code sampleSize} documents
     * and measuring their BSON-encoded sizes, then extrapolating to the full
     * collection. Used when native stats commands are unavailable or return
     * incomplete data (e.g. MongoDB 3.2 partial implementations, CosmosDB,
     * DocumentDB).
     *
     * @param knownCount document count if already fetched, or {@code -1} to fetch it
     */
    private CollectionContext collectViaSampling(MongoDatabase db, String collectionName, long knownCount) {
        long count = knownCount > 0 ? knownCount : fetchCount(db, collectionName);
        if (count == 0) return CollectionContext.empty();

        List<Document> samples = new ArrayList<>((int) Math.min(sampleSize, count));
        try {
            db.getCollection(collectionName).find().limit(sampleSize).into(samples);
        } catch (MongoException e) {
            log.warn("Sampling failed for '{}.{}': {} — size stats will be 0",
                    db.getName(), collectionName, e.getMessage());
            return new CollectionContext(new CollStats(count, 0, 0, 0, 0, List.of(), true), Map.of());
        }

        if (samples.isEmpty()) {
            return new CollectionContext(new CollStats(count, 0, 0, 0, 0, List.of(), true), Map.of());
        }

        long avgDocSize = samples.stream().mapToLong(this::getBsonSize).sum() / samples.size();
        long totalDataSize = count * avgDocSize;
        log.debug("Sampling '{}.{}' — {} docs sampled, avgDocSize={} B, estimatedDataSize={} B",
                db.getName(), collectionName, samples.size(), avgDocSize, totalDataSize);

        IndexSizeStats indexSizeStats = estimateIndexStats(db, collectionName, count);

        CollStats stats = new CollStats(count, avgDocSize, totalDataSize, 0,
                indexSizeStats.totalIndexSize(), List.of(), true);
        return new CollectionContext(stats, indexSizeStats.indexSizes());
    }

    /**
     * Issues a {@code count} command — compatible with all MongoDB versions
     * starting from 3.2, unlike the driver's {@code countDocuments()} which
     * requires an aggregation-capable server (3.6+).
     */
    private long fetchCount(MongoDatabase db, String collectionName) {
        try {
            Document result = db.runCommand(new Document("count", collectionName));
            return toLong(result.get("n"));
        } catch (MongoException e) {
            log.warn("Could not count documents in '{}.{}': {} — using 0",
                    db.getName(), collectionName, e.getMessage());
            return 0;
        }
    }

    private long getBsonSize(Document doc) {
        BasicOutputBuffer buffer = new BasicOutputBuffer();
        BsonBinaryWriter writer = new BsonBinaryWriter(buffer);
        DOCUMENT_CODEC.encode(writer, doc, EncoderContext.builder().build());
        writer.close();
        return buffer.getSize();
    }

    // -------------------------------------------------------------------------
    // Index size estimation (used for sampling path)
    // -------------------------------------------------------------------------

    private IndexSizeStats estimateIndexStats(MongoDatabase db, String collectionName, long countDocuments) {
        List<Document> indexes = listIndexes(db, collectionName);
        Map<String, Long> perIndex = new LinkedHashMap<>();
        long total = 0L;

        for (Document index : indexes) {
            String name = index.getString("name");
            if (cosmosdbSystemIndexes.contains(name)) continue;
            Document keySpec = index.get("key", Document.class);
            long estimated = estimateIndexSize(db, collectionName, keySpec, countDocuments);
            perIndex.put(name, estimated);
            total += estimated;
        }

        return new IndexSizeStats(total, perIndex);
    }

    private long estimateIndexSize(MongoDatabase db, String collectionName,
                                   Document keySpec, long countDocuments) {
        Document projection = new Document("_id", 0);
        for (String field : keySpec.keySet()) {
            projection.append(field, 1);
        }

        MongoCollection<RawBsonDocument> collection =
                db.getCollection(collectionName, RawBsonDocument.class);

        List<RawBsonDocument> documents = new ArrayList<>();
        try {
            collection.find().projection(projection).limit(sampleSize).into(documents);
        } catch (MongoException e) {
            log.warn("Could not sample index keys for '{}.{}': {} — index size estimate will be 0",
                    db.getName(), collectionName, e.getMessage());
            return 0L;
        }

        if (documents.isEmpty()) return 0L;

        double avgKeySize = documents.stream()
                .mapToInt(doc -> doc.getByteBuffer().remaining())
                .average()
                .orElse(0);

        return (long) ((avgKeySize + 37L) * countDocuments);
    }

    // -------------------------------------------------------------------------
    // Index collection
    // -------------------------------------------------------------------------

    private List<IndexOutput> buildIndexOutputs(MongoDatabase db, String collectionName,
                                                Map<String, Long> indexSizes) {
        List<IndexOutput> result = new ArrayList<>();
        for (Document doc : listIndexes(db, collectionName)) {
            String name = doc.getString("name");
            // L1: exclude CosmosDB wildcard index — no Atlas equivalent, always size 0 on the wire
            if (cosmosdbSystemIndexes.contains(name)) {
                log.debug("Skipping system index '{}' in '{}.{}'", name, db.getName(), collectionName);
                continue;
            }
            Document keyDoc = doc.get("key", Document.class);
            Map<String, Object> key = new LinkedHashMap<>();
            if (keyDoc != null) key.putAll(keyDoc);
            boolean unique = Boolean.TRUE.equals(doc.getBoolean("unique"));
            result.add(new IndexOutput(name, indexSizes.getOrDefault(name, 0L), key, unique));
        }
        log.debug("Indexes for '{}.{}': {}", db.getName(), collectionName, result.size());
        return result;
    }

    // C1: runCommand() + CursorHelper.exhaustCursor() to fully enumerate collections with
    // more than 100 indexes. The driver's collection.listIndexes() iterable has the same
    // internal skip-count bug against CosmosDB 3.2 cursors as db.listCollections().
    // L3: all MongoCommandException codes are handled gracefully; no RuntimeException propagation.
    private List<Document> listIndexes(MongoDatabase db, String collectionName) {
        try {
            Document cmdResult = db.runCommand(new Document("listIndexes", collectionName));
            return CursorHelper.exhaustCursor(db, cmdResult, log);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == 26) {
                log.error("Collection not found: '{}.{}' — skipping indexes", db.getName(), collectionName);
            } else {
                log.warn("Could not list indexes for '{}.{}': {} — skipping indexes",
                        db.getName(), collectionName, e.getErrorMessage());
            }
            return List.of();
        } catch (MongoException e) {
            log.warn("Could not list indexes for '{}.{}': {} — skipping indexes",
                    db.getName(), collectionName, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Long> extractIndexSizes(Document indexSizesDoc) {
        if (indexSizesDoc == null) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : indexSizesDoc.entrySet()) {
            if (entry.getValue() instanceof Number n) {
                result.put(entry.getKey(), n.longValue());
            }
        }
        return result;
    }

    // L2: non-null unexpected types are logged so that field renames in server responses
    // surface as warnings rather than silent zeros.
    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        log.warn("Expected a numeric value but got {} '{}' — using 0",
                value.getClass().getSimpleName(), value);
        return 0L;
    }

    private record IndexSizeStats(long totalIndexSize, Map<String, Long> indexSizes) {}
}
