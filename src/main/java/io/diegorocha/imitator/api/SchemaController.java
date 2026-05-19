package io.diegorocha.imitator.api;

import io.diegorocha.imitator.model.input.CollectorInput;
import io.diegorocha.imitator.model.output.ClusterSchema;
import io.diegorocha.imitator.model.output.CollectionSchema;
import io.diegorocha.imitator.model.output.DatabaseSchema;
import io.diegorocha.imitator.model.output.SchemaOutput;
import io.diegorocha.imitator.service.SchemaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * REST controller exposing the schema extraction and ZIP export endpoints.
 * <ul>
 *   <li>{@code POST /api/schema/extract} — infers JSON Schemas from sampled documents and
 *       returns a JSON report</li>
 *   <li>{@code POST /api/schema/export} — same pipeline, packaged as a downloadable ZIP
 *       ({@code {cluster}/{database}/{collection}/schema.json} and {@code sample.json})</li>
 * </ul>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Tag(name = "Schema", description = "Extract JSON Schemas and anonymised example documents from MongoDB-compatible clusters")
@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private static final Logger log = LoggerFactory.getLogger(SchemaController.class);
    private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");

    private final SchemaService schemaService;
    private final JsonMapper jsonMapper;

    public SchemaController(SchemaService schemaService, JsonMapper jsonMapper) {
        this.schemaService = schemaService;
        this.jsonMapper = jsonMapper;
    }

    @Operation(
            summary = "Extract schemas",
            description = "Samples up to N documents per collection (configurable via " +
                    "collector.sampling.sample-size), infers a JSON Schema Draft 2020-12, and returns " +
                    "anonymised example documents. No real data leaves the server — every leaf value " +
                    "is replaced with a synthetic fake."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schema report extracted successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SchemaOutput.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected server error — check application logs",
                    content = @Content)
    })
    @PostMapping(path = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchemaOutput> extract(@RequestBody CollectorInput collectorInput) {
        log.debug("POST /api/schema/extract — {} cluster(s): {}", collectorInput.clusters().size(),
                collectorInput.clusters().stream().map(c -> c.name()).toList());
        try {
            return ResponseEntity.ok(schemaService.collect(collectorInput));
        } catch (Exception e) {
            log.error("Unexpected error during /api/schema/extract", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Operation(
            summary = "Export schemas as ZIP",
            description = "Runs the same schema extraction pipeline as /extract and packages the result " +
                    "as a downloadable ZIP archive. Structure: " +
                    "{cluster}/{database}/{collection}/schema.json and sample.json."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "ZIP archive containing schemas and sample documents",
                    content = @Content(mediaType = "application/zip")),
            @ApiResponse(responseCode = "500", description = "Unexpected server error — check application logs",
                    content = @Content)
    })
    @PostMapping(path = "/export", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/zip")
    public ResponseEntity<byte[]> export(@RequestBody CollectorInput collectorInput) {
        log.debug("POST /api/schema/export — {} cluster(s): {}", collectorInput.clusters().size(),
                collectorInput.clusters().stream().map(c -> c.name()).toList());
        try {
            SchemaOutput output = schemaService.collect(collectorInput);
            byte[] zip = buildZip(output);
            log.debug("ZIP assembled — {} bytes", zip.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schema.zip\"")
                    .contentType(APPLICATION_ZIP)
                    .body(zip);
        } catch (Exception e) {
            log.error("Unexpected error during /api/schema/export", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // ZIP assembly
    // -------------------------------------------------------------------------

    // Structure: {cluster}/{database}/{collection}/schema.json
    //                                              sample.json
    private byte[] buildZip(SchemaOutput output) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (ClusterSchema cluster : output.clusters()) {
                for (DatabaseSchema database : cluster.databases()) {
                    for (CollectionSchema collection : database.collections()) {
                        String dir = sanitize(cluster.name()) + "/"
                                   + sanitize(database.name()) + "/"
                                   + sanitize(collection.name()) + "/";
                        writeEntry(zos, dir + "schema.json",
                                jsonMapper.writerWithDefaultPrettyPrinter()
                                          .writeValueAsBytes(collection.jsonSchema()));
                        writeEntry(zos, dir + "sample.json",
                                jsonMapper.writerWithDefaultPrettyPrinter()
                                          .writeValueAsBytes(collection.exampleDocument()));
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    private void writeEntry(ZipOutputStream zos, String path, byte[] content) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(content);
        zos.closeEntry();
    }

    // Replaces characters that are unsafe in ZIP entry paths with underscores.
    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
