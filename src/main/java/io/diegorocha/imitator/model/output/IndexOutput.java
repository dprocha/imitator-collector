package io.diegorocha.imitator.model.output;

import java.util.Map;

/**
 * Index definition and size for a single MongoDB index.
 *
 * @param name   index name (e.g. {@code "_id_"}, {@code "userId_1"})
 * @param size   index size in bytes; {@code 0} when stats came from the sampling path
 * @param key    key specification map, e.g. {@code {"userId": 1}} or {@code {"createdAt": -1}};
 *               field order is preserved ({@link java.util.LinkedHashMap})
 * @param unique {@code true} if the index enforces uniqueness
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record IndexOutput(String name, long size, Map<String, Object> key, boolean unique) {
}
