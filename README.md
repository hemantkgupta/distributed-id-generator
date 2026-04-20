# Distributed ID Generator

A Java 17 Gradle multi-module project implementing 12 distributed ID strategies across 11 algorithm modules.

The repository is library-first today. The planned Kubernetes deployment shape is a two-container pod:
- `id-client-demo`: a demo client that requests IDs.
- `id-sidecar-service`: a sidecar exposing one selected ID strategy over HTTP or gRPC.

See [docs/kind-deployment-plan.md](docs/kind-deployment-plan.md) for the `kind`-based local deployment plan for macOS.

## Project Structure

```text
distributed-id-generator/
├── common/
├── snowflake/
├── uuid-generator/        # UUIDv4 + UUIDv7
├── ulid/
├── ksuid/
├── nanoid/
├── hlc-snowflake/
├── ticket-server/
├── mongodb-objectid/
├── etcd-snowflake/
├── leaf-segment/
└── spanner-generator/     # Cloud Spanner / TrueTime string IDs
```

## Strategy Coverage

| Module | Generator(s) | Output Type | Time-Ordered | External Coordination | Status |
|---|---|---|---|---|---|
| `uuid-generator` | UUIDv4 | `String` | No | None | Implemented |
| `uuid-generator` | UUIDv7 | `String` | Yes | None | Implemented |
| `ulid` | ULID | `String` | Yes | None | Implemented |
| `ksuid` | KSUID | `String` | Yes | None | Implemented |
| `nanoid` | NanoID | `String` | No | None | Implemented |
| `mongodb-objectid` | MongoDB ObjectID | `String` | Yes | None | Implemented |
| `snowflake` | Twitter Snowflake | `Long` | Yes | Unique datacenter and worker IDs | Implemented |
| `hlc-snowflake` | HLC-Snowflake | `Long` | Yes | Unique worker ID | Implemented |
| `etcd-snowflake` | ETCD-backed Snowflake | `Long` | Yes | etcd lease-backed node assignment | Implemented |
| `ticket-server` | Ticket Server | `Long` | Yes | Relational database | Implemented |
| `leaf-segment` | Leaf Segment | `Long` | Yes | Relational database | Implemented |
| `spanner-generator` | Spanner commit-timestamp ID | `String` | Yes | Cloud Spanner / TrueTime | Implemented |

Behavior notes:
- `SnowflakeIdGenerator` defaults to fail-fast on backward clock movement and also supports bounded-wait recovery for small drifts.
- `UUIDv7Generator` defaults to monotonic same-millisecond ordering by incrementing `rand_a`; pass `false` to opt into fully random `rand_a`.
- `LeafSegmentIdGenerator` starts async prefetch after `90%` of the active block has been consumed.
- `SpannerTrueTimeIdGenerator` uses Cloud Spanner commit timestamps and is typically verified against the Spanner emulator.

## Build And Test

```bash
./gradlew clean test
./gradlew build
```

Module-specific test examples:

```bash
./gradlew :snowflake:test
./gradlew :etcd-snowflake:test
./gradlew :leaf-segment:test
```

Notes:
- Java 17+ is required.
- The Gradle wrapper downloads Gradle automatically.
- Live integration tests use Testcontainers.
- The etcd, PostgreSQL, and Spanner emulator integration tests run when Docker is available and are skipped automatically otherwise.

## Test Coverage

All implemented generator modules have unit coverage. The stateful strategies also have live integration coverage.

| Module | Coverage Type | Main Scenarios |
|---|---|---|
| `common` | Unit | Validation and shared utility helpers |
| `snowflake` | Unit | Bit layout, monotonicity, concurrency, fail-fast rollback, bounded-wait recovery |
| `uuid-generator` | Unit | UUIDv4 format, UUIDv7 format, monotonic same-ms ordering, rollback pinning |
| `ulid` | Unit | Encoding, lexicographic ordering, monotonicity |
| `ksuid` | Unit | Base62 encoding, format, ordering |
| `nanoid` | Unit | Default and custom alphabets, construction guards |
| `mongodb-objectid` | Unit | Format and uniqueness |
| `hlc-snowflake` | Unit | HLC advancement, remote timestamp merge, concurrency |
| `etcd-snowflake` | Unit + Integration | Mocked assignment logic plus live etcd-backed node assignment and ID generation |
| `ticket-server` | Unit + Integration | Mocked JDBC behavior plus live PostgreSQL-backed sequential and concurrent generation |
| `leaf-segment` | Unit + Integration | Dual-buffer correctness plus live PostgreSQL-backed range allocation |
| `spanner-generator` | Unit + Integration | ID formatting/parsing plus Spanner emulator-backed commit timestamp generation |

