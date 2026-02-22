package com.distributed.idgen.uuid;

import com.distributed.idgen.common.IdGenerator;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * UUID Version 7 (time-ordered) ID generator.
 *
 * <h2>Structure (RFC 9562)</h2>
 * 
 * <pre>
 * ┌──────────────────────────────┬──────────────────────────────────────────────┐
 * │    Unix timestamp ms (48 b)  │  Version=7(4b) │ rand_a(12b) │rand_b(62b)   │
 * └──────────────────────────────┴──────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Trade-offs vs UUIDv4</h2>
 * <ul>
 * <li>✅ Timestamp prefix → sequential database insertions → no B-Tree
 * fragmentation</li>
 * <li>✅ Still fully decentralised (no coordination needed)</li>
 * <li>✅ Interoperable with existing UUID columns/libraries</li>
 * <li>❌ Still 128-bit (twice the size of a 64-bit Snowflake integer)</li>
 * </ul>
 *
 * <p>
 * This implementation manually composes a UUIDv7 value using
 * {@link System#currentTimeMillis()} for the 48-bit timestamp and
 * {@link java.security.SecureRandom} for the random portions.
 * </p>
 *
 * <p>
 * Thread-safe: all state is derived from thread-local sources.
 * </p>
 */
public class UUIDv7Generator implements IdGenerator<String> {

    private static final java.util.Random RNG = new java.security.SecureRandom();

    /**
     * Generates a UUIDv7 string in canonical 8-4-4-4-12 hex format.
     *
     * <p>
     * Layout of the 128 bits:
     * </p>
     * <ol>
     * <li>Bits 0–47 : 48-bit Unix timestamp in milliseconds (big-endian)</li>
     * <li>Bits 48–51 : Version nibble (0b0111 = 7)</li>
     * <li>Bits 52–63 : 12 random bits (rand_a)</li>
     * <li>Bits 64–65 : Variant bits (0b10)</li>
     * <li>Bits 66–127: 62 random bits (rand_b)</li>
     * </ol>
     */
    @Override
    public String generate() {
        long epochMs = System.currentTimeMillis();

        // 8 random bytes for the lower 64 bits
        byte[] randomBytes = new byte[8];
        RNG.nextBytes(randomBytes);

        // Assemble 128 bits into two 64-bit longs
        long msb = (epochMs << 16) // bits 0–47 = timestamp
                | (0x7000L) // bits 48–51 = version 7
                | (RNG.nextLong() & 0x0FFFL); // bits 52–63 = rand_a (12 bits)

        // Read 8 random bytes as lsb, then stamp variant bits (0b10) into bits 64–65
        long lsb = ByteBuffer.wrap(randomBytes).getLong();
        lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variant 10xx

        return new UUID(msb, lsb).toString();
    }

    @Override
    public String strategyName() {
        return "UUIDv7 (Time-Ordered 128-bit)";
    }
}
