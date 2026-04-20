package com.distributed.idgen.snowflake;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;
import com.distributed.idgen.common.IdGeneratorUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Twitter Snowflake 64-bit distributed ID generator.
 *
 * <h2>64-bit Layout</h2>
 * 
 * <pre>
 * ┌──────────┬────────────────────────────────────────────┬──────────────────────┬──────────────────┐
 * │ Sign bit │          Timestamp (41 bits)               │  Machine ID (10 bits)│ Sequence (12 bits)│
 * │  1 bit   │    milliseconds since custom epoch         │  dc(5) + worker(5)   │  0 – 4095        │
 * └──────────┴────────────────────────────────────────────┴──────────────────────┴──────────────────┘
 * </pre>
 *
 * <h2>Capacity</h2>
 * <ul>
 * <li>Up to <b>1,024 unique nodes</b> (32 DCs × 32 workers)</li>
 * <li>Up to <b>4,096 IDs per millisecond</b> per node</li>
 * <li>Remains valid for <b>~69.7 years</b> from the custom epoch</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All state mutation is done inside a {@code synchronized} block, making this
 * class
 * safe for use by multiple threads sharing one generator instance.
 */
public class SnowflakeIdGenerator implements IdGenerator<Long> {

    /**
     * Runtime handling strategy when the wall clock moves backwards.
     */
    public enum ClockRollbackPolicy {
        FAIL_FAST,
        BOUNDED_WAIT
    }

    // -----------------------------------------------------------------------
    // Bit allocations
    // -----------------------------------------------------------------------

    /**
     * Number of bits allocated to the worker (machine) ID within the node segment.
     */
    static final int WORKER_ID_BITS = 5;

    /** Number of bits allocated to the datacenter ID within the node segment. */
    static final int DATACENTER_ID_BITS = 5;

    /** Number of bits allocated to the per-millisecond sequence counter. */
    static final int SEQUENCE_BITS = 12;

    // -----------------------------------------------------------------------
    // Maximum values derived from bit allocations
    // -----------------------------------------------------------------------

