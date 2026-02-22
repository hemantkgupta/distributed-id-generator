# Snowflake Module — Diagrams

## 1. Bit-Layout Diagram — Anatomy of a 64-bit Snowflake ID

```mermaid
graph LR
    subgraph id64 ["64-bit Snowflake ID  —  bit 63 (MSB) → bit 0 (LSB)"]
        direction LR
        SIGN["Sign<br/><b>1 bit</b><br/>always 0<br/>bit 63"]
        TS["Timestamp<br/><b>41 bits</b><br/>ms since epoch<br/>bits 62–22"]
        DC["Datacenter ID<br/><b>5 bits</b><br/>0–31<br/>bits 21–17"]
        WK["Worker ID<br/><b>5 bits</b><br/>0–31<br/>bits 16–12"]
        SEQ["Sequence<br/><b>12 bits</b><br/>0–4095<br/>bits 11–0"]
    end
    SIGN --> TS --> DC --> WK --> SEQ

    style SIGN fill:#4a1a6b,color:#fff,stroke:#9a4adb
    style TS   fill:#1a3a5f,color:#fff,stroke:#4a90d9
    style DC   fill:#1a5f3a,color:#fff,stroke:#4ad990
    style WK   fill:#5f3a1a,color:#fff,stroke:#d9904a
    style SEQ  fill:#5f1a3a,color:#fff,stroke:#d94a90
```

> **Reading guide:** bit 63 (leftmost) is always `0` — guarantees a positive `long`.  
> Bits 62–22 hold the 41-bit timestamp. Bits 21–17 hold the 5-bit datacenter ID.  
> Bits 16–12 hold the 5-bit worker ID. Bits 11–0 hold the 12-bit per-ms sequence.

---

## 2. Flowchart — `SnowflakeIdGenerator.generate()` algorithm

```mermaid
flowchart TD
    Start(["▶ generate called"]) --> ReadClock["Read currentEpochMillis()"]
    ReadClock --> ClockCheck{"currentTs < lastTimestamp?"}

    ClockCheck -- "Yes — clock drifted back" --> Throw["🚫 throw IdGenerationException<br/>'Clock moved backwards!'"]

    ClockCheck -- "No" --> SameMs{"currentTs == lastTimestamp?"}

    SameMs -- "Yes — same millisecond" --> IncrSeq["sequence = (sequence + 1) AND 0xFFF"]
    IncrSeq --> SeqOverflow{"sequence == 0?<br/>(all 4096 slots used)"}
    SeqOverflow -- "Yes — exhausted" --> SpinWait["⏳ waitNextMillis(lastTimestamp)<br/>busy-spin until clock ticks"]
    SpinWait --> UpdateTs2["currentTs = next ms"]
    SeqOverflow -- "No" --> UpdateLast

    SameMs -- "No — new millisecond" --> ResetSeq["sequence = 0"]
    ResetSeq --> UpdateLast
    UpdateTs2 --> UpdateLast

    UpdateLast["lastTimestamp = currentTs"] --> Assemble

    Assemble["🔧 Assemble 64-bit ID<br/>(ts SHIFT-LEFT 22) OR (dc SHIFT-LEFT 17) OR (worker SHIFT-LEFT 12) OR seq"]
    Assemble --> Return(["✅ return long"])

    style Throw fill:#7a1a1a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
    style SpinWait fill:#5a3a00,color:#fff,stroke:none
    style Start fill:#2d4a7a,color:#fff,stroke:none
```

---

## 3. Sequence Diagram — Concurrent ID generation by two threads

```mermaid
sequenceDiagram
    actor T1 as Thread-1
    actor T2 as Thread-2
    participant G as SnowflakeIdGenerator<br/>(synchronized)
    participant Clk as System Clock

    T1->>+G: generate()
    Note over G: acquires intrinsic lock
    G->>Clk: currentTimeMillis()
    Clk-->>G: t = 1000 ms
    G->>G: sequence = 0
    G-->>-T1: ID = (1000<<22)|(dc<<17)|(w<<12)|0

    T2->>+G: generate()
    Note over G: Thread-2 waits for lock, then acquires
    G->>Clk: currentTimeMillis()
    Clk-->>G: t = 1000 ms  [same ms!]
    G->>G: sequence++ → 1
    G-->>-T2: ID = (1000<<22)|(dc<<17)|(w<<12)|1

    Note over T1,T2: Both IDs are unique despite same ms timestamp
```

---

## 4. Sequence Diagram — Sequence exhaustion & spin-wait

```mermaid
sequenceDiagram
    participant App as Application
    participant G as SnowflakeIdGenerator
    participant Clk as System Clock

    loop 4096 times (IDs 0–4095)
        App->>G: generate()
        G->>Clk: currentTimeMillis() → T
        G->>G: sequence = 0..4095
        G-->>App: ID with seq=0..4095
    end

    App->>G: generate() [4097th in ms T]
    G->>Clk: currentTimeMillis() → T
    G->>G: sequence overflows → 0
    loop spin-wait
        G->>Clk: currentTimeMillis()
        Clk-->>G: still T...
    end
    Clk-->>G: T+1 (clock ticked!)
    G->>G: sequence = 0
    G-->>App: ✅ ID with timestamp T+1, seq=0
```

---

## 5. Class Diagram — `SnowflakeIdGenerator` internals

```mermaid
classDiagram
    direction LR

    class IdGenerator~Long~ {
        <<interface>>
        +generate() Long
        +strategyName() String
    }

    class SnowflakeIdGenerator {
        -workerId long
        -datacenterId long
        -lastTimestamp long
        -sequence long
        +CUSTOM_EPOCH: 1704067200000$ long
        +MAX_WORKER_ID: 31$ long
        +MAX_DATACENTER_ID: 31$ long
        +MAX_SEQUENCE: 4095$ long
        +TIMESTAMP_SHIFT: 22$ int
        +generate() Long
        +strategyName() String
        +parse(id) SnowflakeComponents$
        #currentEpochMillis() long
    }

    class SnowflakeComponents {
        <<record>>
        +rawId long
        +epochMillis long
        +datacenterId long
        +workerId long
        +sequence long
    }

    class IdGeneratorUtils {
        <<utility>>
        +validateNodeId()$
        +waitNextMillis()$
    }

    IdGenerator <|.. SnowflakeIdGenerator : implements
    SnowflakeIdGenerator ..> SnowflakeComponents : produces
    SnowflakeIdGenerator ..> IdGeneratorUtils : uses
    SnowflakeIdGenerator ..> IdGenerationException : throws
```

---

## 6. State Diagram — Snowflake generator lifecycle

```mermaid
stateDiagram-v2
    [*] --> Idle : constructor(dc, worker)
    note right of Idle : lastTimestamp = -1\nsequence = 0

    Idle --> Generating : generate() called
    Generating --> NewMs : currentTs &gt; lastTs
    Generating --> SameMs : currentTs == lastTs
    Generating --> ClockDrift : currentTs &lt; lastTs

    NewMs --> Idle : reset seq=0, emit ID
    SameMs --> SeqAvailable : seq++ ≤ 4095
    SameMs --> SpinWait : seq++ overflows to 0
    SeqAvailable --> Idle : emit ID
    SpinWait --> Idle : clock advances, emit ID

    ClockDrift --> [*] : 🚫 throw IdGenerationException
```
