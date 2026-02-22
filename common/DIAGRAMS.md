# Common Module — Diagrams

## 1. Class Diagram — Shared Contracts & Utilities

```mermaid
classDiagram
    direction TB

    class IdGenerator {
        <<interface>>
        +generate() T
        +strategyName() String
    }

    class IdGenerationException {
        <<RuntimeException>>
        +IdGenerationException(message)
        +IdGenerationException(message, cause)
    }

    class IdMetadata {
        <<record>>
        +rawId String
        +strategy String
        +bitLength int
        +description String
        +toString() String
    }

    class IdGeneratorUtils {
        <<utility>>
        +validateNodeId(id, maxValue, label)$
        +waitNextMillis(lastTimestamp) long$
        +toHexString(bytes) String$
    }

    IdGenerator ..> IdGenerationException : throws
    IdGenerator ..> IdMetadata : produces (optional)
    IdGenerator ..> IdGeneratorUtils : uses
```

---

## 2. Component Diagram — Module Dependency Graph

```mermaid
graph TB
    subgraph common ["📦 common (shared foundation)"]
        IG["🔌 IdGenerator&lt;T&gt;\n(interface)"]
        IE["⚠️ IdGenerationException\n(unchecked)"]
        IM["📄 IdMetadata\n(record)"]
        IU["🔧 IdGeneratorUtils\n(utility)"]
    end

    subgraph consumers ["Consumer Modules"]
        SF["❄️ snowflake"]
        UUID["🔑 uuid-generator"]
        ULID["📝 ulid"]
        KS["🔐 ksuid"]
        NI["🎲 nanoid"]
        HLC["🕰️ hlc-snowflake"]
    end

    SF --> common
    UUID --> common
    ULID --> common
    KS --> common
    NI --> common
    HLC --> common

    style common fill:#1e3a5f,color:#fff,stroke:#4a90d9
    style consumers fill:#1a1a2e,color:#fff,stroke:#555
```

---

## 3. Flowchart — `IdGeneratorUtils.waitNextMillis()` spin-wait logic

```mermaid
flowchart TD
    A([▶ Enter waitNextMillis\nlastTimestamp]) --> B[/Read System.currentTimeMillis/]
    B --> C{ts ≤ lastTimestamp?}
    C -- Yes / clock not advanced --> B
    C -- No / clock moved forward --> D([✅ Return ts])

    style A fill:#2d4a7a,color:#fff,stroke:none
    style D fill:#1a6b3a,color:#fff,stroke:none
    style C fill:#5a3a00,color:#fff,stroke:none
```

---

## 4. Flowchart — `IdGeneratorUtils.validateNodeId()` guard logic

```mermaid
flowchart LR
    A([▶ id, maxValue, label]) --> B{id &lt; 0?}
    B -- Yes --> E["🚫 throw\nIllegalArgumentException\n'label must be 0–maxValue'"]
    B -- No --> C{id &gt; maxValue?}
    C -- Yes --> E
    C -- No --> D([✅ Valid — return])

    style E fill:#7a1a1a,color:#fff,stroke:none
    style D fill:#1a6b3a,color:#fff,stroke:none
```
