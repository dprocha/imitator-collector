package io.diegorocha.imitator.serialization;

import io.diegorocha.imitator.model.output.CollectorOutput;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

/**
 * Writes a {@link io.diegorocha.imitator.model.output.CollectorOutput} to a JSON file on disk.
 * <p>Thin wrapper around
 * {@code JsonMapper.writerWithDefaultPrettyPrinter().writeValue(Path, Object)}.
 * Available as a Spring bean for CLI or tooling use cases; not called by any REST
 * controller.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class OutputWriter {

    private final JsonMapper jsonMapper;

    public OutputWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(CollectorOutput output, Path path) {
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(path, output);
    }
}
