package io.diegorocha.imitator.model.output;

import java.util.List;

/**
 * Schema extraction report for a single database.
 *
 * @param name        database name
 * @param collections one entry per collected collection
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record DatabaseSchema(String name, List<CollectionSchema> collections) {
}
