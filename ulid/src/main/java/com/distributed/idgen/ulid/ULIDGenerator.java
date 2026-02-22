package com.distributed.idgen.ulid;

import com.distributed.idgen.common.IdGenerator;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * ULID (Universally Unique Lexicographically Sortable Identifier) generator.
 *
 * <h2>Structure (128 bits, 26-character Base32 string)</h2>
 * 
 * <pre>
 * ┌─────────────────────────────┬──────────────────────────────────────────────────┐
 * │     Timestamp   (48 bits)   │           Randomness   (80 bits)                 │
 * │  Unix epoch milliseconds    │    Crockford Base32 — no ambiguous characters    │
 * └─────────────────────────────┴──────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>✅ Lexicographically sortable — alphabetical order = chronological
 * order</li>
 * <li>✅ 26-character Base32 — URL-safe, human-readable</li>
 * <li>✅ No external dependencies — fully self-contained</li>
 * <li>✅ Monotonic within the same millisecond (random part incremented by
 * 1)</li>
 * <li>❌ 128-bit — larger than Snowflake's 64-bit integer</li>
 * </ul>
 *
 * <h2>Crockford Base32 Alphabet</h2>
 * Uses {@code 0123456789ABCDEFGHJKMNPQRSTVWXYZ} — excludes I, L, O, U to avoid
 * visual ambiguity.
 *
 * <h2>Thread Safety</h2>
 * All mutable state is guarded by {@code synchronized}.
 */
public class ULIDGenerator implements IdGenerator<String> {

    /**
     * Crockford Base32 alphabet — 32 characters, excluding I, L, O, U.
     */
    static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int ULID_LENGTH = 26;
    private static final int TIMESTAMP_LENGTH = 10; // chars for 48-bit timestamp
    private static final int RANDOMNESS_LENGTH = 16; // chars for 80-bit randomness

    private final SecureRandom random = new SecureRandom();

    // Monotonic state — used to preserve ordering within the same millisecond
    private long lastTimestamp = -1L;
    private final byte[] lastRandomness = new byte[10]; // 80 bits = 10 bytes

    @Override
    public synchronized String generate() {
        long currentTimestamp = Instant.now().toEpochMilli();

        if (currentTimestamp == lastTimestamp) {
            // Increment the 80-bit random part to maintain strict monotonicity
            incrementRandomness();
        } else {
            // New millisecond — generate fresh randomness
            random.nextBytes(lastRandomness);
            lastTimestamp = currentTimestamp;
        }

        return encode(currentTimestamp, lastRandomness);
    }

    @Override
    public String strategyName() {
        return "ULID (128-bit Lexicographically Sortable)";
    }

    // -----------------------------------------------------------------------
    // Encoding
    // -----------------------------------------------------------------------

    /**
     * Encodes a ULID from its timestamp and randomness components.
     *
     * @param timestamp  48-bit Unix millisecond timestamp
     * @param randomness 10-byte (80-bit) random payload
     * @return 26-character Crockford Base32 ULID string
     */
    private String encode(long timestamp, byte[] randomness) {
        char[] ulid = new char[ULID_LENGTH];

        // Encode timestamp into first 10 characters (48 bits → Base32)
        ulid[9] = ENCODING[(int) (timestamp & 0x1F)];
        ulid[8] = ENCODING[(int) ((timestamp >> 5) & 0x1F)];
        ulid[7] = ENCODING[(int) ((timestamp >> 10) & 0x1F)];
        ulid[6] = ENCODING[(int) ((timestamp >> 15) & 0x1F)];
        ulid[5] = ENCODING[(int) ((timestamp >> 20) & 0x1F)];
        ulid[4] = ENCODING[(int) ((timestamp >> 25) & 0x1F)];
        ulid[3] = ENCODING[(int) ((timestamp >> 30) & 0x1F)];
        ulid[2] = ENCODING[(int) ((timestamp >> 35) & 0x1F)];
        ulid[1] = ENCODING[(int) ((timestamp >> 40) & 0x1F)];
        ulid[0] = ENCODING[(int) ((timestamp >> 45) & 0x1F)];

        // Encode 80-bit randomness into last 16 characters
        // 10 bytes → pack into 5-bit groups for Base32
        long hi = ((long) (randomness[0] & 0xFF) << 32)
                | ((long) (randomness[1] & 0xFF) << 24)
                | ((long) (randomness[2] & 0xFF) << 16)
                | ((long) (randomness[3] & 0xFF) << 8)
                | (randomness[4] & 0xFF);

        long lo = ((long) (randomness[5] & 0xFF) << 32)
                | ((long) (randomness[6] & 0xFF) << 24)
                | ((long) (randomness[7] & 0xFF) << 16)
                | ((long) (randomness[8] & 0xFF) << 8)
                | (randomness[9] & 0xFF);

        ulid[10] = ENCODING[(int) ((hi >> 35) & 0x1F)];
        ulid[11] = ENCODING[(int) ((hi >> 30) & 0x1F)];
        ulid[12] = ENCODING[(int) ((hi >> 25) & 0x1F)];
        ulid[13] = ENCODING[(int) ((hi >> 20) & 0x1F)];
        ulid[14] = ENCODING[(int) ((hi >> 15) & 0x1F)];
        ulid[15] = ENCODING[(int) ((hi >> 10) & 0x1F)];
        ulid[16] = ENCODING[(int) ((hi >> 5) & 0x1F)];
        ulid[17] = ENCODING[(int) (hi & 0x1F)];
        ulid[18] = ENCODING[(int) ((lo >> 35) & 0x1F)];
        ulid[19] = ENCODING[(int) ((lo >> 30) & 0x1F)];
        ulid[20] = ENCODING[(int) ((lo >> 25) & 0x1F)];
        ulid[21] = ENCODING[(int) ((lo >> 20) & 0x1F)];
        ulid[22] = ENCODING[(int) ((lo >> 15) & 0x1F)];
        ulid[23] = ENCODING[(int) ((lo >> 10) & 0x1F)];
        ulid[24] = ENCODING[(int) ((lo >> 5) & 0x1F)];
        ulid[25] = ENCODING[(int) (lo & 0x1F)];

        return new String(ulid);
    }

    /**
     * Increments the 80-bit randomness array by 1 (big-endian carry propagation).
     * This preserves strict monotonicity when multiple ULIDs are generated
     * within the same millisecond.
     */
    private void incrementRandomness() {
        for (int i = lastRandomness.length - 1; i >= 0; i--) {
            if (++lastRandomness[i] != 0) {
                break; // no carry required
            }
        }
    }
}
