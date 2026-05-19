package io.diegorocha.imitator.service;

import io.diegorocha.imitator.collector.ClusterCollector;
import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.output.ClusterOutput;
import io.diegorocha.imitator.model.output.CollectorOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the sizing collection pipeline across all clusters in a
 * {@link io.diegorocha.imitator.model.input.CollectorInput}.
 * <p>Iterates the cluster list, delegates each cluster to {@link io.diegorocha.imitator.collector.ClusterCollector},
 * and catches per-cluster failures so that a single unreachable cluster does not abort the
 * rest of the run. Failed clusters are logged and omitted from the output.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Service
public class CollectorService {

    private static final Logger log = LoggerFactory.getLogger(CollectorService.class);
    private final ClusterCollector clusterCollector;

    public CollectorService(ClusterCollector clusterCollector) {
        this.clusterCollector = clusterCollector;
    }

    public CollectorOutput collect(CollectorInput collectorInput) {
        List<ClusterOutput> results = new ArrayList<>();
        for (var cluster : collectorInput.clusters()) {
            try {
                results.add(clusterCollector.collect(cluster));
            } catch (Exception e) {
                log.error("Error while collecting cluster {}", cluster.name(), e);
            }
        }
        return new CollectorOutput(Instant.now(), results);
    }
}
