package io.diegorocha.imitator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 ({@code tools.jackson.core}) configuration.
 * <p>Registers a {@link tools.jackson.databind.json.JsonMapper} bean with two global settings:
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled (extra request fields are silently ignored) and
 * {@code WRITE_DATES_AS_TIMESTAMPS} disabled ({@link java.time.Instant} fields serialise as
 * ISO-8601 strings, e.g. {@code "2026-04-22T20:00:00Z"}).</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper objectMapper() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
