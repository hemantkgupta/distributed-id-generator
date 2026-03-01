# nanoid/TECH_SPEC.md

## Overview
The `nanoid` module exposes a highly flexible, uncoordinated random string generation sequence designed by standard URL-safe requirements. Unlike `ulid` and `ksuid`, NanoID enforces **strictly no chronological timestamping**.

## Default Configuration
By default, the `NanoIdGenerator` mimics the Javascript spec:
- **Alphabet**: `_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ` (64 characters).
- **Default Size**: `21` characters.

This 21-character structure holds approximately `126 bits` of entropy (`log2(64^21)`), making it statistically identically resilient to collisions as a UUIDv4.

## Key Characteristics
- **Short Lengths**: Often used selectively in URLs, verification codes, and frontend session tracking where standard UUIDs (36 characters) look bloated (e.g., `youtube.com/watch?v=dQw4w9WgXcQ`).
- **Zero Sequence Sorting**: NanoIDs must never be utilized as the sole primary key in massively clustered B-Trees unless wrapped behind a secondary sorting proxy, as the insertion logic will drastically cause page splits and performance deterioration.

## Implementation Nuance: The Mask & Reject Strategy
The standard Java `Random.nextInt(bound)` suffers from modulo bias if the customized alphabet size is not directly a power of 2 (e.g., 62 visible characters). 
To fight this, the `nanoid` generator extracts pure byte streams from `SecureRandom`, computes an explicit bitmask encompassing the highest bounds of the alphabet array, and strictly rejects any index outside the array (rather than coercing it via modulo arithmetic). This forces mathematical purity over random uniform distribution.
