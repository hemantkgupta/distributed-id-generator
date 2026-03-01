# ksuid/TECH_SPEC.md

## Overview
The `ksuid` module generates K-Sortable Unique IDentifiers created by Segment. KSUID is specifically prioritized when the system requires high collision resistance along with 1-second chronological ordering—a common pattern in big-data event-streaming pipelines (like Kafka).

## KSUID Structure (160-bit)
KSUID embraces a massive 160-bit payload, ensuring collision probability across entirely uncoordinated hosts goes to virtually absolute zero. 

```text
 ┌──────────────────────────────┬──────────────────────────────────────────────────┐
 │       Timestamp  (32 bits)   │           Payload / Randomness  (128 bits)       │
 │  Seconds since KSUID epoch   │              Cryptographic randomness             │
 └──────────────────────────────┴──────────────────────────────────────────────────┘
```

## Key Characteristics
1. **Epoch Setup**: Employs a fixed custom epoch `2014-05-13T16:53:20Z` to guarantee stability and prolong the 32-bit timestamp field to roughly ~136 years.
2. **Resolution**: `1 Second` — distinctly lower granularity than Snowflake or ULID (milliseconds). Thus, KSUID guarantees high-level sorting per second rather than perfect database entry sorting.
3. **Base62 Encoding**: KSUID outputs as a `27-character` string containing alphanumeric cases (`0-9`, `A-Z`, `a-z`). It does not pad with characters like hyphens.

## Collision Trade-offs
KSUID trades slightly thicker storage capacity (`160-bits`) to achieve what ULID does with `128-bits`. However, because its random component strictly contains `128 bits` instead of `80 bits`, KSUID boasts dramatically wider protection limits against astronomical generation rates.

## Implementation Details
Java doesn't natively support unsigned 160-bit integers. 
The algorithm packs the timestamp and payload into a `20-byte` array, wraps it around `ByteBuffer`, and employs a big-endian division trick using chunked `32-bit` arrays to constantly modulo by `62` and extract the Base62 characters left-to-right.
