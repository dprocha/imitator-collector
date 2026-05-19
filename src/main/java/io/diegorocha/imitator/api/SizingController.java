package io.diegorocha.imitator.api;

import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.output.CollectorOutput;
import io.diegorocha.imitator.service.CollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the sizing collection endpoint.
 * <p>{@code POST /api/sizing/collect} — connects to one or more MongoDB-compatible clusters,
 * collects per-collection storage statistics, and returns a JSON sizing report.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/sizing")
public class SizingController {

    private static final Logger log = LoggerFactory.getLogger(SizingController.class);

    private final CollectorService collectorService;

    public SizingController(CollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @PostMapping(path = "/collect", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CollectorOutput> collect(@RequestBody CollectorInput collectorInput) {
        log.debug("POST /collect — {} cluster(s): {}", collectorInput.clusters().size(),
                collectorInput.clusters().stream().map(c -> c.name()).toList());
        try {
            return ResponseEntity.ok(collectorService.collect(collectorInput));
        } catch (Exception e) {
            log.error("Unexpected error during /collect", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
