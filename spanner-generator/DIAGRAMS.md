# spanner-generator Diagrams

This document illustrates the internal architecture, database interaction flow, and component structure of the `spanner-generator` (TrueTime Commit Timestamp) ID generator module.

## 1. Sequence Diagram: TrueTime ID Generation
This sequence diagram shows how the Java generator requests a commit timestamp natively from the Google Cloud Spanner coordinator via gRPC without having to generate an ID in memory locally.

```mermaid
sequenceDiagram
    participant App as Client Application
    participant SGen as SpannerTrueTimeIdGenerator
    participant SAPI as Spanner gRPC Client
    participant Spanner as Google Cloud Spanner Node

    App->>+SGen: generate()
    SGen->>+SAPI: dbClient.writeAtLeastOnce(Mutation)
    Note over SAPI: Mutation binds Column -><br/>Value.COMMIT_TIMESTAMP
    
    SAPI->>+Spanner: gRPC Execute Write
    Note over Spanner: Consults hardware TrueTime API.<br/>Waits uncertainty window (~2ms).
    Spanner-->>-SAPI: Transaction Commited
    
    SAPI-->>-SGen: com.google.cloud.Timestamp
    
    SGen->>SGen: formatId(timestamp, uniqueNodeId)
    SGen-->>-App: "2024-10-18T10:15:30.123456000Z-testnode"
```

## 2. Flowchart: Generation Algorithm
This flowchart details the execution path, demonstrating the blind append-only write that drives the point-in-time extraction.

```mermaid
flowchart TD
    Start([Start generate]) --> BuildMut[Create Mutation.newInsertBuilder]
    BuildMut --> BindCols[Bind Timestamp Column to<br/>Value.COMMIT_TIMESTAMP]
    BindCols --> BindNode[Bind NodeSuffix Column to<br/>Generator Configuration suffix]
    
    BindNode --> ExecWrite[Execute writeAtLeastOnce]
    
    ExecWrite --> CheckSuccess{Write Successful?}
    
    CheckSuccess -- Yes --> ExtractTS[Extract returned Spanner Timestamp]
    CheckSuccess -- No --> ThrowErr[Throw IdGenerationException]
    
    ExtractTS --> Format[Combine Timestamp + '-' + UniqueNodeId]
    Format --> ReturnID([Return String ID])
    
    ThrowErr --> EndErr([Error: Failed to reach Spanner])
```

## 3. Component Diagram
Structural view of how the generator leverages standard Google APIs. Unlike typical SQL JDBC wrappers, this relies entirely on Spanner's custom gRPC SDK.

```mermaid
flowchart TD
    subgraph ClientApp [Client Application]
        AppService[Application Thread]
    end

    subgraph SpannerModule [spanner-generator Module]
        SGen[SpannerTrueTimeIdGenerator]
    end

    subgraph GoogleSDK [Google Cloud Client SDK]
        Client[DatabaseClient]
        Protocol[gRPC Channeller]
    end

    subgraph GoogleInfra [Google Cloud Platform]
        SpannerDB[(Cloud Spanner Node)]
        TrueTime[Atomic Clocks / GPS]
    end

    AppService -->|Calls generate| SGen
    SGen -->|Depends on| Client
    
    Client -->|Serializes Mutations via| Protocol
    
    Protocol -->|Network TLS| SpannerDB
    SpannerDB -.->|Polls Time Uncertainty| TrueTime
```
