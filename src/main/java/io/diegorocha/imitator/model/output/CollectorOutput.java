package io.diegorocha.imitator.model.output;

import java.time.Instant;
import java.util.List;

/**
 * Root response payload for the sizing endpoint ({@code POST /api/sizing/collect}).
 *
 * @param dateCreate UTC timestamp when the report was generated (ISO-8601)
 * @param clusters   one entry per successfully collected cluster; failed clusters are absent
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record CollectorOutput(Instant dateCreate, List<ClusterOutput> clusters) {
}
