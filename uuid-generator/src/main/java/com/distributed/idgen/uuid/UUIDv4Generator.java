package com.distributed.idgen.uuid;

import com.distributed.idgen.common.IdGenerator;

import java.util.UUID;

/**
 * UUID Version 4 (random) ID generator.
 *
 * <h2>Structure</h2>
 * 
 * <pre>
 * 128-bit: [60-bit random][4-bit version=4][62-bit random][2-bit variant]
 * </pre>
 *
 * <h2>Trade-offs</h2>
 * <ul>
 * <li>✅ Zero coordination required — fully decentralised</li>
 * <li>✅ Cryptographically random — nearly zero probability of collision</li>
 * <li>❌ No time ordering — causes severe B-Tree index fragmentation in
 * databases</li>
 * <li>❌ 128-bit size — double the memory of a 64-bit integer</li>
 * </ul>
 *
 * <p>
 * Thread-safe: {@link UUID#randomUUID()} uses a thread-local
 * {@code SecureRandom}
 * internally on modern JVMs.
 * </p>
 */
public class UUIDv4Generator implements IdGenerator<String> {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String strategyName() {
        return "UUIDv4 (Random 128-bit)";
    }
}
