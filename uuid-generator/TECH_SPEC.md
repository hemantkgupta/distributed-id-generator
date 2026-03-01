# uuid-generator/TECH_SPEC.md

## Overview
The `uuid-generator` module embraces the RFC standard 128-bit identifier, specifically catering to UUIDv4 (Purely Random) and UUIDv7 (Unix Epoch Time-Ordered). It solves massive decentralization by requiring zero inter-node coordination during generation.

## UUIDv4 Structure (128-bit)
UUIDv4 generates purely random data, inserting explicit version ("4") and RFC 4122 variant bits ("10").

```text
xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
                ^    ^
        Version 4   Variant (8,9,a,b)
```

- **Pros**: Math guarantees extremely low collision probability.
- **Cons**: Severe database performance degradation. Because UUIDv4 is fully non-monotonic (random string), inserting rows forces Postgres/MySQL to break apart non-sequential B-Tree pages continuously.

## UUIDv7 Structure (128-bit)
UUIDv7 (RFC 9562) fixes the fatal indexing flaw of v4 by dedicating the most significant bits to a 48-bit UNIX millisecond timestamp.

```text
 ┌──────────────────────────────┬──────────────────────────────────────────────┐
 │    Unix timestamp ms (48 b)  │  Version=7(4b) │ rand_a(12b) │rand_b(62b)   │
 └──────────────────────────────┴──────────────────────────────────────────────┘
```

1. **Bits 0–47**: 48-bit UNIX epoch timestamp (in milliseconds). Provides sortability until the year 10889.
2. **Bits 48–51**: Version nibble `0111` (7).
3. **Bits 52–63**: 12 bits of cryptographically secure random data (`rand_a`).
4. **Bits 64–65**: Variant bits `10`.
5. **Bits 66–127**: 62 bits of cryptographically secure random data (`rand_b`).

## Trade-offs
- **Storage Profile**: Uses `16 bytes` natively (or raw 36-char VARCHAR in MySQL/Postgres defaults). This doubles the memory/storage requirements of Snowflake (`8 bytes`).
- **Cryptographic Security**: Unlike Snowflake (where timestamps and sequence numbers allow predicting competitors' creation rates), UUIDv7 obscures its internal cadence, stopping malicious actors from enumerating endpoints.
- **Coordination Tolerances**: Requires literally 0 configuration, worker IDs, or ETCD coordination. Works natively out-of-the-box everywhere.
