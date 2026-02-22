# HLC-Snowflake Module — Diagrams

## 1. Component Diagram — HLC-Snowflake architecture

```mermaid
graph TB
    subgraph hlc_module ["📦 hlc-snowflake module"]
        direction TB

        subgraph hlc_state ["🕰️ HybridLogicalClock (record)"]
            PT["physicalTime\n(48 bits) — Unix ms"]
            LC["logicalCount\n(16 bits) — 0..65535"]
            Pack["pack() → 64-bit long\nunpack(long) → HLC"]
        end

        subgraph generator ["⚙️ HLCSnowflakeGenerator"]
            Advance["advance(current, physicalTime)\n4-case HLC protocol"]
            GenId["generate() → Long\nassemble 64-bit ID"]
            Receive["receiveRemoteTimestamp(packed)\ndistributed causality sync"]
            Parse["parse(id) → HLCComponents\ndecompose ID bits"]
        end

        HLCComp["HLCComponents (record)\nrawId / epochMillis / workerId / sequence"]
    end

    subgraph common ["📦 common"]
        IG["IdGenerator&lt;Long&gt;"]
        IU["IdGeneratorUtils\n(validateNodeId, waitNextMillis)"]
    end

    IG --> generator
    IU --> generator
    hlc_state --> generator
    generator --> HLCComp

    style hlc_module fill:#1a1a3a,color:#fff,stroke:#4a4aff
    style common fill:#1a3a1a,color:#fff,stroke:#4aff4a
```

---

## 2. Bit-Layout Diagram — 64-bit HLC-Snowflake ID

```mermaid
graph LR
    subgraph id64 ["HLC-Snowflake ID — 64 bits"]
        direction LR
        T["HLC Physical Time\n37 bits\nms since 2024-01-01\nbits 63–26"]
        W["Worker ID\n10 bits\n0–1023\nbits 25–16"]
        S["HLC Sequence\n16 bits\n0–65535\nbits 15–0"]
    end
    T --> W --> S

    style T fill:#1a3a5f,color:#fff,stroke:#4a90d9
    style W fill:#3a5f1a,color:#fff,stroke:#90d94a
    style S fill:#5f1a3a,color:#fff,stroke:#d94a90
```

> **vs. Standard Snowflake**: Trades timestamp range (37 vs 41 bits → ~4.3 vs ~69.7
> years) for a 16-bit sequence (65,536 vs 4,096 IDs/ms/worker). Combined with HLC's
> logical counter, this offers far greater burst throughput.

---

## 3. Flowchart — The 4-case HLC `advance()` protocol

```mermaid
flowchart TD
    Start(["▶ advance(current HLC, physicalTime pt)"]) --> Case1{pt > current.physicalTime?}

    Case1 -- Yes / clock advanced --> Forward["✅ CASE 1: Clock moved forward\nphysicalTime = pt\nlogicalCount = 0\n(reset — fresh ms)"]
    Forward --> Done

    Case1 -- No --> Case23{pt == current.physicalTime?}

    Case23 -- Yes / same ms --> Inc["CASE 2: Same millisecond\nphysicalTime = pt (unchanged)\nlogicalCount++"]
    Inc --> Overflow{logicalCount > 65535?}
    Overflow -- No --> Done
    Overflow -- Yes --> Spin["⏳ CASE 4: Counter exhausted\nwaitNextMillis(current.physicalTime)\nreset to 0"]
    Spin --> Done

    Case23 -- No / pt &lt; current.physicalTime --> Drift["⚠️ CASE 3: Clock drifted backwards!\nphysicalTime = current.physicalTime (NO rollback!)\nlogicalCount++ (causality maintained)"]
    Drift --> Overflow2{logicalCount > 65535?}
    Overflow2 -- No --> Done
    Overflow2 -- Yes --> Spin

    Done(["✅ Return new HybridLogicalClock(physicalTime, logicalCount)"])

    style Forward fill:#1a6b3a,color:#fff,stroke:none
    style Drift fill:#7a5a00,color:#fff,stroke:none
    style Spin fill:#5a3a00,color:#fff,stroke:none
    style Done fill:#1a3a6b,color:#fff,stroke:none
    style Start fill:#2d4a7a,color:#fff,stroke:none
```

