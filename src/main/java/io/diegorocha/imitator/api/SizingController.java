package io.diegorocha.imitator.api;

import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.output.CollectorOutput;
import io.diegorocha.imitator.service.CollectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Sizing", description = "Collect per-collection storage statistics for Atlas migration sizing")
@RestController
@RequestMapping("/api/sizing")
public class SizingController {

    private static final Logger log = LoggerFactory.getLogger(SizingController.class);

    private final CollectorService collectorService;

    public SizingController(CollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @Operation(
            summary = "Collect sizing report",
            description = "Connects to each cluster, collects storage statistics for every collection " +
                    "(document count, average document size, total data size, and index definitions), " +
                    "and returns a structured JSON report for Atlas cluster sizing estimation."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sizing report collected successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CollectorOutput.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error — check application logs",
                    content = @Content)
    })
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
