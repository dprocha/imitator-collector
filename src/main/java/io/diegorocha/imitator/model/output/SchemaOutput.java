package io.diegorocha.imitator.model.output;

import java.time.Instant;
import java.util.List;

/**
 * Root response payload for the schema extraction endpoint
 * ({@code POST /api/schema/extract}) and the ZIP export endpoint
 * ({@code POST /api/schema/export}).
 *
 * @param dateCreate UTC timestamp when the report was generated (ISO-8601)
 * @param clusters   one entry per successfully processed cluster; failed clusters are absent
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record SchemaOutput(Instant dateCreate, List<ClusterSchema> clusters) {
}
