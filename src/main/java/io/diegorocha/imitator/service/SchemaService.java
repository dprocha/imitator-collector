package io.diegorocha.imitator.service;

import io.diegorocha.imitator.collector.ClusterSchemaCollector;
import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.output.ClusterSchema;
import io.diegorocha.imitator.model.output.SchemaOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the schema extraction pipeline across all clusters in a
 * {@link io.diegorocha.imitator.model.input.CollectorInput}.
 * <p>Iterates the cluster list, delegates each cluster to
 * {@link io.diegorocha.imitator.collector.ClusterSchemaCollector}, and catches per-cluster
 * failures so that a single unreachable cluster does not abort the rest of the run. Failed
 * clusters are logged and omitted from the output.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Service
public class SchemaService {

    private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

    private final ClusterSchemaCollector clusterSchemaCollector;

    public SchemaService(ClusterSchemaCollector clusterSchemaCollector) {
        this.clusterSchemaCollector = clusterSchemaCollector;
    }

    public SchemaOutput collect(CollectorInput input) {
        List<ClusterSchema> clusters = new ArrayList<>();
        for (var cluster : input.clusters()) {
            try {
                clusters.add(clusterSchemaCollector.collect(cluster));
            } catch (Exception e) {
                log.error("Error while extracting schema for cluster '{}'", cluster.name(), e);
            }
        }
        return new SchemaOutput(Instant.now(), clusters);
    }
}
