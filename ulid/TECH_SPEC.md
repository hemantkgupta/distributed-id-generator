# ulid/TECH_SPEC.md

## Overview
The `ulid` module implements the Universally Unique Lexicographically Sortable Identifier (ULID) standard. ULIDs solve the need for completely uncoordinated (serverless) identifiers that are both highly unique and strongly chronologically ordered.

## ULID Structure (128-bit)
Every ULID is built from a timestamp and an 80-bit random block, compacted into a 26-character Base32 string.

```text
 ┌─────────────────────────────┬──────────────────────────────────────────────────┐
 │     Timestamp   (48 bits)   │           Randomness   (80 bits)                 │
 │  Unix epoch milliseconds    │    Crockford Base32 — no ambiguous characters    │
 └─────────────────────────────┴──────────────────────────────────────────────────┘
```

## Encoding & Alphabet
ULIDs use the Crockford Base32 alphabet:
`0123456789ABCDEFGHJKMNPQRSTVWXYZ`
- Excludes `I`, `L`, `O`, `U` to prevent visual ambiguity with `1` and `0`, and avoiding accidental profanity.

## Key Characteristics
1. **Size**: `128 bits` mapped to `26 characters`.
2. **Lexicographical Integrity**: A string comparison between two ULIDs gives the chronological sorting (`ULID_A < ULID_B == Time_A < Time_B`).
3. **Collision Resistance**: The 80-bit random factor allows generating 1.21e24 unique identifiers *per millisecond* before mathematical exhaustion.

## Monotonicity in the Same Millisecond
A significant edge case in ULID occurs when generating thousands of IDs within a single `System.currentTimeMillis()` tick. 
By the formal specification, if the timestamp hasn't ticked over to the next millisecond, the module **must increment the least significant bit** of the 80-bit randomness payload.
- This creates strict monotonicity.
- This codebase implements a Big-Endian carry array traversal to gracefully increment `byte[] lastRandomness` by 1 upon a tight-loop generation.
