# 🆔 Distributed ID Generator — Gradle Multi-Module Project

A **production-quality**, fully self-contained Java 17 Gradle multi-module project that
implements **six distinct distributed ID generation strategies** — from the simplest
UUID to the most sophisticated Hybrid Logical Clock — all written from scratch with no
external ID-generation libraries.

---

## 📦 Project Structure

```
distributed-id-generator/          ← root project
├── common/                        ← Shared interfaces, utilities, exceptions
├── snowflake/                     ← Twitter Snowflake (64-bit integer)
├── uuid-generator/                ← UUIDv4 (random) + UUIDv7 (time-ordered)
├── ulid/                          ← ULID (26-char Crockford Base32)
├── ksuid/                         ← KSUID (27-char Base62)
├── nanoid/                        ← NanoID (configurable random string)
└── hlc-snowflake/                 ← HLC-Snowflake (clock-drift-resilient 64-bit)
```

Each module is fully independent, produces its own JAR, and has its own comprehensive
test suite.

---

## 🏗 Build & Test

```bash
# Run ALL tests across all modules
./gradlew test

# Run tests for a specific module
./gradlew :snowflake:test
./gradlew :hlc-snowflake:test

# Build all JARs
./gradlew build

# Clean everything
./gradlew clean
```

Requires **Java 17+** on the `PATH`. The Gradle wrapper (`gradlew`) downloads
Gradle 8.7 automatically — no installation needed.

---

## 📐 Design Philosophy

All generators implement a single interface from the `common` module:

```java
public interface IdGenerator<T> {
    T generate();          // always non-null, always unique
    String strategyName(); // human-readable label
}
```

Every implementation must guarantee:
- **Uniqueness** — no two calls return the same ID within the same instance.
- **Thread safety** — safe for concurrent access without external synchronisation.
- **Non-null returns** — `generate()` never returns `null`.

---

## 🔬 Module Deep-Dives

### 1. `common` — Shared Foundation

| Class | Purpose |
|---|---|
| `IdGenerator<T>` | Core interface implemented by all generators |
| `IdGenerationException` | Unchecked exception for unrecoverable failures (e.g. clock drift) |
| `IdMetadata` | Immutable record holding human-readable breakdown of an ID |
| `IdGeneratorUtils` | Node ID validation, millisecond spin-wait, byte→hex conversion |

---

### 2. `snowflake` — Twitter Snowflake (64-bit Integer)

The **industry baseline** for high-performance distributed ID generation, originally
engineered by Twitter.

#### Bit Layout
```
 63        22 21      17 16     12 11         0
 ┌──────────┬──────────┬─────────┬────────────┐
 │  Sign(1) │ Time(41) │ DC(5)   │  Seq(12)   │
 │  always 0│ ms epoch │ Worker(5)│  0–4095    │
 └──────────┴──────────┴─────────┴────────────┘
```

| Parameter | Detail |
|---|---|
| **Custom epoch** | `2024-01-01T00:00:00Z` — maximises timestamp range |
| **Lifespan** | ~69.7 years |
| **Max nodes** | 1,024 (32 DCs × 32 workers) |
| **Throughput** | 4,096 IDs/ms/node → **4.19 million IDs/ms** cluster-wide |
| **Type** | `Long` |

#### Clock-Drift Handling
If `currentTime < lastTimestamp`, the generator **throws** `IdGenerationException`. This is
the correct safe behaviour — see `hlc-snowflake` for drift-resilient generation.

#### Key API
```java
SnowflakeIdGenerator gen = new SnowflakeIdGenerator(datacenterId, workerId);
long id = gen.generate();

// Parse any Snowflake ID back into its components
SnowflakeIdGenerator.SnowflakeComponents c = SnowflakeIdGenerator.parse(id);
System.out.println(c.epochMillis() + " / dc=" + c.datacenterId() + " / seq=" + c.sequence());
```

---

### 3. `uuid-generator` — UUIDv4 & UUIDv7

#### UUIDv4 — Random 128-bit
```
Version nibble = 4   Variant = RFC 4122
xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx   (y ∈ {8,9,a,b})
```
- ✅ Zero coordination, fully decentralised
- ❌ **No time ordering** → catastrophic B-Tree index fragmentation

