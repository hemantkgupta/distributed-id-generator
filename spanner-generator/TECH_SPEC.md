# spanner-generator/TECH_SPEC.md

## Overview
The `spanner-generator` module utilizes Google Cloud Spanner's **TrueTime** infrastructure to create strictly accurate, globally monotonic logical timestamps that act as IDs. Unlike sequence generators that suffer from database "hot-spotting", or memory generators that lose track of time on NTP drift, this module leans entirely on Spanner's hardware-backed atomic clocks and GPS receivers to order events across the planet perfectly.

## Method & Execution
In Google Spanner, standard monotonically increasing integers (like those from `ticket-server`) cause all writes to cluster on a single database split (server node). This instantly kills performance. Therefore, Spanner explicitly recommends using UUIDs or Bit-Reversed sequences.

However, if sequential chronological sorting is a strictly hard requirement, Spanner provides the `PENDING_COMMIT_TIMESTAMP()` feature.
1. The `SpannerTrueTimeIdGenerator` issues a blind insert (using `writeAtLeastOnce` API) into an append-only table.
2. The mutation explicitly asks Spanner to evaluate the `COMMIT_TIMESTAMP`.
3. Spanner's coordinator node queries the TrueTime API, waits out the time uncertainty window (a few milliseconds), and commits.
4. The client receives back an incredibly accurate `com.google.cloud.Timestamp`.
5. The Timestamp is concatenated with a machine/random suffix (to prevent dual-commit collisions on the exact microsecond) and returned as a String ID (e.g. `2024-10-18T10:15:30.123456000Z-nodeA`).

## Trade-offs
- **Perfect Global Ordering**: Relies on TrueTime, meaning an event generated in Tokyo is perfectly time-ordered against an event in New York without any centralized network coordination bottleneck.
- **Latency Penalty**: TrueTime forces the Spanner coordinator to sleep out the maximum clock uncertainty interval (usually 1-4ms) before committing. Every single ID generation suffers this exact latency penalty minimum.
- **High Cloud Costs**: Spanner is an expensive enterprise-grade database. Running a sheer append-only log just for ID tracking requires strict TTL (Time To Live) row deletion policies so storage costs don't spiral out of control.
- **String/UUID format**: Returns a Variable String format rather than a standard 64-bit Long due to the nature of RFC 3339 timestamps and node collision suffixes.
