# Distributed ID Generation — Code Companion

This file is the code-first companion to the long-form distributed ID generation writing in the `CSE-Raw` vault. Use it when changing code, syncing the wiki/blog, or handing the topic to Claude or Codex for follow-up work.

## Canonical Companions

The narrative and synthesis live outside this repo:

```text
CSE-Raw/raw-blog/distributed-id-generation.md
CSE-Raw/wiki/implementations/distributed-id-generator.md
CSE-Raw/wiki/concepts/snowflake-id.md
CSE-Raw/wiki/concepts/uuid-v7-ulid.md
CSE-Raw/wiki/patterns/leaf-segment.md
CSE-Raw/wiki/concepts/truetime.md
```

This repo should stay focused on executable behavior, module-level technical notes, and code examples.

## Current Truth In Code

- `snowflake` defaults to `ClockRollbackPolicy.FAIL_FAST`; `ClockRollbackPolicy.BOUNDED_WAIT` is supported for small rollback windows.
- `uuid-generator` defaults UUIDv7 to monotonic same-millisecond ordering by incrementing `rand_a`; `new UUIDv7Generator(false)` keeps `rand_a` fully random.
- `common/IdGeneratorUtils.waitNextMillis(long, LongSupplier)` is the shared helper for generators that do not operate in wall-clock milliseconds.
- `hlc-snowflake` uses the epoch-relative wait helper so logical-counter overflow stays in the same clock domain.
- `leaf-segment` defaults to a prefetch threshold of `0.9f`, which means the next block is fetched after `90%` of the current block has been consumed.
- `spanner-generator` is implemented around Cloud Spanner commit timestamps; local verification typically uses the Spanner emulator via Testcontainers.

## Topic-To-Module Map

| Topic | Primary Module(s) | Key File(s) |
|---|---|---|
| Ticket server | `ticket-server` | `ticket-server/src/main/java/com/distributed/idgen/ticket/TicketServerIdGenerator.java` |
| Snowflake | `snowflake` | `snowflake/src/main/java/com/distributed/idgen/snowflake/SnowflakeIdGenerator.java` |
| HLC-backed Snowflake | `hlc-snowflake` | `hlc-snowflake/src/main/java/com/distributed/idgen/hlc/HLCSnowflakeGenerator.java` |
| etcd-backed worker assignment | `etcd-snowflake` | `etcd-snowflake/src/main/java/com/distributed/idgen/etcd/EtcdNodeIdAssigner.java` |
| UUID v4 / UUID v7 | `uuid-generator` | `uuid-generator/src/main/java/com/distributed/idgen/uuid/UUIDv4Generator.java`, `uuid-generator/src/main/java/com/distributed/idgen/uuid/UUIDv7Generator.java` |
| ULID | `ulid` | `ulid/src/main/java/com/distributed/idgen/ulid/ULIDGenerator.java` |
| KSUID | `ksuid` | `ksuid/src/main/java/com/distributed/idgen/ksuid/KSUIDGenerator.java` |
| NanoID | `nanoid` | `nanoid/src/main/java/com/distributed/idgen/nanoid/NanoIdGenerator.java` |
| MongoDB ObjectID | `mongodb-objectid` | `mongodb-objectid/src/main/java/com/distributed/idgen/objectid/ObjectIdGenerator.java` |
| Leaf-Segment | `leaf-segment` | `leaf-segment/src/main/java/com/distributed/idgen/leafsegment/LeafSegmentIdGenerator.java` |
| Shared validation/wait helpers | `common` | `common/src/main/java/com/distributed/idgen/common/IdGeneratorUtils.java` |
| Spanner / TrueTime | `spanner-generator` | `spanner-generator/TECH_SPEC.md`, `spanner-generator/DIAGRAMS.md` |

## Sync Contract

When code changes behavior:

1. Update the affected module `TECH_SPEC.md`.
2. Update `README.md` if the project matrix, guarantees, or module status changed.
3. Update this file if the cross-topic truth changed.
4. Update the wiki implementation page in `CSE-Raw/wiki/implementations/distributed-id-generator.md`.
5. Update the relevant concept or pattern pages in `CSE-Raw/wiki/`.

When the wiki/blog changes a technical claim:

1. Either implement the change here with tests.
2. Or document the gap explicitly in the wiki implementation page and this file.
3. Do not leave silent drift between prose and code.

## Review Checklist

- Did a change affect ordering, uniqueness, rollback handling, or block-allocation semantics?
- Did a change introduce or remove an operational dependency?
- Did a change alter the default behavior versus an optional policy?
- Did the relevant tests change with the behavior?
- Does the companion wiki/blog still describe the exact default, not just a generic algorithm?