#### UUIDv7 — Time-Ordered 128-bit (RFC 9562)
```
Bits 0–47   : 48-bit Unix ms timestamp  ← sequential inserts!
Bits 48–51  : Version nibble (7)
Bits 52–63  : rand_a (12 bits)
Bits 64–65  : Variant (0b10)
Bits 66–127 : rand_b (62 bits)
```
- ✅ Sequential DB inserts — eliminates B-Tree fragmentation
- ✅ No coordination needed
- ❌ Still 128-bit (twice the size of `Long`)

#### Key API
```java
IdGenerator<String> v4 = new UUIDv4Generator();
IdGenerator<String> v7 = new UUIDv7Generator();
String id = v4.generate(); // "550e8400-e29b-41d4-a716-446655440000"
String id7 = v7.generate(); // "018e6b2a-xxxx-7xxx-yxxx-xxxxxxxxxxxx"
```

---

### 4. `ulid` — Universally Unique Lexicographically Sortable Identifier

```
┌──────────────────────────────────┬─────────────────────────────────────────────┐
│    Timestamp  (10 chars / 48 b)  │        Randomness  (16 chars / 80 b)        │
│       Unix epoch milliseconds    │  Crockford Base32 — no I, L, O, U chars     │
└──────────────────────────────────┴─────────────────────────────────────────────┘
```

| Parameter | Detail |
|---|---|
| **Encoding** | Crockford Base32 (`0-9A-Z` minus `I L O U`) |
| **Length** | 26 characters |
| **Bits** | 128 (48 timestamp + 80 random) |
| **Sortability** | Lexicographic = chronological |
| **Monotonicity** | Random part incremented by 1 within the same millisecond |

#### Key API
```java
ULIDGenerator gen = new ULIDGenerator();
String ulid = gen.generate(); // "01ARZ3NDEKTSV4RRFFQ69G5FAV"
```

---

### 5. `ksuid` — K-Sortable Unique Identifier

```
┌────────────────────────────────┬──────────────────────────────────────────────────┐
│    Timestamp (4 bytes / 32 b)  │           Payload  (16 bytes / 128 b)            │
│  Seconds since KSUID epoch     │      Cryptographically secure randomness          │
│  (2014-05-13T16:53:20Z)        │                                                   │
└────────────────────────────────┴──────────────────────────────────────────────────┘
```
Encoded as a **27-character Base62** string.

| Parameter | Detail |
|---|---|
| **Encoding** | Base62 (`0-9A-Za-z`) |
| **Length** | 27 characters |
| **Bits** | 160 (32 timestamp + 128 random) — highest collision resistance |
| **Timestamp granularity** | 1 second |
| **Use case** | Kafka streams, distributed event-sourcing logs |

#### Key API
```java
KSUIDGenerator gen = new KSUIDGenerator();
String ksuid = gen.generate(); // "0vdbMgWkU6SlqpNAssets4pMLhH"
```

---

### 6. `nanoid` — NanoID (Configurable Random String)

A highly customisable random string generator. Uses a **bitmask-and-reject** sampling
strategy to avoid modulo bias when the alphabet size is not a power of two.

| Parameter | Detail |
|---|---|
| **Default alphabet** | `_-0-9a-zA-Z` (64 URL-safe characters) |
| **Default length** | 21 characters → ~126 bits of entropy |
| **Ordering** | ❌ None — fully random, not suitable for DB primary keys |

#### Key API
```java
// Default: 21-char URL-safe string
NanoIdGenerator gen = new NanoIdGenerator();
String id = gen.generate(); // "V1StGXR8_Z5jdHi6B-myT"

// Custom: 10-char numeric PIN
NanoIdGenerator pinGen = new NanoIdGenerator("0123456789", 10);
String pin = pinGen.generate(); // "3847291054"
```

---

### 7. `hlc-snowflake` — Hybrid Logical Clock Snowflake

The **most advanced module**. Solves the biggest weakness of standard Snowflake: **clock
drift**. An HLC is a 64-bit value combining a physical wall-clock timestamp with a
logical causality counter. It never moves backwards.

#### HLC 64-bit Layout
```
 Bits 63–16                    Bits 15–0
 ┌──────────────────────────────┬────────────────────────────────┐
 │    Physical Time  (48 bits)  │   Logical Counter  (16 bits)   │
 │    Unix epoch milliseconds   │   0 – 65,535 causal events     │
 └──────────────────────────────┴────────────────────────────────┘
```

