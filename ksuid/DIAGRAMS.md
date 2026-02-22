# KSUID Module — Diagrams

## 1. Structure Diagram — Anatomy of a 20-byte KSUID

```mermaid
graph LR
    subgraph ksuid ["KSUID — 20 bytes (160 bits) → 27-char Base62 string"]
        direction LR
        T["⏱ Timestamp\n4 bytes / 32 bits\nSeconds since KSUID epoch\n2014-05-13T16:53:20Z"]
        R["🎲 Payload / Randomness\n16 bytes / 128 bits\nCryptographic SecureRandom"]
    end
    T --> R

    style T fill:#1a3a5f,color:#fff,stroke:#4a90d9
    style R fill:#1a3a3a,color:#fff,stroke:#4ad9d9
```

**Example:** `0vdbMgWkU6SlqpNAssets4pMLhH`
```
Bytes 0–3      Bytes 4–19
──────────     ──────────────────────────────────────────────────────
Timestamp      128-bit random payload
(seconds)
```

---

## 2. Epoch Comparison Diagram

```mermaid
timeline
    title Epoch & Timestamp Ranges
    section Unix Epoch (1970-01-01)
        UUIDs, Snowflake  : All reference from 1970
                          : 32-bit seconds → year 2106
    section KSUID Epoch (2014-05-13)
        KSUID             : Custom epoch shifts usable range
                          : 32-bit seconds from 2014 → ~2150 (+136 years)
    section Custom Epoch (2024-01-01)
        Snowflake         : 41-bit ms → ~69.7 years from 2024
        HLC-Snowflake     : 37-bit ms  → ~4.3 years (extended by HLC)
```

---

## 3. Flowchart — `KSUIDGenerator.generate()` algorithm

```mermaid
flowchart TD
    Start([▶ generate called]) --> Epoch["Instant.now().getEpochSecond()\n- KSUID_EPOCH_SECONDS\n→ 32-bit epochSeconds"]

    Epoch --> Rand["SecureRandom.nextBytes(16)\n→ 128-bit payload"]

    Rand --> Pack["ByteBuffer.allocate(20)\n.putInt(epochSeconds)  [4 bytes]\n.put(payload)           [16 bytes]"]

    Pack --> Encode["base62Encode(20-byte array)\n→ Big-endian unsigned integer\n÷ 62 repeatedly, extract digits"]

    Encode --> Pad["Left-pad with '0'\nto ensure exactly 27 chars"]

    Pad --> Return(["✅ return 27-char Base62 KSUID"])

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
```

---

## 4. Flowchart — `base62Encode()` big-endian division algorithm

```mermaid
flowchart TD
    Input["20-byte array → int[5] chunks\n(4 bytes each, big-endian)"] --> OutArr["result = char[27]\ni = 26 (rightmost digit)"]

    OutArr --> Loop{i ≥ 0?}
    Loop -- Yes --> Divide["carry = 0\nFor each chunk j=0..4:\n  val = unsigned(chunks[j]) + carry × 2³²\n  chunks[j] = val ÷ 62\n  carry = val mod 62"]
    Divide --> SetChar["result[i] = BASE62[carry]"]
    SetChar --> Dec["i--"]
    Dec --> Loop

    Loop -- No / all 27 digits extracted --> Return(["✅ return new String(result)"])

    style Return fill:#1a6b3a,color:#fff,stroke:none
    style Input fill:#2d4a7a,color:#fff,stroke:none
```

---

## 5. Sequence Diagram — KSUID generation and event-store usage

```mermaid
sequenceDiagram
    participant Svc as Microservice
    participant KG as KSUIDGenerator
    participant RNG as SecureRandom
    participant Kafka as Kafka Topic

    Svc->>KG: generate()
    KG->>KG: epochSeconds = now - KSUID_EPOCH
    KG->>RNG: nextBytes(16) → payload
    KG->>KG: pack(epochSeconds, payload) → 20 bytes
    KG->>KG: base62Encode(bytes) → 27-char string
    KG-->>Svc: "0vdbMgWkU6S..."

    Svc->>Kafka: produce(event, key="0vdbMgWkU6S...")
    Note over Kafka: Events are lexicographically ordered by key<br/>= chronologically ordered by KSUID ✅

    Svc->>KG: generate() [1 second later]
    KG-->>Svc: "0vdbMh3rT9F..."
    Note over Svc: "0vdbMh3rT9F..." > "0vdbMgWkU6S..." ✅
```

---

## 6. Class Diagram

```mermaid
classDiagram
    direction TB

    class IdGenerator~String~ {
        <<interface>>
        +generate() String
        +strategyName() String
    }

    class KSUIDGenerator {
        -KSUID_EPOCH_SECONDS: 1400000000$ long
        -KSUID_STRING_LENGTH: 27$ int
        -KSUID_BYTES: 20$ int
        -BASE62: char[]$
        -random: SecureRandom
        +generate() String
        +strategyName() String
        +base62Encode(bytes)$ String
        +extractTimestamp(rawBytes)$ Instant
    }

    IdGenerator <|.. KSUIDGenerator : implements
```

---

## 7. Comparison — Collision resistance across strategies

```mermaid
xychart-beta
    title "Random Payload Size (bits) = Collision Resistance"
    x-axis ["UUIDv4", "UUIDv7", "ULID", "KSUID", "NanoID-21", "Snowflake"]
    y-axis "Bits of Randomness" 0 --> 130
    bar [122, 74, 80, 128, 126, 12]
```

> **KSUID** has the largest random payload (128 bits), making it the most
> collision-resistant string ID in this project. The trade-off is a coarser
> 1-second timestamp granularity.