## Documentation Map

Each algorithm module has a technical spec and a diagrams document.

| Module | Technical Spec | Diagrams |
|---|---|---|
| `common` | [common/TECH_SPEC.md](common/TECH_SPEC.md) | [common/DIAGRAMS.md](common/DIAGRAMS.md) |
| `snowflake` | [snowflake/TECH_SPEC.md](snowflake/TECH_SPEC.md) | [snowflake/DIAGRAMS.md](snowflake/DIAGRAMS.md) |
| `uuid-generator` | [uuid-generator/TECH_SPEC.md](uuid-generator/TECH_SPEC.md) | [uuid-generator/DIAGRAMS.md](uuid-generator/DIAGRAMS.md) |
| `ulid` | [ulid/TECH_SPEC.md](ulid/TECH_SPEC.md) | [ulid/DIAGRAMS.md](ulid/DIAGRAMS.md) |
| `ksuid` | [ksuid/TECH_SPEC.md](ksuid/TECH_SPEC.md) | [ksuid/DIAGRAMS.md](ksuid/DIAGRAMS.md) |
| `nanoid` | [nanoid/TECH_SPEC.md](nanoid/TECH_SPEC.md) | [nanoid/DIAGRAMS.md](nanoid/DIAGRAMS.md) |
| `hlc-snowflake` | [hlc-snowflake/TECH_SPEC.md](hlc-snowflake/TECH_SPEC.md) | [hlc-snowflake/DIAGRAMS.md](hlc-snowflake/DIAGRAMS.md) |
| `ticket-server` | [ticket-server/TECH_SPEC.md](ticket-server/TECH_SPEC.md) | [ticket-server/DIAGRAMS.md](ticket-server/DIAGRAMS.md) |
| `mongodb-objectid` | [mongodb-objectid/TECH_SPEC.md](mongodb-objectid/TECH_SPEC.md) | [mongodb-objectid/DIAGRAMS.md](mongodb-objectid/DIAGRAMS.md) |
| `etcd-snowflake` | [etcd-snowflake/TECH_SPEC.md](etcd-snowflake/TECH_SPEC.md) | [etcd-snowflake/DIAGRAMS.md](etcd-snowflake/DIAGRAMS.md) |
| `leaf-segment` | [leaf-segment/TECH_SPEC.md](leaf-segment/TECH_SPEC.md) | [leaf-segment/DIAGRAMS.md](leaf-segment/DIAGRAMS.md) |
| `spanner-generator` | [spanner-generator/TECH_SPEC.md](spanner-generator/TECH_SPEC.md) | [spanner-generator/DIAGRAMS.md](spanner-generator/DIAGRAMS.md) |

Cross-repo sync:
- [docs/distributed-id-generation-code-companion.md](docs/distributed-id-generation-code-companion.md) — code-first companion and sync checklist for the long-form wiki/blog topic

## Kubernetes Target

The target end-to-end Kubernetes shape is:

1. One `id-client-demo` container.
2. One `id-sidecar-service` container in the same pod.
3. Optional support services depending on the strategy:
   - None for `uuid-generator`, `ulid`, `ksuid`, `nanoid`, and `mongodb-objectid`.
   - Unique worker identity for `snowflake` and `hlc-snowflake`.
   - etcd for `etcd-snowflake`.
   - PostgreSQL for `ticket-server` and `leaf-segment`.
   - Cloud Spanner or the Spanner emulator for `spanner-generator` (not `kind`-native).

For local macOS testing, the target cluster is `kind`.

## Reference Reading

- [Distributed ID Generation_ A Foundational Guide.md](Distributed%20ID%20Generation_%20A%20Foundational%20Guide.md)
- [Planet-Scale Distributed ID Generation.md](Planet-Scale%20Distributed%20ID%20Generation.md)
