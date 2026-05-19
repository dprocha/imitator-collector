package io.diegorocha.imitator;

import io.diegorocha.imitator.config.CollectorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot application entry point for imitator-collector.
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
@SpringBootApplication
@EnableConfigurationProperties(CollectorProperties.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