---

## 4. Flowchart — `HLCSnowflakeGenerator.generate()` end-to-end

```mermaid
flowchart TD
    Start(["▶ generate() — synchronized"]) --> ReadClock["pt = currentEpochMillis()\n= System.currentTimeMillis() - CUSTOM_EPOCH"]
    ReadClock --> Advance["hlc = advance(hlc, pt)\n[4-case HLC protocol]"]
    Advance --> Extract["timestamp = hlc.physicalTime()\nsequence  = hlc.logicalCount()"]
    Extract --> Assemble["Assemble 64-bit ID via bit-shifts:\n(timestamp &lt;&lt; 26)\n| (workerId &lt;&lt; 16)\n| sequence"]
    Assemble --> Return(["✅ return Long"])

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
```

---

## 5. Sequence Diagram — Standard Snowflake vs HLC on clock drift

```mermaid
sequenceDiagram
    participant App as Application
    participant SF as SnowflakeGenerator
    participant HLC as HLCSnowflakeGenerator
    participant NTP as NTP Service (clock rollback)

    Note over App, NTP: Normal operation
    App->>SF: generate()
    SF-->>App: ID with t=1000

    App->>HLC: generate()
    HLC-->>App: ID with t=1000, lc=0

    NTP->>SF: Clock rolled back to t=998!
    NTP->>HLC: Clock rolled back to t=998!

    App->>SF: generate()
    SF->>SF: currentTs(998) < lastTs(1000) ← DRIFT!
    SF-->>App: 🚫 throws IdGenerationException
    Note over App: Application CRASHES / halts

    App->>HLC: generate()
    HLC->>HLC: pt(998) < stored(1000) ← Case 3!
    HLC->>HLC: Keep physicalTime=1000, logicalCount=1
    HLC-->>App: ✅ ID with t=1000, lc=1 (unique, monotonic)
    Note over App: Application CONTINUES uninterrupted ✅
```

---

## 6. Sequence Diagram — Distributed remote timestamp reception

```mermaid
sequenceDiagram
    participant NodeA as Node A (HLC)
    participant NodeB as Node B (HLC)
    participant NodeC as Node C (HLC)

    Note over NodeA,NodeC: Each node has its own local HLC
    Note over NodeA: HLC₍A₎ = pt=1000, lc=5
    Note over NodeB: HLC₍B₎ = pt=998,  lc=10
    Note over NodeC: HLC₍C₎ = pt=1001, lc=0

    NodeA->>NodeB: message (carries HLC₍A₎ packed)
    NodeB->>NodeB: receiveRemoteTimestamp(HLC₍A₎)
    Note over NodeB: maxPt = max(local=998, remoteA=1000, clock=999) = 1000
    Note over NodeB: HLC₍A₎.pt==maxPt → newLc = HLC₍A₎.lc + 1 = 6
    Note over NodeB: HLC₍B₎ updated to pt=1000, lc=6

    NodeB->>NodeC: message (carries HLC₍B₎ = pt=1000, lc=6)
    NodeC->>NodeC: receiveRemoteTimestamp(HLC₍B₎)
    Note over NodeC: maxPt = max(local=1001, remoteB=1000, clock=1001) = 1001
    Note over NodeC: local.pt==maxPt → newLc = HLC₍C₎.lc + 1 = 1
    Note over NodeC: HLC₍C₎ updated to pt=1001, lc=1

    Note over NodeA,NodeC: All nodes maintain causally consistent ordering ✅
```

---

## 7. State Diagram — HLC lifecycle

