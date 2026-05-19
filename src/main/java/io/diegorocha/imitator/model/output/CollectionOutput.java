package io.diegorocha.imitator.model.output;

/**
 * Sizing result for a single collection.
 *
 * @param name      collection name
 * @param collStats per-collection statistics (native or estimated via sampling)
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record CollectionOutput(String name, CollStats collStats) {
}
