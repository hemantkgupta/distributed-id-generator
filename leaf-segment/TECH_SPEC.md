# leaf-segment/TECH_SPEC.md

## Overview
The `leaf-segment` module addresses the fundamental architectural flaw of the `ticket-server` approach by decoupling the database from every single ID request. Inspired by Meituan Leaf, it uses a **Dual-Buffer Segment** strategy. Instead of requesting one ID via DB transaction, the application requests a block of IDs (e.g., 1000 IDs per block). 

## Method & Execution
1. The `LeafSegmentIdGenerator` initiates by drawing a `Segment` representing an ID block boundary `[min, max]`.
2. Memory-bound threads iterate through this segment atomically via `AtomicLong` without holding locks or making network calls.
3. When the primary segment reaches a configurable exhaustion threshold (e.g., `90%` consumed), a background asynchronous thread optimistically reaches out to the Database via `IdBlockFetcher` to pre-load a secondary buffer (the `nextSegment`).
4. When the primary buffer perfectly exhausts, the engine synchronously swaps pointers to the preloaded `nextSegment`, essentially hiding network latencies from the generating caller thread.

## Trade-offs
- **High Throughput**: ID generation is strictly lock-free CPU memory-bound atomic increments, yielding extreme throughput (10M+ IDs per second per node).
- **Graceful DB Pressure**: The DB is only touched once per 1000 sequence allocations, dropping QPS load on the Master Node by 99.9%.
- **Numeric Sequence Holes**: Unlike `ticket-server`, if a node crashes mid-segment, the unallocated IDs in memory are lost forever, leaving permanent numeric "holes" in the sequence. It guarantees monotonicity, but not perfect unbroken continuity.
- **Clock Drift Immunity**: Relies purely on the DB max boundary, unaffected by System NTP clock rollbacks (unlike Snowflake algorithms).

## Implementation Specifics
The system handles single-node concurrent calls flawlessly through `ReentrantLock` around buffer swapping and atomic increments. The database `IdBlockFetcher` implementation is expected to use lock-free increment commands like:
`UPDATE leaf_alloc SET max_id = max_id + step WHERE biz_tag = ?`
followed by `SELECT max_id`.
