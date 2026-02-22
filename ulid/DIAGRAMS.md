# ULID Module — Diagrams

## 1. Structure Diagram — Anatomy of a 26-character ULID

```mermaid
graph LR
    subgraph ulid ["ULID — 26 Crockford Base32 characters (128 bits)"]
        direction LR
        T["⏱ Timestamp<br/><b>10 chars / 48 bits</b><br/>Unix epoch ms"]
        R["🎲 Randomness<br/><b>16 chars / 80 bits</b><br/>SecureRandom (or monotonic increment)"]
    end
    T --> R

    style T fill:#1a3a5f,color:#fff,stroke:#4a90d9
    style R fill:#3a1a5f,color:#fff,stroke:#9a4ad9
```

**Example ULID:** `01ARZ3NDEKTSV4RRFFQ69G5FAV`
```
01ARZ3NDEK  TSV4RRFFQ69G5FAV
──────────  ────────────────
Timestamp   Randomness (80-bit)
(48-bit)
```

---

## 2. Crockford Base32 Alphabet Diagram

```mermaid
graph TD
    A["Standard Base32 (32 chars): 0-9, A-Z"] --> B["Remove ambiguous characters"]
    B --> C["Remove: I (looks like 1)<br/>Remove: L (looks like 1)<br/>Remove: O (looks like 0)<br/>Remove: U (looks like V)"]
    C --> D["✅ Crockford Base32 (32 chars):<br/>0123456789ABCDEFGHJKMNPQRSTVWXYZ"]

    style D fill:#1a6b3a,color:#fff,stroke:none
    style C fill:#7a3a00,color:#fff,stroke:none
```

---

## 3. Flowchart — `ULIDGenerator.generate()` algorithm

```mermaid
flowchart TD
    Start(["▶ generate called"]) --> Now["Instant.now().toEpochMilli()<br/>→ currentTimestamp"]
    Now --> SameMs{"currentTimestamp == lastTimestamp?"}

    SameMs -- "Yes - same ms" --> Increment["incrementRandomness()<br/>(+1 on 80-bit big-endian array)"]
    Increment --> Encode

    SameMs -- "No - new ms" --> FreshRand["SecureRandom.nextBytes(10)<br/>→ new 80-bit randomness"]
    FreshRand --> SaveTs["lastTimestamp = currentTimestamp"]
    SaveTs --> Encode

    Encode["encode(currentTimestamp, lastRandomness)<br/>→ build 26-char array"] --> TimePart["Encode 48-bit timestamp<br/>into chars[0..9]<br/>(10 Crockford Base32 chars)"]
    TimePart --> RandPart["Pack 10 bytes into two 40-bit longs<br/>Encode into chars[10..25]<br/>(16 Crockford Base32 chars)"]
    RandPart --> Return(["✅ return 26-char ULID string"])

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
    style SameMs fill:#5a3a00,color:#fff,stroke:none
```

---

## 4. Flowchart — `incrementRandomness()` carry propagation

```mermaid
flowchart LR
    Start(["▶ incrementRandomness()<br/>10-byte big-endian array"]) --> Loop["i = 9 (least significant byte)"]
    Loop --> Inc["++lastRandomness[i]"]
    Inc --> Carry{"Result == 0?<br/>(byte wrapped around)"}
    Carry -- "No - no carry" --> Done(["✅ Done — no carry needed"])
    Carry -- "Yes - carry" --> PrevByte["i-- (carry to next byte)"]
    PrevByte --> Exhausted{"i less than 0?<br/>(all 80 bits carried)"}
    Exhausted -- "No" --> Inc
    Exhausted -- "Yes - overflow" --> Done2(["⚠️ Randomness overflowed<br/>(extremely rare: 2^80 IDs/ms)"])

    style Done fill:#1a6b3a,color:#fff,stroke:none
    style Done2 fill:#7a3a00,color:#fff,stroke:none
```

---

## 5. Sequence Diagram — Two ULIDs in the same millisecond

```mermaid
sequenceDiagram
    participant App as Application
    participant G as ULIDGenerator (synchronized)
    participant RNG as SecureRandom

    App->>+G: generate() [Call 1]
    G->>G: now = T, T not equal to lastTimestamp
    G->>RNG: nextBytes(10) → random1
    G->>G: lastTimestamp = T, lastRandomness = random1
    G->>G: encode(T, random1)
    G-->>-App: "T_PREFIX" + Base32(random1)

    App->>+G: generate() [Call 2, same ms T]
    G->>G: now = T, T == lastTimestamp!
    G->>G: incrementRandomness() → random1 + 1
    Note over G: No new SecureRandom call needed
    G->>G: encode(T, random1+1)
    G-->>-App: "T_PREFIX" + Base32(random1+1)

    Note over App: ID2 greater than ID1 lexicographically — strict monotonicity ✅
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

    class ULIDGenerator {
        -ENCODING: char[] [32 Crockford chars]
        -random: SecureRandom
        -lastTimestamp: long
        -lastRandomness: byte[10]
        +generate() String
        +strategyName() String
        -encode(timestamp, randomness) String
        -incrementRandomness() void
    }

    IdGenerator <|.. ULIDGenerator : implements
```

---

## 7. Timeline — ULID monotonicity across milliseconds

```mermaid
timeline
    title ULID Generation Timeline
    section ms = 1000
        Call 1 : Fresh randomness R1
               : ULID = "1000_PREFIX" + Base32(R1)
        Call 2 : Increment to R1+1
               : ULID = "1000_PREFIX" + Base32(R1+1)  [strictly after call 1]
        Call 3 : Increment to R1+2
               : ULID = "1000_PREFIX" + Base32(R1+2)  [strictly after call 2]
    section ms = 1001
        Call 4 : Fresh randomness R2
               : ULID = "1001_PREFIX" + Base32(R2)  [strictly after all ms=1000 IDs]
    section ms = 1002
        Call 5 : Fresh randomness R3
               : ULID = "1002_PREFIX" + Base32(R3)
```
