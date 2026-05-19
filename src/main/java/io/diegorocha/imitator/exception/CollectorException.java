package io.diegorocha.imitator.exception;

/**
 * Signals an unrecoverable failure during the data-collection pipeline.
 * <p>The constructor does not log — callers are responsible for logging so that duplicate
 * log entries are avoided when the exception is caught and logged at a higher level.</p>
 *
 * @author Diego Rocha
 * @since 1.0.0
 */
public class CollectorException extends Exception {

    public CollectorException(String message, Throwable cause) {
        super(message, cause);
    }
}
