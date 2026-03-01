# common/TECH_SPEC.md

## Overview
The `common` module acts as the foundation for the `distributed-id-generator` project. It contains the core interfaces, robust utilities, and shared exceptions used uniformly by all generation strategies.

## Core Contract

The `IdGenerator<T>` interface establishes three unshakeable guarantees for every generator in this repository:

1. **Uniqueness**: No two invocations of `generate()` within the same instance will yield the same identifier.
2. **Thread Safety**: Every implementation is internally synchronized or relies on lock-free primitives (like `AtomicInteger`). Callers never need to lock their own threads.
3. **Non-Null**: The `generate()` method is guaranteed to never return null.

```java
public interface IdGenerator<T> {
    T generate();
    String strategyName();
}
```

## Shared Utilities
The module leverages an `IdGeneratorUtils` class containing zero-dependency static helpers:
- `validateNodeId(long id, long maxValue, String label)`: Throws descriptive `IllegalArgumentException`s if coordinates (like `datacenterId`) exceed their allocated bit-width (e.g., > 31 for 5 bits).
- `waitNextMillis(long lastTimestamp)`: A low-latency busy-spin sleep loop used by timestamp-based generators to stall the calling thread until the physical clock advances beyond `lastTimestamp`.
- `toHexString(byte[] bytes)`: Fast conversion for byte arrays outputting canonical, low-cased hex formats used heavily by MongoDB ObjectIDs and UUID manipulations.

## The IdGenerationException
Any unrecoverable failure during the execution of `generate()` throws a runtime `IdGenerationException`. 

Common triggers across the project include:
- **Physical Clock Drift**: If NTP violently re-syncs backwards, Snowflake generators will catch the drift and explicitly crash rather than risk ID collision.
- **Resource Exhaustion**: If the ETCD generator hits 1024 active workers with an inability to claim an ID.
- **Network Failures**: If a ticket-generator or ETCD coordinator drops its network connection and cannot reach quorum.
