# snowflake/TECH_SPEC.md

## Overview
The `snowflake` module implements Twitter's standard 64-bit numerical ID generator. It is the gold standard for high-throughput, microsecond-resolution, scalable environments where relational database primary keys demand numeric sequential locality.

## 64-bit Structure (Bit Layout)

A standard Snowflake ID occupies exactly 64 bits (a Java `Long`):

```text
 63        22 21      17 16     12 11         0
 ┌──────────┬──────────┬─────────┬────────────┐
 │  Sign(1) │ Time(41) │ DC(5)   │  Seq(12)   │
 │  always 0│ ms epoch │ Worker(5)│  0-4095    │
 └──────────┴──────────┴─────────┴────────────┘
```

1. **Sign Bit (1 bit)**: Always `0` to ensure the generated IDs are positive integers.
2. **Timestamp (41 bits)**: Captures the number of milliseconds elapsed since a **custom epoch** (`2024-01-01T00:00:00Z`). Allows the generator to survive for `~69.7` years.
3. **Datacenter ID (5 bits)**: Allows up to 32 datacenters.
4. **Worker ID (5 bits)**: Allows up to 32 machines/processes per datacenter (Combined: up to 1024 unique instances across the cluster).
5. **Sequence (12 bits)**: A monotonically increasing counter initialized at `0` every new millisecond. Capable of generating `4096` unique IDs per millisecond per node.

## Key Characteristics
- **Type**: 64-bit Integer (`Long` in Java)
- **Monotonicity**: Yes (Time-Ordered locally)
- **Database B-Tree Friendly**: Highly friendly; IDs sort chronologically and insertions happen sequentially at page boundaries.

## Throughput
- Max `4096 IDs / millisecond / worker`
- **Cluster Max**: `4,194,304 IDs / millisecond` globally across all 1024 nodes.

## Trade-offs & Challenges
1. **Clock Dependency (The NTP Problem)**:
   If the physical machine clock travels backward (via NTP re-sync), the algorithm risks generating duplicate sequence numbers for past milliseconds. 
   **Resolution**: The `snowflake` implementation caches `lastTimestamp`. If current time < `lastTimestamp`, it violently throws an `IdGenerationException`, requiring the application to either handle the outage or crash. (See `hlc-snowflake` for a drift-resilient alternative).
2. **Worker Coordinates**:
   The operator must guarantee that no two instances boot with the exact same `(datacenterId, workerId)` combination, otherwise, collisions are mathematically inevitable. (See `etcd-snowflake` for an automated assignment alternative).
