# hlc-snowflake/TECH_SPEC.md

## Overview
The `hlc-snowflake` module implements the **Hybrid Logical Clock (HLC)** architecture proposed by Martin Fowler. By supplementing the physical timestamp with a causal sequence counter, an HLC establishes a distributed vector that is perfectly immune to backwards time-travel events caused by flawed or drifting NTP daemons on Snowflake workers.

## 64-bit Structure (Bit Layout)
The module's actual Long representation exactly mimics a standard Snowflake layout, making it completely interoperable with external parsers. The difference lies entirely in *how* those timestamp bits are accumulated internally.

```text
 63     26 25       16 15           0
 ┌────────┬───────────┬──────────────┐
 │Time(37)│ Worker(10)│ Sequence(16) │
 └────────┴───────────┴──────────────┘
```

The `Time(37)` represents the *physical time* extracted from the Hybrid Logical Clock. The `Sequence(16)` acts immediately as the *logical counter* in the Hybrid Logical Clock logic.

## Trade-offs
1. **Capacity Overhaul**: Instead of generating `4096` IDs per millisecond, the allocation strategy extends the worker ID to 10-bits combined and widens the sequence counter to a generous `16-bits`, offering up to `65,536` events per millisecond before overflowing the spin-wait sequence.
2. **Clock Drift Immunity**: If NTP pulls the machine clock backwards by 5 seconds, standard Snowflake must either yield generating IDs or crash. *HLC Snowflake* retains the highest-witnessed logical `lastTimestamp`, incrementing solely the `Sequence(16)` logical counter until the physical clock finally catches up realistically.
3. **Causality Exchange**: For absolute safety across clustered services, `HLCSnowflakeGenerator` exposes a `receiveRemoteTimestamp(long packedHlc)`. Distributed applications exchanging gRPC metadata or Kafka headers should transmit their internal clock and sync it alongside RPC request dispatches. 

## Thread Safety Details
`HLCSnowflakeGenerator` internally wraps an explicitly lock-based synchronization boundary around its `.generate()` mechanism due to mutable interactions on the tuple `(lastTimestamp, logicalCounter)`. 
Wait-loops strictly delegate back out to the high-throughput `IdGeneratorUtils.waitNextMillis()` boundary.
