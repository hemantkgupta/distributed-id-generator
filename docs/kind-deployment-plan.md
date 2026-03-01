# kind Deployment Plan

This document describes the target end-to-end deployment shape for local macOS testing with `kind`.

## Objective

Deploy a pod with two application containers:

1. `id-client-demo`
2. `id-sidecar-service`

The client calls the sidecar over `localhost` inside the same pod. The sidecar exposes one selected ID strategy at a time.

## Container Split

### `id-client-demo`

Responsibilities:
- issue repeated ID requests to the sidecar
- print or persist sample responses
- act as the end-to-end smoke client in Kubernetes

Non-responsibilities:
- no embedded ID generation logic
- no direct dependency on etcd or PostgreSQL

### `id-sidecar-service`

Responsibilities:
- load one configured ID generator strategy
- expose `/v1/ids` or equivalent gRPC method
- export readiness and liveness probes
- expose strategy metadata for debugging

Suggested configuration:
- `IDGEN_STRATEGY`
- `WORKER_ID`
- `DATACENTER_ID`
- `ETCD_ENDPOINTS`
- `JDBC_URL`
- `JDBC_USERNAME`
- `JDBC_PASSWORD`
- `LEAF_BIZ_TAG`

## Strategy Support Matrix

| Strategy | Sidecar Dependency | Kubernetes Support Service |
|---|---|---|
| UUIDv4 | In-process only | None |
| UUIDv7 | In-process only | None |
| ULID | In-process only | None |
| KSUID | In-process only | None |
| NanoID | In-process only | None |
| MongoDB ObjectID | In-process only | None |
| Snowflake | Worker identity config | None for single replica; stable worker IDs for multi-replica |
| HLC-Snowflake | Worker identity config | None for single replica; stable worker IDs for multi-replica |
| ETCD-Snowflake | etcd client | etcd |
| Ticket Server | JDBC | PostgreSQL |
| Leaf Segment | JDBC | PostgreSQL |

## kind On macOS

Recommended local stack:

1. Docker Desktop or Colima
2. `kind`
3. A local registry connected to the `kind` network

Recommended workflow:

1. Build the `id-sidecar-service` image locally.
2. Build the `id-client-demo` image locally.
3. Push both images to the local registry.
4. Create a `kind` cluster configured to use that registry.
5. Apply the manifests for the chosen strategy profile.
6. Run a smoke `Job` that calls the sidecar through `id-client-demo`.

## Kubernetes Resource Plan

### Stateless strategies

Use:
- `Deployment`
- `Service` only if traffic must enter from outside the pod

Applies to:
- UUIDv4
- UUIDv7
- ULID
- KSUID
- NanoID
- MongoDB ObjectID

### Snowflake and HLC-Snowflake

Use:
- `StatefulSet` for multi-replica deployment
- pod ordinal to derive stable worker IDs

Notes:
- a single-replica `Deployment` is enough for local smoke testing
- multi-replica needs stable worker identity assignment

### ETCD-Snowflake

Use:
- one `Deployment` or `StatefulSet` for the application pod
- one single-node etcd `StatefulSet` for local testing

Local-first recommendation:
- start with one etcd replica on `kind`
- move to three replicas only after the base flow works

### Ticket Server and Leaf Segment

Use:
- one `Deployment` for the application pod
- one PostgreSQL `StatefulSet`
- one PVC for PostgreSQL data
- one init or migration step to create the required schema

Required schema:
- `ticket-server`: one sequence-driving table
- `leaf-segment`: one allocation table keyed by `biz_tag`

## Manifest Layout

Recommended directory shape when the services are added:

```text
k8s/
├── base/
│   ├── id-client-demo.yaml
│   ├── id-sidecar-service.yaml
│   └── kustomization.yaml
└── overlays/
    └── kind/
        ├── stateless/
        ├── snowflake/
        ├── etcd/
        └── postgres/
```

## Validation Plan

After the services are implemented, validate in this order:

1. `./gradlew clean test`
2. sidecar unit tests
3. sidecar integration tests
4. `kind` deployment smoke for a stateless strategy
5. `kind` deployment smoke for `etcd-snowflake`
6. `kind` deployment smoke for `ticket-server`
7. `kind` deployment smoke for `leaf-segment`

## Current Status

Current repository state:
- the generator libraries are implemented
- unit tests exist for all modules
- live integration tests exist for `etcd-snowflake`, `ticket-server`, and `leaf-segment`

Not implemented yet:
- `id-sidecar-service`
- `id-client-demo`
- Kubernetes manifests
