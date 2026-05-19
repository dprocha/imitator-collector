package io.diegorocha.imitator.serialization;

import io.diegorocha.imitator.model.input.CollectorInput;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

/**
 * Reads a {@link io.diegorocha.imitator.model.input.CollectorInput} from a JSON file on disk.
 * <p>Thin wrapper around {@code JsonMapper.readValue(Path, Class)}. Available as a Spring bean
 * for CLI or tooling use cases; not called by any REST controller.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Component
public class InputReader {

    private final JsonMapper jsonMapper;

    public InputReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public CollectorInput read(Path path) {
        return jsonMapper.readValue(path, CollectorInput.class);
    }
}
