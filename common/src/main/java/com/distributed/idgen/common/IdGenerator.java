package com.distributed.idgen.common;

/**
 * Core contract for all distributed ID generators in this project.
 *
 * <p>
 * Each implementation must guarantee:
 * </p>
 * <ul>
 * <li><b>Uniqueness</b> – No two calls return the same ID within the same
 * generator instance.</li>
 * <li><b>Thread safety</b> – Implementations must be safe for concurrent
 * access.</li>
 * <li><b>Non-null return</b> – {@code generate()} must never return
 * {@code null}.</li>
 * </ul>
 *
 * @param <T> The type of ID produced (e.g., {@code Long} for Snowflake,
 *            {@code String} for ULID).
 */
public interface IdGenerator<T> {

    /**
     * Generate a new unique identifier.
     *
     * @return a non-null, unique ID of type {@code T}
     * @throws IdGenerationException if an error prevents safe ID generation
     */
    T generate();

    /**
     * Returns the human-readable name/strategy of this generator.
     *
     * @return strategy name, e.g. "Snowflake", "UUIDv7", "ULID"
     */
    String strategyName();
}
