package io.diegorocha.imitator.model.input;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Root input payload shared by all collector endpoints.
 *
 * @param clusters one or more cluster descriptors to collect from
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Schema(description = "Root input payload — list the clusters you want to collect from")
public record CollectorInput(
        @Schema(description = "One or more MongoDB-compatible cluster descriptors", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ClusterInput> clusters
) {
}
