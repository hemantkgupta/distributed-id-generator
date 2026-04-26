# ADR 0001: Twelve-Strategy Taxonomy

## Status

Accepted for initial implementation.

## Decision

Implement 12 distinct ID generation strategies as independent Gradle modules sharing a common `IdGenerator` interface. Cover the full design space from coordination-free random IDs (UUID v4, NanoID) through timestamp-structured IDs (Snowflake, UUID v7, ULID, KSUID, MongoDB ObjectID, HLC-Snowflake) to externally-coordinated IDs (Ticket Server, Leaf-Segment, etcd-backed Snowflake, Spanner TrueTime).

## Context

The project serves as a companion to a system design blog on distributed ID generation. Three scoping alternatives were considered:

1. **Snowflake-only deep dive**: Implement one strategy (Twitter Snowflake) with extensive depth — clock drift handling, bit layout variants, machine ID assignment.
2. **Three canonical strategies**: Implement Snowflake + UUID v7 + Leaf-Segment as the three main production choices.
3. **Full taxonomy**: Implement all 12 strategies that appear in production systems and interview discussions.

## Rationale

The full taxonomy was chosen for three reasons:

**1. The blog covers the entire design space.** The blog progresses from "approaches that don't scale" through Snowflake variants, Leaf-Segment, UUID v7/ULID, and the security angle. Each strategy mentioned in the blog should have a working implementation the reader can inspect.

**2. The strategies are small.** Unlike a KV store (where one module like `kv-repair` is 3,000+ LOC), each ID generator is 50–200 lines. The marginal cost of adding a strategy is low. The educational value of comparing implementations side-by-side is high.

**3. The `IdGenerator` interface unifies all strategies.** Every strategy implements the same contract — `generate()` → `IdMetadata`. This makes the strategies directly comparable and allows the planned sidecar service to swap strategies via configuration.

## Strategy Selection

| Strategy | Why included |
|---|---|
| UUID v4 | Baseline — coordination-free, no ordering, maximum opacity |
| UUID v7 | The modern default — RFC 9562, native PostgreSQL 18, sequential inserts |
| ULID | String-native lexicographic sorting alternative to UUID v7 |
| KSUID | Segment's approach — 4-byte timestamp + 16-byte random, Base62 |
| NanoID | Compact random string IDs — URL-friendly, custom alphabets |
| MongoDB ObjectID | Legacy 12-byte format — timestamp + machine + PID + counter |
| Snowflake | The canonical 64-bit solution — timestamp + machine + sequence |
| HLC-Snowflake | Snowflake variant using Hybrid Logical Clock for causal ordering |
| etcd-Snowflake | Snowflake with etcd lease-backed machine ID assignment |
| Ticket Server | Flickr's 2010 pattern — centralized DB counter |
| Leaf-Segment | Meituan's 2017 pattern — dual-buffer block allocation |
| Spanner TrueTime | Google's commit-timestamp approach — TrueTime GPS/atomic clocks |

## Consequences

- Every blog claim about a strategy maps to a module with source and tests.
- The shared `IdGenerator` interface enables the planned sidecar HTTP service.
- Strategy-specific docs (TECH_SPEC.md, DIAGRAMS.md) per module document behavior and edge cases.
- Integration tests (etcd, PostgreSQL, Spanner emulator) verify coordinated strategies against real backends.
