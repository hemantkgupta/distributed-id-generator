# UUID Generator Module — Diagrams

## 1. Component Diagram — UUIDv4 vs UUIDv7 side-by-side

```mermaid
graph LR
    subgraph v4 ["🔴 UUIDv4 — Random"]
        direction TB
        V4A["🎲 SecureRandom<br/>(128 bits)"]
        V4B["Set version nibble = 4<br/>Set variant bits = 10xx"]
        V4C["Format as 36-char hex string<br/>xxxxxxxx-xxxx-4xxx-yxxx-xxxx"]
        V4A --> V4B --> V4C
    end

    subgraph v7 ["🟢 UUIDv7 — Time-Ordered RFC 9562"]
        direction TB
        V7A["🕐 System.currentTimeMillis()<br/>(48-bit Unix ms)"]
        V7B["🎲 SecureRandom<br/>rand_a (12b) + rand_b (62b)"]
        V7C["Set version nibble = 7<br/>Set variant bits = 10xx"]
        V7D["Format as 36-char hex string<br/>xxxxxxxx-xxxx-7xxx-yxxx-xxxx"]
        V7A --> V7C
        V7B --> V7C
        V7C --> V7D
    end

    style v4 fill:#3a1a1a,color:#fff,stroke:#ff6b6b
    style v7 fill:#1a3a1a,color:#fff,stroke:#6bff6b
```

---

## 2. Bit-Layout Diagram — UUIDv4 (128-bit random)

```mermaid
graph LR
    subgraph uuid4 ["UUIDv4 — 128 bits"]
        A["Random<br/>(48 bits)"]
        B["Ver=4<br/>(4b)"]
        C["Random<br/>(12 bits)"]
        D["Var<br/>(2b)"]
        E["Random<br/>(62 bits)"]
    end
    A --> B --> C --> D --> E
```

> ⚠️ **No timestamp = no ordering.** Each insert lands at a random position in the
> B-Tree, causing page splits and index fragmentation at scale.

---

## 3. Bit-Layout Diagram — UUIDv7 (time-ordered, RFC 9562)

```mermaid
graph LR
    subgraph uuid7 ["UUIDv7 — 128 bits"]
        direction LR
        A["Unix ms timestamp<br/>(48 bits) — sequential!"]
        B["Ver=7<br/>(4b)"]
        C["rand_a<br/>(12 bits)"]
        D["Var 10xx<br/>(2b)"]
        E["rand_b<br/>(62 bits)"]
    end
    A --> B --> C --> D --> E

    style A fill:#1a3a5f,color:#fff,stroke:none
```

> ✅ **Timestamp prefix = sequential inserts.** Records land in chronological order,
> eliminating B-Tree page splits.

---

## 4. Flowchart — `UUIDv7Generator.generate()` construction

```mermaid
flowchart TD
    Start(["▶ generate called"]) --> Ts["Read System.currentTimeMillis()<br/>→ epochMs (48-bit)"]
    Ts --> Shift["Shift epochMs left 16 bits<br/>→ MSB bits 0-47 hold timestamp"]
    Shift --> SetVer["OR with 0x7000<br/>→ bits 48-51 = version 7"]
    SetVer --> RandA["OR with SecureRandom 12 bits<br/>→ bits 52-63 = rand_a"]
    RandA --> RandB["Generate 8 random bytes for LSB<br/>(rand_b = 64 bits)"]
    RandB --> SetVar["Stamp variant bits 0b10<br/>LSB = (lsb AND 0x3FFFFFFFFFFFFFFF) OR 0x8000000000000000"]
    SetVar --> Format["new UUID(msb, lsb).toString()<br/>→ 36-char hex with hyphens"]
    Format --> Return(["✅ return UUIDv7 string"])

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
```

---

## 5. Sequence Diagram — UUID generation and DB insert comparison

```mermaid
sequenceDiagram
    participant App as Application
    participant G4 as UUIDv4Generator
    participant G7 as UUIDv7Generator
    participant DB as Database B-Tree Index

    Note over G4, DB: UUIDv4 — random inserts cause fragmentation
    App->>G4: generate()
    G4-->>App: "f47ac10b-..." [fully random]
    App->>DB: INSERT with PK = f47ac10b-...
    DB->>DB: Page split! Random position in B-Tree

    Note over G7, DB: UUIDv7 — sequential inserts
    App->>G7: generate()
    G7-->>App: "018e9f2a-..." [timestamp prefix]
    App->>DB: INSERT with PK = 018e9f2a-...
    DB->>DB: Appended to end of B-Tree — no split
```

---

## 6. Class Diagram

```mermaid
classDiagram
    direction LR

    class IdGenerator~String~ {
        <<interface>>
        +generate() String
        +strategyName() String
    }

    class UUIDv4Generator {
        +generate() String
        +strategyName() String
    }

    class UUIDv7Generator {
        -RNG: SecureRandom$
        +generate() String
        +strategyName() String
    }

    IdGenerator <|.. UUIDv4Generator : implements
    IdGenerator <|.. UUIDv7Generator : implements
    UUIDv4Generator ..> UUID : delegates to UUID.randomUUID()
    UUIDv7Generator ..> UUID : wraps new UUID(msb, lsb)
```

---

## 7. Comparison Chart — Sortability vs Storage Efficiency

```mermaid
quadrantChart
    title ID Strategy Trade-offs: Sortability vs Storage Efficiency
    x-axis Low Storage Efficiency --> High Storage Efficiency
    y-axis Not Sortable --> Fully Sortable
    quadrant-1 Ideal - small and sorted
    quadrant-2 Sorted but large
    quadrant-3 Large and unsorted
    quadrant-4 Small but unsorted
    UUIDv4: [0.15, 0.05]
    UUIDv7: [0.15, 0.80]
    Snowflake: [0.95, 0.85]
    ULID: [0.30, 0.80]
    KSUID: [0.10, 0.65]
    NanoID: [0.45, 0.05]
    HLC-Snowflake: [0.95, 0.90]
```
