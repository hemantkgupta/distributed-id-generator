# mongodb-objectid/TECH_SPEC.md

## Overview
The `mongodb-objectid` module embraces the industry-revered 12-byte specification engineered by MongoDB primarily for uncoordinated BSON identifiers.

## Standard Structure (12-byte Hex)

An ObjectID occupies precisely 12 bytes but generates predominantly as a `24-character` Hex String representation natively across the cluster.

```text
 ┌──────────────────────────────┬──────────────────────────┬────────────────────────────┐
 │       Timestamp (4 bytes)    │     Random (5 bytes)     │     Counter (3 bytes)      │
 │  Seconds since Unix epoch    │   Per-process random     │   Incrementing counter     │
 └──────────────────────────────┴──────────────────────────┴────────────────────────────┘
```

1. **Timestamp (4 bytes)**: Time since Unix Epoch exclusively measured down to a `1-second` resolution.
2. **Random Payload (5 bytes)**: Extracted out exclusively once upon JVM startup inside the static initializer block utilizing `SecureRandom`. Ensures machines rarely share prefixes.
3. **Sequential Counter (3 bytes)**: An `AtomicInteger` monotonically counting upwards starting securely from a random integer base (not zero).

## Trade-offs
1. **Length Sweet-spot**: The string output rests at `24-characters`. Significantly slimmer than a UUID object (`36-chars`), yet larger than NanoID. It perfectly matches a `char(24)` database persistence constraint.
2. **Collisions Limits**: Because the timestamp resolution only rolls over once per second, the collision limit depends purely on wrapping the `3-byte counter` (`16,777,216` sequences before a collision repeats within the identically started static random JVM sequence).
3. **Chronological Caveat**: Because it's locked to second resolutions, multiple ObjectIDs generated in high-throughput streams practically share identical 4-byte prefixes. They lexically order successfully solely because the counter trails the end byte.
