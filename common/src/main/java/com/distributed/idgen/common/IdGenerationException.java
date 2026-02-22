package com.distributed.idgen.common;

/**
 * Thrown when an ID generator cannot safely produce a unique identifier.
 *
 * <p>
 * Common causes:
 * </p>
 * <ul>
 * <li>Clock moved backwards (Snowflake / HLC protection)</li>
 * <li>Sequence counter exhausted and wait-for-next-millisecond timed out</li>
 * <li>Invalid machine/worker ID configuration</li>
 * </ul>
 */
public class IdGenerationException extends RuntimeException {

    public IdGenerationException(String message) {
        super(message);
    }

    public IdGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
