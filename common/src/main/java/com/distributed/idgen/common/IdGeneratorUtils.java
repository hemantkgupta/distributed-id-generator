package com.distributed.idgen.common;

import java.util.function.LongSupplier;

/**
 * Utility methods shared across ID generator modules.
 */
public final class IdGeneratorUtils {

    private IdGeneratorUtils() {
        // Utility class — no instantiation
    }

    /**
     * Validates that a machine/worker ID falls within an allowed bit range.
     *
     * @param id       The machine or worker ID value to validate
     * @param maxValue The maximum value allowed (inclusive), e.g. 1023 for 10-bit
     * @param label    Human-readable label for the ID type (for exception messages)
     * @throws IllegalArgumentException if {@code id} is negative or exceeds
     *                                  {@code maxValue}
     */
    public static void validateNodeId(long id, long maxValue, String label) {
        if (id < 0 || id > maxValue) {
            throw new IllegalArgumentException(
                    String.format("%s must be between 0 and %d (inclusive), got: %d", label, maxValue, id));
        }
    }

    /**
     * Busy-spins until {@code System.currentTimeMillis()} strictly exceeds
     * {@code lastTimestamp}. Used by Snowflake-style generators when the sequence
     * is exhausted within a millisecond.
     *
     * @param lastTimestamp the timestamp that must be surpassed
     * @return the next millisecond timestamp
     */
    public static long waitNextMillis(long lastTimestamp) {
        return waitNextMillis(lastTimestamp, System::currentTimeMillis);
    }

    /**
     * Busy-spins until the supplied clock strictly exceeds {@code lastTimestamp}.
     *
     * @param lastTimestamp       the timestamp that must be surpassed
     * @param currentTimeSupplier clock supplier in the same timestamp domain as
     *                            {@code lastTimestamp}
     * @return the next timestamp
     */
    public static long waitNextMillis(long lastTimestamp, LongSupplier currentTimeSupplier) {
        long ts = currentTimeSupplier.getAsLong();
        while (ts <= lastTimestamp) {
            ts = currentTimeSupplier.getAsLong();
        }
        return ts;
    }

    /**
     * Converts a byte array to a hex string (lower-case).
     *
     * @param bytes input bytes
     * @return hex representation
     */
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
