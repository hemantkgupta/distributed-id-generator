package com.distributed.idgen.hlc;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;
import com.distributed.idgen.common.IdGeneratorUtils;

/**
 * Hybrid Logical Clock (HLC) backed Snowflake 64-bit ID generator.
 *
 * <h2>Motivation</h2>
 * The standard Snowflake algorithm fails catastrophically if the physical clock
 * rolls backwards (e.g., due to an NTP correction). It either throws and halts,
 * or blocks in a busy-wait. An HLC resolves this by <em>never moving
 * backwards</em>:
 * if the physical clock is behind the previously seen time, it simply
 * increments
 * the logical counter, preserving both uniqueness and monotonicity.
 *
 * <h2>64-bit Layout</h2>
 * 
 * <pre>
 * ┌──────┬──────────────────-──────────────────-──┬──────────────────-─┬───────────────────┐
 * │Sign  │      HLC Physical Time (37 bits)        │  Worker ID (10 b) │  Sequence (16 b)  │
 * │(1b)  │  ms relative to custom epoch (2024)     │   0 – 1023        │    0 – 65535      │
 * └──────┴──────────────────-──────────────────────┴───────────────────┴───────────────────┘
 * </pre>
 *
 * <p>
 * Note: Unlike standard Snowflake (12-bit sequence, 10-bit node), this variant
 * uses a <b>16-bit HLC logical counter</b> as the sequence, giving 65,536 IDs
 * per physical millisecond per node — at the cost of a shorter timestamp.
 * </p>
 *
 * <h2>HLC Algorithm</h2>
 * <ol>
 * <li>Read physical wall-clock time ({@code pt}).</li>
 * <li>If {@code pt > hlc.physicalTime}: advance HLC to {@code pt}, reset
 * logical counter.</li>
 * <li>If {@code pt == hlc.physicalTime}: increment logical counter (same
 * millisecond).</li>
 * <li>If {@code pt < hlc.physicalTime}: <em>keep stored physical time</em> and
 * increment
 * logical counter — this is the key clock-drift resilience.</li>
 * <li>If logical counter overflows 65535, spin-wait for the physical clock to
 * advance.</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * All state is guarded by {@code synchronized}.
 */
public class HLCSnowflakeGenerator implements IdGenerator<Long> {

    // -----------------------------------------------------------------------
    // Bit allocations
    // -----------------------------------------------------------------------

    static final int WORKER_ID_BITS = 10; // 1024 workers
    static final int SEQUENCE_BITS = 16; // 65536 IDs per ms per worker
    static final int TIMESTAMP_BITS = 37; // ~4.3 years @ ms precision

    static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 1023
    static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS); // 65535

    static final int SEQUENCE_SHIFT = 0;
    static final int WORKER_ID_SHIFT = SEQUENCE_BITS; // 16
    static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 26

    /** Custom epoch: 2024-01-01T00:00:00Z */
    static final long CUSTOM_EPOCH = 1_704_067_200_000L;

    // -----------------------------------------------------------------------
    // Mutable HLC state
    // -----------------------------------------------------------------------

    private final long workerId;
    private HybridLogicalClock hlc;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Creates an HLC-Snowflake generator with the given worker ID.
     *
     * @param workerId unique worker/machine identifier, must be 0–1023
     */
    public HLCSnowflakeGenerator(long workerId) {
        IdGeneratorUtils.validateNodeId(workerId, MAX_WORKER_ID, "workerId");
        this.workerId = workerId;
        this.hlc = HybridLogicalClock.of(currentEpochMillis());
    }

    public HLCSnowflakeGenerator() {
        this(0);
    }

    // -----------------------------------------------------------------------
    // IdGenerator implementation
    // -----------------------------------------------------------------------

    @Override
    public synchronized Long generate() {
        long pt = currentEpochMillis(); // physical time relative to custom epoch
        hlc = advance(hlc, pt);

        // Use the HLC physical time as the timestamp component
        long timestamp = hlc.physicalTime();
        int sequence = hlc.logicalCount();

        return (timestamp << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    @Override
    public String strategyName() {
        return "HLC-Snowflake (clock-drift resilient 64-bit)";
    }

    // -----------------------------------------------------------------------
    // HLC advancement logic
    // -----------------------------------------------------------------------

    /**
     * Advances the HLC state according to the four-case HLC protocol.
     *
     * @param current current HLC state
     * @param pt      physical wall-clock time (ms since custom epoch)
     * @return new, advanced HLC
     * @throws IdGenerationException if the logical counter would overflow
     */
    HybridLogicalClock advance(HybridLogicalClock current, long pt) {
        if (pt > current.physicalTime()) {
            // Physical clock advanced — update pt, reset logical counter
            return new HybridLogicalClock(pt, 0);
        } else {
            // Clock same or drifted back — keep stored pt, increment logical counter
            int newLogical = current.logicalCount() + 1;
            if (newLogical > HybridLogicalClock.MAX_LOGICAL_COUNT) {
                // Logical counter exhausted — spin-wait for physical clock to advance
                long next = IdGeneratorUtils.waitNextMillis(current.physicalTime());
                return new HybridLogicalClock(next, 0);
            }
            return new HybridLogicalClock(current.physicalTime(), newLogical);
        }
    }

    /**
     * Simulates receiving an HLC timestamp from a remote node (for distributed
     * message ordering). Updates the local HLC to be strictly greater than both
     * the local and remote clocks.
     *
     * @param remoteHlcPacked packed 64-bit HLC from a remote node's message
     */
    public synchronized void receiveRemoteTimestamp(long remoteHlcPacked) {
        HybridLogicalClock remote = HybridLogicalClock.unpack(remoteHlcPacked);
        long pt = currentEpochMillis();

        long maxPhysical = Math.max(Math.max(pt, hlc.physicalTime()), remote.physicalTime());

        int newLogical;
        if (maxPhysical == hlc.physicalTime() && maxPhysical == remote.physicalTime()) {
            newLogical = Math.max(hlc.logicalCount(), remote.logicalCount()) + 1;
        } else if (maxPhysical == hlc.physicalTime()) {
            newLogical = hlc.logicalCount() + 1;
        } else if (maxPhysical == remote.physicalTime()) {
            newLogical = remote.logicalCount() + 1;
        } else {
            newLogical = 0;
        }

        hlc = new HybridLogicalClock(maxPhysical, newLogical);
    }

    // -----------------------------------------------------------------------
    // Accessors and helpers
    // -----------------------------------------------------------------------

    public HybridLogicalClock getCurrentHlc() {
        return hlc;
    }

    public long getWorkerId() {
        return workerId;
    }

    long currentEpochMillis() {
        return System.currentTimeMillis() - CUSTOM_EPOCH;
    }

    /**
     * Parses the components of an HLC-Snowflake ID.
     *
     * @param id a 64-bit ID produced by this generator
     * @return parsed components
     */
    public static HLCComponents parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long sequence = id & MAX_SEQUENCE;
        return new HLCComponents(id, timestamp, workerId, sequence);
    }

    public record HLCComponents(long rawId, long epochMillis, long workerId, long sequence) {
        @Override
        public String toString() {
            return String.format(
                    "HLCComponents{id=%d, epochMs=%d, worker=%d, seq=%d}",
                    rawId, epochMillis, workerId, sequence);
        }
    }
}
