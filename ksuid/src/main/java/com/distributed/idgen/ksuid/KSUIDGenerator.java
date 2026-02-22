package com.distributed.idgen.ksuid;

import com.distributed.idgen.common.IdGenerator;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * KSUID (K-Sortable Unique IDentifier) generator.
 *
 * <h2>Structure (160 bits = 20 bytes)</h2>
 * 
 * <pre>
 * ┌──────────────────────────────┬──────────────────────────────────────────────────┐
 * │       Timestamp  (32 bits)   │           Payload / Randomness  (128 bits)       │
 * │  Seconds since KSUID epoch   │              Cryptographic randomness             │
 * └──────────────────────────────┴──────────────────────────────────────────────────┘
 * </pre>
 * 
 * Encoded as a 27-character Base62 string.
 *
 * <h2>KSUID Epoch</h2>
 * KSUID uses a custom epoch of {@code 2014-05-13T16:53:20Z} (Unix seconds:
 * 1400000000),
 * giving ~136 years of coverage.
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>✅ 128-bit payload → dramatically lower collision probability than
 * ULID</li>
 * <li>✅ Lexicographically sortable (timestamp prefix)</li>
 * <li>✅ 27-character Base62 string — compact and URL-safe</li>
 * <li>✅ Popular in distributed event-sourcing / Kafka stream architectures</li>
 * <li>❌ 160-bit total — largest identifier in this project</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * {@link SecureRandom} is thread-safe; no mutable shared state.
 */
public class KSUIDGenerator implements IdGenerator<String> {

    /** KSUID custom epoch: 2014-05-13T16:53:20Z in Unix seconds */
    static final long KSUID_EPOCH_SECONDS = 1_400_000_000L;

    /** Length of the Base62-encoded KSUID string */
    static final int KSUID_STRING_LENGTH = 27;

    /** Total payload size in bytes: 4 bytes timestamp + 16 bytes random */
    static final int KSUID_BYTES = 20;

    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        long epochSeconds = Instant.now().getEpochSecond() - KSUID_EPOCH_SECONDS;

        // Assemble 20-byte payload
        byte[] payload = new byte[16]; // 128-bit random
        random.nextBytes(payload);

        ByteBuffer buf = ByteBuffer.allocate(KSUID_BYTES);
        buf.putInt((int) epochSeconds); // 32-bit timestamp (big-endian)
        buf.put(payload); // 128-bit randomness

        return base62Encode(buf.array());
    }

    @Override
    public String strategyName() {
        return "KSUID (160-bit K-Sortable, Base62)";
    }

    // -----------------------------------------------------------------------
    // Base62 encoding
    // -----------------------------------------------------------------------

    /**
     * Encodes a 20-byte array into a 27-character Base62 string using big-endian
     * unsigned arithmetic, left-padded with '0' to always produce exactly 27 chars.
     *
     * @param bytes 20-byte KSUID raw payload
     * @return 27-character Base62-encoded KSUID
     */
    static String base62Encode(byte[] bytes) {
        // Treat 20 bytes as a big-endian unsigned integer
        // Use an int[] of length 5 to hold it as 5 × 32-bit chunks
        // then repeatedly divide by 62 to extract Base62 digits.
        // The maximum KSUID value fits in a 160-bit number.

        // Copy bytes into an int array (4 bytes each, big-endian)
        int[] chunks = new int[5];
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        for (int i = 0; i < 5; i++) {
            chunks[i] = buf.getInt();
        }

        char[] result = new char[KSUID_STRING_LENGTH];
        for (int i = KSUID_STRING_LENGTH - 1; i >= 0; i--) {
            long carry = 0;
            for (int j = 0; j < chunks.length; j++) {
                long val = Integer.toUnsignedLong(chunks[j]) + carry * 0x100000000L;
                chunks[j] = (int) (val / 62);
                carry = val % 62;
            }
            result[i] = BASE62[(int) carry];
        }

        return new String(result);
    }

    /**
     * Decodes the timestamp portion (first 4 bytes) from a raw 20-byte KSUID
     * and returns the wall-clock {@link Instant}.
     *
     * @param rawBytes 20-byte raw KSUID
     * @return wall-clock instant encoded in the KSUID
     */
    public static Instant extractTimestamp(byte[] rawBytes) {
        long epochSeconds = Integer.toUnsignedLong(ByteBuffer.wrap(rawBytes, 0, 4).getInt());
        return Instant.ofEpochSecond(epochSeconds + KSUID_EPOCH_SECONDS);
    }
}
