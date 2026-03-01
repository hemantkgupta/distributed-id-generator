# etcd-snowflake Diagrams

This document illustrates the internal architecture, initialization flow, and component structure of the `etcd-snowflake` generator module. This module enables distributed coordination of standard 64-bit Snowflake datacenter and worker IDs using an ETCD V3 Backend.

## 1. Sequence Diagram: Initialization and Generation
This diagram shows how a client application initializes `EtcdSnowflakeIdGenerator` which then requests an exclusive worker node ID via `EtcdNodeIdAssigner`. ETCD uses leases to ensure that node IDs are safely reused if a node crashes or disconnects.

```mermaid
sequenceDiagram
    participant App as Client App (Spring/Guice)
    participant EIdGen as EtcdSnowflakeIdGenerator
    participant Assigner as EtcdNodeIdAssigner
    participant Etcd as ETCD Cluster
    participant SGen as SnowflakeIdGenerator

    %% Initialization Phase
    App->>+EIdGen: new EtcdSnowflakeIdGenerator(client, basePath)
    EIdGen->>+Assigner: new EtcdNodeIdAssigner(client, basePath, 1024)
    Assigner-->>-EIdGen: assigner instance
    
    EIdGen->>+Assigner: assignWorkerId(ttl=10s)
    Assigner->>+Etcd: lease.grant(10s)
    Etcd-->>-Assigner: Lease ID
    
    %% Background Keep-Alive
    Note over Assigner,Etcd: Starts background keep-alive to renew lease
    Assigner->>Etcd: lease.keepAlive(StreamObserver)
    
    %% ID Assignment Loop
    loop i from 0 to 1023
        Assigner->>+Etcd: txn.If(key/i not exists).Then(put(i) with Lease)
        Etcd-->>-Assigner: Success (claimed ID i)
    end
    
    Assigner-->>-EIdGen: Assigned 10-bit Node ID (e.g. 35)
    
    %% Split into Datacenter and Worker ID (5-bits each)
    Note over EIdGen: Splits 10-bit Node ID into:<br/>Datacenter ID (5-bits)<br/>Worker ID (5-bits)
    
    EIdGen->>+SGen: new SnowflakeIdGenerator(datacenterId, workerId)
    SGen-->>-EIdGen: generator instance
    
    EIdGen-->>-App: Ready for ID Generation

    %% ID Generation Phase
    App->>+EIdGen: generate()
    EIdGen->>+SGen: generate()
    Note over SGen: Generates 64-bit ID based on time, sequence, and split IDs
    SGen-->>-EIdGen: 64-bit Snowflake ID
    EIdGen-->>-App: 64-bit Snowflake ID
```

## 2. Flowchart: Worker ID Assignment Algorithm
This flowchart details the algorithm used by `EtcdNodeIdAssigner` to negotiate and claim an available coordinate ID over ETCD KV. It ensures unique ID ownership through lease-bound strictly atomic `txn` (transaction) commands.

```mermaid
flowchart TD
    Start([Start Worker ID Assignment]) --> GrantLease[Grant ETCD Lease ttl=10s]
    GrantLease --> StartKeepAlive[Start Background Keep-Alive observer]
    StartKeepAlive --> InitIndex[Initialize i = 0]
    
    InitIndex --> CheckLimit{i < 1024?}
    
    CheckLimit -- Yes --> TxnMatch[Execute ETCD Transaction:<br/>If key does not exist:<br/>Put instance UUID with Lease]
    TxnMatch --> CheckSuccess{Txn Success?}
    
    CheckSuccess -- Yes --> AssignID[Assign Node ID = i]
    CheckSuccess -- No --> IncrementIndex[i = i + 1]
    
    IncrementIndex --> CheckLimit
    
    CheckLimit -- No --> Exhausted[Throw IdGenerationException:<br/>No available IDs]
    
    AssignID --> End([Return Assigned ID])
    Exhausted --> EndError([Error: Node startup fails])
```

## 3. Component Diagram
This diagram outlines the relationships and dependencies among the core architectural and structural components of the `etcd-snowflake` generator. 

```mermaid
flowchart TD
    subgraph Client Application
        AppService[Spring/Guice Service]
    end

    subgraph etcd-snowflake Module
        EIdGen[EtcdSnowflakeIdGenerator]
        Assigner[EtcdNodeIdAssigner]
    end

    subgraph snowflake Module
        SGen[SnowflakeIdGenerator]
    end

    subgraph jetcd Core
        KV[KV Client]
        Lease[Lease Client]
        gRPC[gRPC Channel]
    end

    subgraph ETCD v3 Cluster
        EtcdCluster[(Distributed KV Store)]
    end

    AppService -->|Uses to generate IDs| EIdGen
    EIdGen -->|Initializes| Assigner
    EIdGen -->|Delegates generation| SGen

    Assigner -->|Reads/Writes Node Keys| KV
    Assigner -->|Grants/Renews Leases| Lease
    
    KV -->|Uses HTTP/2 Streams| gRPC
    Lease -->|Uses HTTP/2 Streams| gRPC
    
    gRPC <-->|Remote Operations| EtcdCluster
```
