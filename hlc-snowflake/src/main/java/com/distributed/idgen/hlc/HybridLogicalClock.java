package com.distributed.idgen.hlc;

/**
 * Hybrid Logical Clock (HLC) state, encapsulated as an immutable value object.
 *
 * <h2>64-bit Layout</h2>
 * 
 * <pre>
 * ┌─────────────────────────────────────────────┬────────────────────────────────┐
 * │           Physical Time  (48 bits)           │   Logical Counter  (16 bits)  │
 * │           Unix epoch milliseconds            │       0 – 65535               │
 * └─────────────────────────────────────────────┴────────────────────────────────┘
 * </pre>
 *
 * <p>
 * The HLC advances monotonically even when the physical clock drifts backwards
 * (e.g., due to NTP rollback), by holding the stored physical time steady and
 * incrementing only the logical counter.
 * </p>
 *
 * @param physicalTime Unix epoch milliseconds (48 bits used)
 * @param logicalCount Per-millisecond causal counter (16 bits, 0–65535)
 */
public record HybridLogicalClock(long physicalTime,int logicalCount){

/** Maximum value of the 16-bit logical counter. */
public static final int MAX_LOGICAL_COUNT=0xFFFF; // 65535

/**
 * Packs this HLC into a single 64-bit long for embedding in IDs or network
 * messages.
 *
 * @return 64-bit packed representation:
 *         {@code (physicalTime << 16) | logicalCount}
 */
public long pack(){return(physicalTime<<16)|(logicalCount&0xFFFFL);}

/**
 * Unpacks a 64-bit value produced by {@link #pack()} back into an
 * {@code HybridLogicalClock}.
 *
 * @param packed 64-bit HLC value
 * @return reconstructed HLC
 */
public static HybridLogicalClock unpack(long packed){long physicalTime=packed>>>16;int logicalCount=(int)(packed&0xFFFFL);return new HybridLogicalClock(physicalTime,logicalCount);}

/**
 * Convenience factory method starting a new HLC at the given wall-clock time.
 *
 * @param wallClockMs current wall-clock time in ms
 * @return new HLC with logical counter = 0
 */
public static HybridLogicalClock of(long wallClockMs){return new HybridLogicalClock(wallClockMs,0);}

@Override public String toString(){return String.format("HLC{pt=%d, lc=%d, packed=%d}",physicalTime,logicalCount,pack());}}
