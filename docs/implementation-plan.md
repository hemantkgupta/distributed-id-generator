# Implementation Plan

## Phase 1: Core Interface and Coordination-Free Generators — Done

- Defined `IdGenerator` interface with `generate()` returning `IdMetadata`.
- Defined `IdMetadata` record carrying the generated ID and strategy metadata.
- Defined `IdGeneratorUtils` with shared helpers: `waitNextMillis`, `toUnsignedHex`.
- Implemented UUID v4, UUID v7 (with monotonic same-ms ordering via rand_a increment), ULID, KSUID, NanoID, MongoDB ObjectID.
- All generators are zero-coordination — no external service dependency.

### Phase 1 Code Map

- `common/src/main/java/.../common/IdGenerator.java` — interface contract
- `common/src/main/java/.../common/IdMetadata.java` — generation result record
- `common/src/main/java/.../common/IdGeneratorUtils.java` — waitNextMillis, hex conversion
- `common/src/main/java/.../common/IdGenerationException.java` — domain exception
- `uuid-generator/src/main/java/.../uuid/UUIDv4Generator.java` — 122-bit CSPRNG
- `uuid-generator/src/main/java/.../uuid/UUIDv7Generator.java` — 48-bit ms timestamp + monotonic rand_a
- `ulid/src/main/java/.../ulid/ULIDGenerator.java` — Crockford Base32 encoding
- `ksuid/src/main/java/.../ksuid/KSUIDGenerator.java` — 4-byte epoch + 16-byte random, Base62
- `nanoid/src/main/java/.../nanoid/NanoIdGenerator.java` — configurable alphabet and length
- `mongodb-objectid/src/main/java/.../objectid/ObjectIdGenerator.java` — 12-byte legacy format

## Phase 2: Snowflake Family — Done

- Implemented Twitter Snowflake with configurable epoch, datacenter/worker ID split.
- Added clock rollback detection with two policies: `FAIL_FAST` and `BOUNDED_WAIT`.
- Implemented HLC-Snowflake with Hybrid Logical Clock for causal ordering across nodes.
- Implemented etcd-backed Snowflake with lease-based node ID assignment.

### Phase 2 Code Map

- `snowflake/src/main/java/.../snowflake/SnowflakeIdGenerator.java` — 64-bit: 41-bit timestamp + 10-bit machine + 12-bit sequence; fail-fast and bounded-wait clock rollback
- `hlc-snowflake/src/main/java/.../hlc/HLCSnowflakeGenerator.java` — Snowflake variant with HLC advancement and remote timestamp merge
- `hlc-snowflake/src/main/java/.../hlc/HybridLogicalClock.java` — HLC state: physical clock + logical counter
- `etcd-snowflake/src/main/java/.../etcd/EtcdSnowflakeIdGenerator.java` — Snowflake backed by etcd lease
- `etcd-snowflake/src/main/java/.../etcd/EtcdNodeIdAssigner.java` — lease-based worker ID assignment via etcd

## Phase 3: Externally-Coordinated Generators — Done

- Implemented Ticket Server with JDBC-backed sequential counter.
- Implemented Leaf-Segment with dual-buffer block allocation and async prefetch at 90% threshold.
- Implemented Spanner TrueTime generator using commit timestamps.

### Phase 3 Code Map

- `ticket-server/src/main/java/.../ticket/TicketServerIdGenerator.java` — JDBC UPDATE...RETURNING atomic increment
- `leaf-segment/src/main/java/.../leafsegment/LeafSegmentIdGenerator.java` — dual AtomicLong buffers, async prefetch, volatile swap
- `leaf-segment/src/main/java/.../leafsegment/IdBlockFetcher.java` — interface for block allocation backend
- `spanner-generator/src/main/java/.../spanner/SpannerTrueTimeIdGenerator.java` — commit-timestamp-based ID

## Phase 4: Tests and Integration — Done

- Unit tests for all 12 strategies covering format, monotonicity, concurrency, and edge cases.
- Integration tests for etcd-Snowflake, Ticket Server, Leaf-Segment, and Spanner using Testcontainers.
- Per-module TECH_SPEC.md and DIAGRAMS.md documentation.

### Phase 4 Code Map

- `common/src/test/java/.../common/IdGeneratorUtilsTest.java`
- `snowflake/src/test/java/.../snowflake/SnowflakeIdGeneratorTest.java`
- `uuid-generator/src/test/java/.../uuid/UUIDGeneratorTest.java`
- `ulid/src/test/java/.../ulid/ULIDGeneratorTest.java`
- `ksuid/src/test/java/.../ksuid/KSUIDGeneratorTest.java`
- `nanoid/src/test/java/.../nanoid/NanoIdGeneratorTest.java`
- `mongodb-objectid/src/test/java/.../objectid/ObjectIdGeneratorTest.java`
- `hlc-snowflake/src/test/java/.../hlc/HLCSnowflakeGeneratorTest.java`
- `etcd-snowflake/src/test/java/.../etcd/EtcdSnowflakeIdGeneratorTest.java`
- `etcd-snowflake/src/test/java/.../etcd/EtcdSnowflakeIdGeneratorIntegrationTest.java`
- `ticket-server/src/test/java/.../ticket/TicketServerIdGeneratorTest.java`
- `ticket-server/src/test/java/.../ticket/TicketServerIdGeneratorIntegrationTest.java`
- `leaf-segment/src/test/java/.../leafsegment/LeafSegmentIdGeneratorTest.java`
- `leaf-segment/src/test/java/.../leafsegment/LeafSegmentIdGeneratorIntegrationTest.java`
- `spanner-generator/src/test/java/.../spanner/SpannerTrueTimeIdGeneratorTest.java`

## Phase 5: Sidecar Service and Kubernetes Deployment — Planned

- Build `id-sidecar-service`: HTTP server exposing one selected strategy.
- Build `id-client-demo`: demo client requesting IDs.
- Package as two-container pod for `kind` local Kubernetes.
- Strategy-specific support: etcd StatefulSet, PostgreSQL for ticket/leaf, Spanner emulator.

## Phase 6: Benchmarks — Planned

- Add `id-bench` module for throughput and latency measurement.
- Compare strategies at 1K, 10K, 100K, 1M IDs/sec.
- Measure B-tree insert performance for sequential vs random strategies.

## Gaps vs Production

| Production Component | Local Implementation | Why |
|---|---|---|
| ZooKeeper for Snowflake machine ID | etcd lease-based assignment | etcd is simpler to run locally |
| NTP discipline / slew-only mode | System clock | Local dev doesn't need NTP hardening |
| Multi-datacenter Snowflake | Single-machine with configurable DC/worker bits | Same bit layout, no real DC topology |
| Spanner TrueTime (GPS/atomic clocks) | Spanner emulator via Testcontainers | Emulator provides same API, no real TrueTime |
| Production Leaf-Segment (MySQL) | PostgreSQL via Testcontainers | Same SQL semantics, PostgreSQL is simpler locally |
| Sidecar HTTP/gRPC service | Not yet implemented (Phase 5) | Library-first approach |
