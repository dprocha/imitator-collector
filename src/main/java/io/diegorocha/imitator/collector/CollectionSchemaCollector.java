package io.diegorocha.imitator.collector;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import io.diegorocha.imitator.config.CollectorProperties;
import io.diegorocha.imitator.model.output.CollectionSchema;
import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Samples documents from a single MongoDB collection and produces a
 * {@link io.diegorocha.imitator.model.output.CollectionSchema} containing an inferred JSON
 * Schema and an anonymised example document.
 * <p>Sampling fetches up to {@code sampleSize} documents (configurable via
 * {@code collector.sampling.sample-size}). All leaf values in the example document are replaced
 * with safe fake data — no original data is retained in the output.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class CollectionSchemaCollector {

    private static final Logger log = LoggerFactory.getLogger(CollectionSchemaCollector.class);

    private final int sampleSize;
    private final BsonJsonSchemaGenerator schemaGenerator;

    public CollectionSchemaCollector(CollectorProperties properties, BsonJsonSchemaGenerator schemaGenerator) {
        this.sampleSize = properties.sampling().sampleSize();
        this.schemaGenerator = schemaGenerator;
    }

    public CollectionSchema collect(MongoDatabase db, String collectionName) {
        List<Document> samples = new ArrayList<>();
        try {
            db.getCollection(collectionName).find().limit(sampleSize).into(samples);
        } catch (MongoException e) {
            log.warn("Could not sample '{}.{}': {} — schema will be empty",
                    db.getName(), collectionName, e.getMessage());
        }
        log.debug("Sampled {} document(s) from '{}.{}' for schema extraction",
                samples.size(), db.getName(), collectionName);
        Map<String, Object> example = samples.isEmpty() ? Map.of() : anonymize(samples.getFirst());
        return new CollectionSchema(collectionName, samples.size(),
                schemaGenerator.generate(collectionName, samples), example);
    }

    // -------------------------------------------------------------------------
    // Anonymization — preserves structure, replaces every leaf with a fake value
    // -------------------------------------------------------------------------

    private Map<String, Object> anonymize(Document doc) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : doc.entrySet()) {
            result.put(entry.getKey(), anonymizeValue(entry.getValue()));
        }
        return result;
    }

    private Object anonymizeValue(Object value) {
        if (value == null) return null;
        return switch (value) {
            case String _       -> UUID.randomUUID().toString();
            case Integer _      -> 0;
            case Long _         -> 0L;
            case Double _       -> 0.0;
            case Boolean _      -> false;
            case Date _         -> "1970-01-01T00:00:00.000Z";
            case ObjectId _     -> "000000000000000000000000";
            case Decimal128 _   -> "0";
            case Binary _       -> "";
            case Document d     -> anonymize(d);
            case List<?> l      -> anonymizeArray(l);
            default             -> "";
        };
    }

    // Retains one element to show the array item structure without exposing real data.
    private List<Object> anonymizeArray(List<?> array) {
        if (array.isEmpty()) return List.of();
        return List.of(anonymizeValue(array.getFirst()));
    }
}