```mermaid
stateDiagram-v2
    [*] --> Initialised : new HLCSnowflakeGenerator(workerId)
    note right of Initialised : HLC = {pt=wallClock, lc=0}

    Initialised --> Generating : generate() called

    state Generating {
        [*] --> ReadClock : read pt
        ReadClock --> Case1 : pt > hlc.pt
        ReadClock --> Case2 : pt == hlc.pt
        ReadClock --> Case3 : pt &lt; hlc.pt (drift!)
        Case1 --> EmitID : hlc={pt,lc=0}
        Case2 --> CheckOverflow : lc++
        Case3 --> CheckOverflow : keep pt, lc++
        CheckOverflow --> EmitID : lc ≤ 65535
        CheckOverflow --> SpinWait : lc > 65535
        SpinWait --> EmitID : wait for next ms
        EmitID --> [*]
    }

    Generating --> Synchronising : receiveRemoteTimestamp()

    state Synchronising {
        [*] --> ComputeMax : max(local, remote, wall)
        ComputeMax --> UpdateHLC : advance lc based on which source had maxPt
        UpdateHLC --> [*]
    }

    Synchronising --> Generating : ready for next ID
    Generating --> Generating : next generate() call
```

---

## 8. Class Diagram — Complete HLC-Snowflake structure

```mermaid
classDiagram
    direction TB

    class IdGenerator~Long~ {
        <<interface>>
        +generate() Long
        +strategyName() String
    }

    class HybridLogicalClock {
        <<record>>
        +MAX_LOGICAL_COUNT: 65535$ int
        +physicalTime long
        +logicalCount int
        +pack() long
        +unpack(packed)$ HybridLogicalClock
        +of(wallClockMs)$ HybridLogicalClock
    }

    class HLCSnowflakeGenerator {
        +CUSTOM_EPOCH: 1704067200000$ long
        +MAX_WORKER_ID: 1023$ long
        +MAX_SEQUENCE: 65535$ long
        +TIMESTAMP_SHIFT: 26$ int
        +WORKER_ID_SHIFT: 16$ int
        -workerId long
        -hlc HybridLogicalClock
        +generate() Long
        +strategyName() String
        +receiveRemoteTimestamp(packed) void
        +getCurrentHlc() HybridLogicalClock
        +parse(id)$ HLCComponents
        #advance(current, pt) HybridLogicalClock
        #currentEpochMillis() long
    }

    class HLCComponents {
        <<record>>
        +rawId long
        +epochMillis long
        +workerId long
        +sequence long
    }

    class IdGenerationException {
        <<RuntimeException>>
    }

    class IdGeneratorUtils {
        <<utility>>
        +validateNodeId()$
        +waitNextMillis()$
    }

    IdGenerator <|.. HLCSnowflakeGenerator
    HLCSnowflakeGenerator "1" *-- "1" HybridLogicalClock : owns (mutable state)
    HLCSnowflakeGenerator ..> HLCComponents : produces
    HLCSnowflakeGenerator ..> IdGeneratorUtils : uses
    HLCSnowflakeGenerator ..> IdGenerationException : may throw (overflow)
```

---

## 9. Comparison — Snowflake vs HLC-Snowflake

```mermaid
graph LR
    subgraph sf ["❄️ Standard Snowflake"]
        direction TB
        SF1["✅ 41-bit timestamp\n~69.7 year range"]
        SF2["✅ 12-bit sequence\n4,096 IDs/ms/node"]
        SF3["❌ Clock drift\n→ throws exception\n→ node halts"]
        SF4["❌ No causality tracking"]
    end

    subgraph hlc ["🕰️ HLC-Snowflake"]
        direction TB
        H1["⚠️ 37-bit timestamp\n~4.3 year range (shorter)"]
        H2["✅ 16-bit sequence\n65,536 IDs/ms/node (16× more)"]
        H3["✅ Clock drift\n→ logical counter increments\n→ node continues"]
        H4["✅ Causal ordering via\nreceiveRemoteTimestamp()"]
    end

    style sf fill:#3a1a1a,color:#fff,stroke:#ff6b6b
    style hlc fill:#1a3a1a,color:#fff,stroke:#6bff6b
```