    /** Maximum worker ID: 2^5 - 1 = 31 */
    static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS); // 31

    /** Maximum datacenter ID: 2^5 - 1 = 31 */
    static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS); // 31

    /** Maximum sequence per millisecond: 2^12 - 1 = 4095 */
    static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS); // 4095

    // -----------------------------------------------------------------------
    // Bit-shift offsets
    // -----------------------------------------------------------------------

    /** Worker ID occupies bits 12–16 (shift sequence bits left). */
    static final int WORKER_ID_SHIFT = SEQUENCE_BITS; // 12

    /** Datacenter ID occupies bits 17–21 (shift worker + sequence bits left). */
    static final int DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 17

    /** Timestamp occupies bits 22–62 (shift all node + sequence bits left). */
    static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS; // 22

    // -----------------------------------------------------------------------
    // Custom epoch — January 1, 2024, 00:00:00 UTC in milliseconds
    // Using a recent epoch maximises the usable timestamp range (~69.7 years).
    // -----------------------------------------------------------------------

    static final long CUSTOM_EPOCH = 1704067200000L; // 2024-01-01T00:00:00Z

    // -----------------------------------------------------------------------
    // Mutable state (all protected by synchronized
    // -----------------------------------------------------------------------

    private final long workerId;
    private final long datacenterId;
    private final ClockRollbackPolicy rollbackPolicy;
    private final long maxBackwardMillis;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Creates a new Snowflake generator with explicit datacenter and worker IDs.
     *
     * @param datacenterId datacenter identifier, must be 0–31
     * @param workerId     worker/machine identifier, must be 0–31
     */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        this(datacenterId, workerId, ClockRollbackPolicy.FAIL_FAST, 0L);
    }

    /**
     * Creates a new Snowflake generator with an explicit rollback-handling policy.
     *
     * @param datacenterId      datacenter identifier, must be 0–31
     * @param workerId          worker/machine identifier, must be 0–31
     * @param rollbackPolicy    how to react when the wall clock moves backwards
     * @param maxBackwardMillis maximum tolerated rollback when using
     *                          {@link ClockRollbackPolicy#BOUNDED_WAIT}
     */
    public SnowflakeIdGenerator(
            long datacenterId,
            long workerId,
            ClockRollbackPolicy rollbackPolicy,
            long maxBackwardMillis) {
        IdGeneratorUtils.validateNodeId(datacenterId, MAX_DATACENTER_ID, "datacenterId");
        IdGeneratorUtils.validateNodeId(workerId, MAX_WORKER_ID, "workerId");
        this.rollbackPolicy = Objects.requireNonNull(rollbackPolicy, "rollbackPolicy must not be null");
        if (rollbackPolicy == ClockRollbackPolicy.BOUNDED_WAIT && maxBackwardMillis <= 0) {
            throw new IllegalArgumentException("maxBackwardMillis must be > 0 for BOUNDED_WAIT policy");
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
        this.maxBackwardMillis = rollbackPolicy == ClockRollbackPolicy.BOUNDED_WAIT ? maxBackwardMillis : 0L;
    }

    /**
     * Convenience constructor for single-node setups (datacenter=0, worker=0).
     */
    public SnowflakeIdGenerator() {
        this(0, 0);
    }

    // -----------------------------------------------------------------------
    // IdGenerator implementation
    // -----------------------------------------------------------------------

    /**
     * Generates the next unique Snowflake ID.
     *
     * <p>
     * Algorithm:
     * </p>
     * <ol>
     * <li>Read current wall-clock milliseconds relative to the custom epoch.</li>
     * <li>If still in the same millisecond, increment the sequence counter.</li>
     * <li>If the sequence overflows (> 4095), spin-wait for the next
     * millisecond.</li>
     * <li>If the clock went backwards, throw {@link IdGenerationException}.</li>
     * <li>Combine timestamp, datacenter ID, worker ID, and sequence via bitwise
     * OR.</li>
     * </ol>
     *
     * @return a positive 64-bit Snowflake ID
     * @throws IdGenerationException if clock drift is detected
     */
    @Override
    public synchronized Long generate() {
        long currentTimestamp = currentEpochMillis();

        if (currentTimestamp < lastTimestamp) {
            currentTimestamp = handleClockRollback(currentTimestamp);
        }

        if (currentTimestamp == lastTimestamp) {
            // Increment sequence within the same millisecond
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // Sequence exhausted — spin-wait until next millisecond
                currentTimestamp = IdGeneratorUtils.waitNextMillis(lastTimestamp, this::currentEpochMillis);
            }
        } else {
            // New millisecond — reset sequence
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        // Assemble the 64-bit ID via bit-shifts and bitwise OR
        return (currentTimestamp << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    @Override
    public String strategyName() {
        return "Twitter Snowflake (64-bit)";
    }

    // -----------------------------------------------------------------------
    // Accessors (useful for testing and monitoring)
    // -----------------------------------------------------------------------

    public long getWorkerId() {
        return workerId;
    }

    public long getDatacenterId() {
        return datacenterId;
    }

    public ClockRollbackPolicy getRollbackPolicy() {
        return rollbackPolicy;
    }

    public long getMaxBackwardMillis() {
        return maxBackwardMillis;
    }

    public long getLastTimestamp() {
        return lastTimestamp;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    long currentEpochMillis() {
        return System.currentTimeMillis() - CUSTOM_EPOCH;
    }

    long handleClockRollback(long currentTimestamp) {
        long driftMillis = lastTimestamp - currentTimestamp;
        if (rollbackPolicy == ClockRollbackPolicy.FAIL_FAST) {
            throw backwardClockException(driftMillis, null);
        }
        if (driftMillis > maxBackwardMillis) {
            throw backwardClockException(driftMillis,
                    String.format("exceeding bounded-wait limit of %d ms", maxBackwardMillis));
        }
        return waitForClockToCatchUp(lastTimestamp, driftMillis);
    }

    long waitForClockToCatchUp(long targetTimestamp, long driftMillis) {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxBackwardMillis + 1);
        long currentTimestamp = currentEpochMillis();

        while (currentTimestamp < targetTimestamp) {
            if (System.nanoTime() > deadlineNanos) {
                throw backwardClockException(driftMillis,
                        String.format("clock did not recover within %d ms bounded wait", maxBackwardMillis));
            }
            try {
                sleepMillis(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IdGenerationException("Interrupted while waiting for clock to recover", e);
            }
            currentTimestamp = currentEpochMillis();
        }

        return currentTimestamp;
    }

    void sleepMillis(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private IdGenerationException backwardClockException(long driftMillis, String detail) {
        String suffix = detail == null ? "" : " (" + detail + ")";
        return new IdGenerationException(String.format("Clock moved backwards by %d ms%s", driftMillis, suffix));
    }

    /**
     * Parses the components of a Snowflake ID back into its constituent parts.
     * Useful for debugging and observability.
     *
     * @param id a Snowflake ID produced by this generator
     * @return parsed {@link SnowflakeComponents}
     */
    public static SnowflakeComponents parse(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
        long datacenter = (id >> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
        long worker = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long seq = id & MAX_SEQUENCE;
        return new SnowflakeComponents(id, timestamp, datacenter, worker, seq);
    }

    /**
     * Immutable value object holding the decoded parts of a Snowflake ID.
     */
    public record SnowflakeComponents(
            long rawId,
            long epochMillis,
            long datacenterId,
            long workerId,
            long sequence
    ) {
        @Override
        public String toString() {
            return String.format(
                    "SnowflakeComponents{id=%d, epochMs=%d, dc=%d, worker=%d, seq=%d}",
                    rawId, epochMillis, datacenterId, workerId, sequence);
        }
    }
}
