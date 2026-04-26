# Research Checkpoint

## Direction

Build a comprehensive taxonomy of distributed ID generation strategies, covering the full design space from coordination-free random IDs through timestamp-structured IDs to externally-coordinated sequential IDs. Each strategy is a separate Gradle module implementing a shared `IdGenerator` interface, with unit and integration tests.

Raft-backed distributed counters and global transaction ordering (Spanner-style) are treated as specific strategies within the taxonomy, not as the primary design family.

## Foundation

- Five ID properties (not simultaneously achievable): uniqueness, time-sortability, opacity, coordination-free, compactness.
- Three scaling families: coordination-free (UUID v4, NanoID), timestamp-structured (Snowflake, UUID v7, ULID), coordinated (Ticket Server, Leaf-Segment).
- Snowflake bit layout: 1-bit sign + 41-bit timestamp + 10-bit machine + 12-bit sequence = 64 bits.
- UUID v7 structure: 48-bit ms timestamp + 4-bit version + 12-bit rand_a + 2-bit variant + 62-bit rand_b = 128 bits.
- Leaf-Segment mechanism: atomic DB block claim → serve from memory → dual-buffer async prefetch.

## Going Deeper

- Clock rollback is a policy question: fail-fast (Twitter), bounded-wait (Meituan), or monotonic clock (eliminates the problem class at the cost of wall-clock accuracy).
- Snowflake IDs leak three channels: creation timestamp, machine count, per-ms throughput. UUID v7 leaks timestamp only. UUID v4 leaks nothing.
- Sequence overflow at 4,096/ms creates sawtooth latency; pre-generation pool absorbs bursts at the cost of timestamp staleness.
- Leaf-Segment dual-buffer eliminates boundary latency spikes: async prefetch at 90% consumed, instant switchover.
- HLC (Hybrid Logical Clock) provides causal ordering across nodes without GPS/atomic clocks — useful when Snowflake's per-node timestamps aren't causally related.
- etcd lease-based node ID assignment replaces ZooKeeper ephemeral nodes — simpler operational model for Kubernetes environments.

## At Scale

- B-tree insert performance: sequential IDs (Snowflake, UUID v7) → rightmost leaf page, hot in buffer pool. Random IDs (UUID v4) → random page, cold reads, page splits. Measurable above 200M rows.
- Leaf-Segment DB outage tolerance: step = peak_QPS × 600 → 10–20 minutes of operation without DB contact.
- Multi-datacenter Snowflake: 5-bit DC + 5-bit machine = 32 DCs × 32 machines. IDs encode which DC generated them.
- Spanner TrueTime: GPS + atomic clocks provide globally consistent timestamps. Commit timestamp ordering eliminates clock drift by construction. Not available outside Google Cloud.

## Recommended Defaults

- Greenfield PostgreSQL, < 50M rows: UUID v4 (zero coordination, index cost not material).
- Greenfield PostgreSQL, > 50M rows: UUID v7 (sequential inserts, coordination-free, RFC 9562).
- 64-bit required, high throughput: Snowflake (4M+/sec per node, BIGINT storage, decodable metadata).
- Simple infra, no time structure needed: Leaf-Segment (~50K QPS/node, 10–20 min DB tolerance).
- Public URLs with sensitive resources: Snowflake internal + UUID v4 external (dual-ID pattern).
- Security tokens: UUID v4 or CSPRNG. Always. Non-negotiable.

## Wiki References

- `concepts/snowflake-id` — bit layout, clock drift, sequence overflow
- `concepts/uuid-v7-ulid` — RFC 9562, monotonic ordering, PostgreSQL 18
- `patterns/leaf-segment` — dual-buffer, async prefetch, DB outage tolerance
- `concepts/truetime` — GPS/atomic clocks, Spanner commit timestamps
- `tradeoffs/uuid-vs-snowflake-vs-db-autoincrement` — decision framework
- `interviews/design-distributed-id-generator` — interview walkthrough
