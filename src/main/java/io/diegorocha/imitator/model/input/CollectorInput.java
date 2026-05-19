package io.diegorocha.imitator.model.input;

import java.util.List;

/**
 * Root input payload shared by all collector endpoints.
 *
 * @param clusters one or more cluster descriptors to collect from
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public record CollectorInput(List<ClusterInput> clusters) {

}
