package io.diegorocha.imitator.collector;

import org.bson.Document;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Infers a JSON Schema Draft 2020-12 {@link tools.jackson.databind.node.ObjectNode} from a
 * list of sampled BSON {@link org.bson.Document} objects at runtime.
 * <p>Schema inference inspects the runtime Java types returned by the BSON driver — it never
 * uses Java class annotations or reflection on model types. Handles nested objects (recursive),
 * arrays (items schema from all elements across samples), nullable fields
 * ({@code ["type","null"]} array), and mixed types (dominant type wins). Format hints
 * ({@code objectid}, {@code date-time}, {@code byte}) are applied for non-string BSON types
 * that map to JSON {@code "string"}.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
class BsonJsonSchemaGenerator {

    private static final String SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema";

    private final JsonMapper jsonMapper;

    BsonJsonSchemaGenerator(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    ObjectNode generate(String title, List<Document> samples) {
        ObjectNode root = jsonMapper.createObjectNode();
        root.put("$schema", SCHEMA_URI);
        root.put("title", title);
        root.put("type", "object");
        if (!samples.isEmpty()) {
            root.set("properties", buildProperties(samples));
        }
        return root;
    }

    // Groups values per field across all sample documents, then infers one schema per field.
    private ObjectNode buildProperties(List<Document> docs) {
        Map<String, List<Object>> byField = new LinkedHashMap<>();
        for (Document doc : docs) {
            doc.forEach((k, v) -> byField.computeIfAbsent(k, _ -> new ArrayList<>()).add(v));
        }
        ObjectNode props = jsonMapper.createObjectNode();
        byField.forEach((field, values) -> props.set(field, fieldSchema(values)));
        return props;
    }

    private ObjectNode fieldSchema(List<Object> values) {
        List<Object> nonNull = values.stream().filter(Objects::nonNull).toList();
        boolean nullable = nonNull.size() < values.size();
        ObjectNode schema = jsonMapper.createObjectNode();

        if (nonNull.isEmpty()) {
            schema.put("type", "null");
            return schema;
        }

        String type = dominantJsonType(nonNull);

        if (nullable) {
            ArrayNode typeArray = jsonMapper.createArrayNode().add(type).add("null");
            schema.set("type", typeArray);
        } else {
            schema.put("type", type);
        }

        switch (type) {
            case "object" -> {
                List<Document> subDocs = nonNull.stream()
                        .filter(v -> v instanceof Document)
                        .map(v -> (Document) v)
                        .toList();
                if (!subDocs.isEmpty()) {
                    schema.set("properties", buildProperties(subDocs));
                }
            }
            case "array" -> {
                List<Object> allElements = nonNull.stream()
                        .filter(v -> v instanceof List<?>)
                        .flatMap(v -> ((List<?>) v).stream())
                        .map(e -> (Object) e)
                        .toList();
                if (!allElements.isEmpty()) {
                    schema.set("items", fieldSchema(allElements));
                }
            }
            case "string" -> applyStringFormat(schema, nonNull);
        }

        return schema;
    }

    // "string" covers ObjectId, Date, Binary, UUID, and actual String values.
    // Apply a format hint for the non-string BSON types so consumers know the encoding.
    private void applyStringFormat(ObjectNode schema, List<Object> values) {
        Object representative = values.getFirst();
        if (representative instanceof Date) {
            schema.put("format", "date-time");
        } else if (representative instanceof Binary) {
            schema.put("format", "byte");
        } else if (representative instanceof ObjectId) {
            schema.put("format", "objectid");
        }
    }

    private String dominantJsonType(List<Object> values) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object v : values) {
            counts.merge(toJsonType(v), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("string");
    }

    private String toJsonType(Object value) {
        if (value == null) return "null";
        return switch (value) {
            case Boolean _              -> "boolean";
            case Integer _, Long _      -> "integer";
            case Double _, Decimal128 _ -> "number";
            case Document _             -> "object";
            case List<?> _              -> "array";
            default                     -> "string"; // String, ObjectId, Date, Binary
        };
    }
}
