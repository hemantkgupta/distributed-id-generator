package com.distributed.idgen.nanoid;

import com.distributed.idgen.common.IdGenerator;

import java.security.SecureRandom;

/**
 * NanoID random string ID generator.
 *
 * <h2>Overview</h2>
 * NanoID generates compact, URL-safe random strings with a customisable
 * alphabet and length. Unlike Snowflake or ULID, it has <b>no embedded
 * timestamp</b>,
 * which means IDs are not time-ordered — a deliberate trade-off in favour of
 * simplicity and flexibility.
 *
 * <h2>Default Configuration</h2>
 * <ul>
 * <li>Alphabet: {@code A-Za-z0-9_-} (64 characters — URL-safe)</li>
 * <li>Length: 21 characters → ~126 bits of entropy</li>
 * </ul>
 *
 * <h2>Collision Probability</h2>
 * With the default 21-char length over a 64-char alphabet:
 * ~1 billion IDs needed before a 1% collision probability is reached.
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>✅ Customisable alphabet and length</li>
 * <li>✅ URL-safe by default</li>
 * <li>✅ Compact representation</li>
 * <li>❌ No time ordering → unsuitable for clustered B-Tree indexes</li>
 * <li>❌ String type → larger than a 64-bit integer</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * {@link SecureRandom} is thread-safe. No mutable shared state.
 */
public class NanoIdGenerator implements IdGenerator<String> {

    /** Default URL-safe alphabet (64 characters). */
    public static final String DEFAULT_ALPHABET = "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** Default ID length: 21 characters → ~126 bits of entropy. */
    public static final int DEFAULT_SIZE = 21;

    private final char[] alphabet;
    private final int size;
    private final SecureRandom random;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Creates a NanoID generator with the default 21-char URL-safe alphabet. */
    public NanoIdGenerator() {
        this(DEFAULT_ALPHABET, DEFAULT_SIZE);
    }

    /**
     * Creates a NanoID generator with a custom alphabet and length.
     *
     * @param alphabet characters to sample from (must be 2–256 chars, no duplicates
     *                 enforced)
     * @param size     number of characters in each generated ID (must be ≥ 1)
     */
    public NanoIdGenerator(String alphabet, int size) {
        if (alphabet == null || alphabet.isEmpty()) {
            throw new IllegalArgumentException("Alphabet must not be null or empty");
        }
        if (alphabet.length() > 256) {
            throw new IllegalArgumentException("Alphabet must have at most 256 characters");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        this.alphabet = alphabet.toCharArray();
        this.size = size;
        this.random = new SecureRandom();
    }

    // -----------------------------------------------------------------------
    // IdGenerator implementation
    // -----------------------------------------------------------------------

    /**
     * Generates a NanoID using a bitmask-and-reject sampling strategy.
     *
     * <p>
     * This avoids modulo bias (where certain characters would appear more
     * often if {@code alphabet.length} is not a power of two) by generating
     * extra bytes and only keeping samples that fall within the valid range.
     * </p>
     *
     * @return a random string of exactly {@link #size} characters
     */
    @Override
    public String generate() {
        // Degenerate case: single-char alphabet — every position must be that
        // character.
        if (alphabet.length == 1) {
            char[] result = new char[size];
            java.util.Arrays.fill(result, alphabet[0]);
            return new String(result);
        }

        // Compute the smallest bitmask that covers the alphabet index range.
        // Integer.numberOfLeadingZeros(alphabet.length - 1) is safe here because length
        // >= 2.
        int mask = (2 << (31 - Integer.numberOfLeadingZeros(alphabet.length - 1))) - 1;
        int step = (int) Math.ceil(1.6 * mask * size / alphabet.length);

        char[] result = new char[size];
        int filled = 0;

        while (filled < size) {
            byte[] bytes = new byte[step];
            random.nextBytes(bytes);
            for (byte b : bytes) {
                int idx = (b & 0xFF) & mask;
                if (idx < alphabet.length) {
                    result[filled++] = alphabet[idx];
                    if (filled == size)
                        break;
                }
            }
        }

        return new String(result);
    }

    @Override
    public String strategyName() {
        return String.format("NanoID (random, size=%d, alphabet=%d chars)", size, alphabet.length);
    }

    public int getSize() {
        return size;
    }

    public char[] getAlphabet() {
        return alphabet.clone();
    }
}
