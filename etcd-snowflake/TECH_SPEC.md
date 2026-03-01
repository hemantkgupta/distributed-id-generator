# etcd-snowflake/TECH_SPEC.md

## Overview
The `etcd-snowflake` module tackles the hardest operational hurdle of standard 64-bit Snowflake architecture globally: **Coordinate Auto-Configuration**. Out-of-the-box Snowflake demands that administrators assure hardware guarantees (i.e. node A is given workerId=1, and node B is given workerId=2). By injecting a distributed KV consensus store (`ETCD`) ahead of the generator, the system manages instance collisions gracefully and entirely automatically.

## Integration Architecture

Instead of booting and blindly generating numerical collisions, the Java `EtcdSnowflakeIdGenerator` process negotiates coordinate assignments utilizing the Etcd V3 gRPC protocol bounds via `jetcd-core`.

### The Lease Assignment Loop
1. The microservice creates an `EtcdNodeIdAssigner` allocating a fixed 10-bit maximum bound index of `1,024` attempts limit.
2. The module requests a singular fast-action ETCD `Lease` bound by a short TTL (commonly 10 seconds).
3. Utilizing strictly guaranteed `Txn.If(CmpTarget.version(0)).Then(Op.put(...).withLeaseId(lease))` loops, the client cycles sequentially from `0` to `1023` searching for a completely unprovisioned string key `/snowflake/nodes/{attempt_index}`.

## Execution Trade-offs

- **Startup Latency Impact**: Etcd network negotiations block instantiation during bean initialization frameworks (Spring/Guice), forcing boot delays of roughly ~50-100ms.
- **Background Networking Streams**: By definition, protecting a lease implies sustaining an active gRPC stream (`StreamObserver`). If Kubernetes scales nodes dramatically, Etcd requires holding massive open HTTP/2 persistent bidirectional pipelines.
- **Fail-Safe Circuit Breaker**: If `keepAlive` observers drop (indicating network partition bridging the microservice away from the ETCD truth cluster), the inner JVM process has lost authority natively on its coordinate (it's legally allowed for another pod to steal and boot with its ID). Because generating Snowflake sequences blindly beyond an ETCD partition guarantees collision, the `assignedId` should ultimately unlatch and crash safely via an `IdGenerationException`.