#### ID 64-bit Layout
```
 63     26 25       16 15           0
 ┌────────┬───────────┬──────────────┐
 │Time(37)│ Worker(10)│ Sequence(16) │
 └────────┴───────────┴──────────────┘
```

| Parameter | Detail |
|---|---|
| **Max workers** | 1,024 |
| **Throughput** | 65,536 IDs/ms/worker |
| **Clock drift** | ✅ **Resilient** — never throws, keeps logical counter monotonic |
| **Remote sync** | ✅ `receiveRemoteTimestamp()` for distributed causality tracking |

#### HLC Advancement Protocol (4 cases)

| Physical clock vs stored | Action |
|---|---|
| `pt > stored` | Advance to `pt`, reset logical counter to 0 |
| `pt == stored` | Keep `pt`, increment logical counter |
| `pt < stored` (drift!) | Keep stored `pt`, increment logical counter — **no rollback** |
| Logical counter overflows | Spin-wait for physical clock to advance, reset counter |

#### Key API
```java
HLCSnowflakeGenerator gen = new HLCSnowflakeGenerator(workerId);
long id = gen.generate();

// Tell this node about a timestamp from a remote peer
gen.receiveRemoteTimestamp(remoteHlcPacked);

// Inspect the current HLC state
HybridLogicalClock hlc = gen.getCurrentHlc();
System.out.println("pt=" + hlc.physicalTime() + " lc=" + hlc.logicalCount());

// Parse the ID
HLCSnowflakeGenerator.HLCComponents c = HLCSnowflakeGenerator.parse(id);
```

---

## ⚖️ Algorithm Comparison

| Strategy | Bit Width | Format | Time-Ordered | Coordination | Best Use Case |
|---|---|---|---|---|---|
| **UUIDv4** | 128 | 36-char hex | ❌ Random | None | Tokens, legacy systems |
| **UUIDv7** | 128 | 36-char hex | ✅ ms | None | Modern RDBMS with UUID schema |
| **ULID** | 128 | 26-char Base32 | ✅ ms | None | URL-safe, human-readable keys |
| **KSUID** | 160 | 27-char Base62 | ✅ 1s | None | Kafka, event sourcing |
| **NanoID** | Variable | Custom string | ❌ Random | None | Frontend, URL shorteners |
| **Snowflake** | 64 | `Long` | ✅ ms | Worker ID mgmt | High-throughput microservices |
| **HLC-Snowflake** | 64 | `Long` | ✅ causal | None | Distributed DBs, clock-drift environments |

---

## 🧪 Test Coverage Summary

| Module | Tests | What's Covered |
|---|---|---|
| `common` | 5 | Node ID validation, hex conversion |
| `snowflake` | 20 | Construction, uniqueness ×5 runs, monotonicity, bit parsing, clock-drift exception, concurrent uniqueness (8 threads × 500 IDs) |
| `uuid-generator` | 11 | Format regex, version nibble, variant bits, time-ordering, 10k uniqueness (both v4 & v7) |
| `ulid` | 7 | 26-char length, Crockford alphabet, ambiguous-char exclusion, 10k uniqueness, lex ordering |
| `ksuid` | 7 | 27-char Base62, 10k uniqueness, time-ordering, Base62 edge cases |
| `nanoid` | 12 | Default config, custom size/alphabet, single-char edge case, construction guards, 10k uniqueness |
| `hlc-snowflake` | 25 | HLC pack/unpack, 3-case advancement, clock-drift resilience, remote timestamp reception, bit layout parsing, concurrent uniqueness (8 threads × 500 IDs) |
| **Total** | **87** | |

---

## 📚 Reference Reading

- [Foundational Guide](./Distributed%20ID%20Generation_%20A%20Foundational%20Guide.md) — first-principles walkthrough, from monolith AUTO_INCREMENT to Snowflake
- [Planet-Scale Analysis](./Planet-Scale%20Distributed%20ID%20Generation.md) — deep dive into HLC, Google Spanner TrueTime, LMAX Disruptor, and Aeron

---

## 🛠 Tech Stack

- **Java 17** (records, `sealed`, text blocks)
- **Gradle 8.7** multi-module build
- **JUnit 5** (Jupiter) + **AssertJ** for fluent assertions
- Zero external ID-generation library dependencies — all algorithms implemented from scratch
