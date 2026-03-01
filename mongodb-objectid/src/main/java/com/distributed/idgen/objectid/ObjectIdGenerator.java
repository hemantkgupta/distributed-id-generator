package com.distributed.idgen.objectid;

import com.distributed.idgen.common.IdGenerator;
import com.distributed.idgen.common.IdGeneratorUtils;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MongoDB ObjectID generator.
 *
 * <h2>Structure (12 bytes = 24 hex characters)</h2>
 * 
 * <pre>
 * ┌──────────────────────────────┬──────────────────────────┬────────────────────────────┐
 * │       Timestamp (4 bytes)    │     Random (5 bytes)     │     Counter (3 bytes)      │
 * │  Seconds since Unix epoch    │   Per-process random     │   Incrementing counter     │
 * └──────────────────────────────┴──────────────────────────┴────────────────────────────┘
 * </pre>
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>✅ 12-byte size — smaller than UUID (16 bytes) but larger than Snowflake (8 bytes)</li>
 * <li>✅ Lexicographically sortable by time (on a 1-second resolution)</li>
 * <li>✅ No central coordination required</li>
 * <li>✅ 24-character hex string encoding is highly standard and URL-safe</li>
 * <li>❌ 1-second resolution means many IDs can share the same timestamp prefix</li>
 * </ul>
 */
public class ObjectIdGenerator implements IdGenerator<String> {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 5-byte random value, initialized once per JVM/ClassLoader
    private static final byte[] RANDOM_VALUE = new byte[5];
    static {
        SECURE_RANDOM.nextBytes(RANDOM_VALUE);
    }

    // 3-byte counter, initialized to a random value to avoid collisions on startup
    private final AtomicInteger counter = new AtomicInteger(SECURE_RANDOM.nextInt());

    @Override
    public String generate() {
        int timestamp = (int) (System.currentTimeMillis() / 1000);
        int currentCounter = counter.getAndIncrement();

        // Build the 12-byte array
        byte[] bytes = new byte[12];

        // 4 bytes: Timestamp (big-endian)
        bytes[0] = (byte) (timestamp >> 24);
        bytes[1] = (byte) (timestamp >> 16);
        bytes[2] = (byte) (timestamp >> 8);
        bytes[3] = (byte) (timestamp);

        // 5 bytes: Random value
        System.arraycopy(RANDOM_VALUE, 0, bytes, 4, 5);

        // 3 bytes: Counter (big-endian)
        bytes[9] = (byte) (currentCounter >> 16);
        bytes[10] = (byte) (currentCounter >> 8);
        bytes[11] = (byte) (currentCounter);

        return IdGeneratorUtils.toHexString(bytes);
    }

    @Override
    public String strategyName() {
        return "MongoDB ObjectID (12-byte Hex String)";
    }
}
