package io.diegorocha.imitator.model.output;

import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Schema extraction result for a single collection.
 *
 * @param name            collection name
 * @param sampleSize      number of documents actually sampled (may be less than the configured
 *                        limit if the collection has fewer documents)
 * @param jsonSchema      JSON Schema Draft 2020-12 inferred from the sample set
 * @param exampleDocument anonymised document — original structure preserved, every leaf value
 *                        replaced with safe fake data
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record CollectionSchema(String name, int sampleSize, ObjectNode jsonSchema, Map<String, Object> exampleDocument) {
}
