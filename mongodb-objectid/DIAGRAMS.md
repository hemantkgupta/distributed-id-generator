# MongoDB ObjectID Module — Diagrams

## 1. Structure Diagram — Anatomy of a 12-byte ObjectID

```mermaid
graph LR
    subgraph objectid ["MongoDB ObjectID — 12 bytes (24 hex chars)"]
        direction LR
        T["Timestamp\n4 bytes / 32 bits\nUnix epoch seconds"]
        R["Random Value\n5 bytes / 40 bits\nPer-process SecureRandom seed"]
        C["Counter\n3 bytes / 24 bits\nAtomicInteger-derived sequence"]
    end
    T --> R --> C

    style T fill:#1a3a5f,color:#fff,stroke:#4a90d9
    style R fill:#1a5f3a,color:#fff,stroke:#4ad990
    style C fill:#5f3a1a,color:#fff,stroke:#d9904a
```

## 2. Flowchart — `ObjectIdGenerator.generate()` algorithm

```mermaid
flowchart TD
    Start(["▶ generate()"]) --> Ts["timestamp = System.currentTimeMillis() / 1000"]
    Ts --> Seq["counter.getAndIncrement()"]
    Seq --> Build["Allocate 12-byte array"]
    Build --> WriteTs["Write 4-byte timestamp\nbig-endian"]
    WriteTs --> WriteRand["Copy 5-byte process random value"]
    WriteRand --> WriteCtr["Write low 3 bytes of counter\nbig-endian"]
    WriteCtr --> Hex["IdGeneratorUtils.toHexString(bytes)"]
    Hex --> Return(["✅ return 24-char lowercase hex string"])

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
```

## 3. Sequence Diagram — Two IDs generated in the same second

```mermaid
sequenceDiagram
    participant App as Application
    participant G as ObjectIdGenerator
    participant RNG as SecureRandom

    Note over G,RNG: 5-byte random value is created once at JVM startup

    App->>G: generate()
    G->>G: timestamp = T
    G->>G: counter = N
    G->>G: bytes = [timestamp][random][counter]
    G-->>App: hex(T, random, N)

    App->>G: generate()
    G->>G: timestamp = T
    G->>G: counter = N + 1
    G->>G: bytes = [timestamp][random][counter+1]
    G-->>App: hex(T, random, N+1)

    Note over App: The second ID is distinct even though the timestamp prefix is unchanged.
```

## 4. Class Diagram

```mermaid
classDiagram
    direction TB

    class IdGenerator~String~ {
        <<interface>>
        +generate() String
        +strategyName() String
    }

    class ObjectIdGenerator {
        -SECURE_RANDOM: SecureRandom$
        -RANDOM_VALUE: byte[]$
        -counter: AtomicInteger
        +generate() String
        +strategyName() String
    }

    class IdGeneratorUtils {
        <<utility>>
        +toHexString(bytes)$ String
    }

    IdGenerator <|.. ObjectIdGenerator : implements
    ObjectIdGenerator ..> IdGeneratorUtils : uses
```
