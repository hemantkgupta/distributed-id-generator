# NanoID Module — Diagrams

## 1. Component Diagram — NanoID configurable design

```mermaid
graph TD
    subgraph config ["⚙️ Configuration (injected at construction)"]
        ALF["Alphabet\n(char[])\nDefault: _-0-9a-zA-Z (64 chars)"]
        SZ["Size\n(int)\nDefault: 21 characters"]
    end

    subgraph engine ["🔧 Generation Engine"]
        MASK["Bitmask Computation\n(2 &lt;&lt; (31 - CLZ(len-1))) - 1\nNearest power-of-2 bitmask"]
        STEP["Step Calculation\n⌈1.6 × mask × size / len⌉\nBytes to generate per batch"]
        RNG["SecureRandom\nGenerate `step` bytes per batch"]
        REJECT["Bitmask & Reject Sampling\nidx = byte & mask\nif idx < len → accept\nelse → reject (modulo bias avoided)"]
    end

    config --> engine
    MASK --> STEP --> RNG --> REJECT

    subgraph specialcase ["🔀 Special Case: Single-char Alphabet"]
        SC["alphabet.length == 1?\n→ Arrays.fill(result, alphabet[0])\n(no random needed)"]
    end

    REJECT -->|"filled == size"| Out(["✅ Return String"])
    REJECT -->|"need more"| RNG
    specialcase --> Out

    style config fill:#2d4a7a,color:#fff,stroke:#4a90d9
    style Out fill:#1a6b3a,color:#fff,stroke:none
```

---

## 2. Flowchart — `NanoIdGenerator.generate()` with bitmask-and-reject

```mermaid
flowchart TD
    Start([▶ generate called]) --> SingleChar{alphabet.length == 1?}

    SingleChar -- Yes --> Fill["Arrays.fill(result, alphabet[0])\n→ trivially done"]
    Fill --> Return

    SingleChar -- No --> Mask["Compute mask\n= (2 &lt;&lt; (31 - CLZ(len-1))) - 1\n(smallest power-of-2 bitmask ≥ len)"]
    Mask --> Step["Compute step\n= ⌈1.6 × mask × size / len⌉\n(batch size, tuned to minimise iterations)"]
    Step --> Init["filled = 0\nresult = char[size]"]

    Init --> Outer{filled &lt; size?}
    Outer -- Yes / need more chars --> Batch["SecureRandom.nextBytes(step)\n→ raw random bytes"]
    Batch --> Inner["For each byte b in batch:\n  idx = (b & 0xFF) & mask"]
    Inner --> Accept{idx &lt; alphabet.length?}
    Accept -- Yes / within range --> Store["result[filled++] = alphabet[idx]"]
    Store --> Done{filled == size?}
    Done -- Yes --> Return(["✅ return new String(result)"])
    Done -- No --> Accept
    Accept -- No / reject → avoids modulo bias --> Inner
    Outer -- No --> Return

    style Start fill:#2d4a7a,color:#fff,stroke:none
    style Return fill:#1a6b3a,color:#fff,stroke:none
    style Accept fill:#5a3a00,color:#fff,stroke:none
```

---

## 3. Diagram — Why modulo bias matters

```mermaid
graph TD
    Problem["❌ Naïve: idx = byte % alphabet.length
    
    If alphabet.length = 3, byte range = 0-255:
    • 0 maps to 0 (86 times from 0,3,6...255)
    • 1 maps to 1 (85 times from 1,4,7...254)
    • 2 maps to 2 (85 times from 2,5,8...254)
    
    → Character '0' appears MORE often!
    → Predictable bias = reduced entropy"]

    Solution["✅ Bitmask & Reject:
    
    mask = 0x03  (smallest power-of-2 - 1 ≥ 3)
    Accept only bytes 0, 1, 2 (idx < 3)
    Reject bytes 3 (idx >= 3)
    
    → Each accepted value has equal probability
    → Zero modulo bias = full entropy preserved"]

    Problem -->|"Fixed by"| Solution

    style Problem fill:#7a1a1a,color:#fff,stroke:none
    style Solution fill:#1a6b3a,color:#fff,stroke:none
```

---

## 4. Sequence Diagram — Custom NanoID for PIN generation

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant G as NanoIdGenerator
    participant RNG as SecureRandom

    Dev->>G: new NanoIdGenerator("0123456789", 6)
    Note over G: alphabet = ['0'..'9'] (10 chars)<br/>size = 6<br/>mask = 15 (0b1111, nearest ≥ 10)<br/>step = ⌈1.6×15×6/10⌉ = 15

    Dev->>G: generate()

    loop until 6 digits collected
        G->>RNG: nextBytes(15)
        RNG-->>G: [raw bytes]
        G->>G: For each byte: idx = byte & 0xF
        G->>G: if idx < 10 → accept digit, else reject
    end

    G-->>Dev: "482916"  [← uniform, unbiased 6-digit PIN]
```

---

## 5. Sequence Diagram — Default NanoID generation

```mermaid
sequenceDiagram
    participant App as Application
    participant G as NanoIdGenerator (default)
    participant RNG as SecureRandom

    App->>G: generate()
    Note over G: alphabet = 64 URL-safe chars<br/>size = 21<br/>mask = 63 (0b111111)<br/>step = ⌈1.6×63×21/64⌉ = 33

    G->>RNG: nextBytes(33)
    RNG-->>G: 33 random bytes

    G->>G: For each byte: idx = byte & 0x3F
    Note over G: 63 of 256 values reject (all ≥ 64)<br/>~75% acceptance rate → avg 1.3 batches for 21 chars

    G-->>App: "V1StGXR8_Z5jdHi6B-myT"  [21-char URL-safe ID]
```

---

## 6. State Diagram — NanoID generation retry loop

```mermaid
stateDiagram-v2
    [*] --> Start : generate() called
    Start --> SingleChar : alphabet.length == 1
    SingleChar --> Done : fill array with sole char

    Start --> ComputeMask : alphabet.length >= 2
    ComputeMask --> GenerateBatch : mask + step computed

    state GenerateBatch {
        [*] --> RequestBytes : nextBytes(step)
        RequestBytes --> ScanBytes : bytes ready
        ScanBytes --> Accept : idx < alphabet.length
        ScanBytes --> Reject : idx >= alphabet.length
        Reject --> ScanBytes : try next byte
        Accept --> CheckFull : store character
        CheckFull --> ScanBytes : filled < size
        CheckFull --> [*] : filled == size
    }

    GenerateBatch --> GenerateBatch : need more chars (rare)
    GenerateBatch --> Done : 21 chars collected

    Done --> [*] : return String
```

---

## 7. Class Diagram

```mermaid
classDiagram
    direction TB

    class IdGenerator~String~ {
        <<interface>>
        +generate() String
        +strategyName() String
    }

    class NanoIdGenerator {
        +DEFAULT_ALPHABET: String$ = "_-0-9a-zA-Z" (64 chars)
        +DEFAULT_SIZE: int$ = 21
        -alphabet: char[]
        -size: int
        -random: SecureRandom
        +NanoIdGenerator()
        +NanoIdGenerator(alphabet, size)
        +generate() String
        +strategyName() String
        +getSize() int
        +getAlphabet() char[]
    }

    IdGenerator <|.. NanoIdGenerator : implements
```
