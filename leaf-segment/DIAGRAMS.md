# leaf-segment Diagrams

This document illustrates the internal architecture, block pre-fetching flow, and component structure of the `leaf-segment` (Dual-Buffer Block Allocation) ID generator module.

## 1. Sequence Diagram: Dual-Buffer Pre-Fetching
This sequence diagram shows how the system yields high throughput by serving requests directly from memory, while asynchronously pre-fetching the next segment in the background right before the current buffer exhausts.

```mermaid
sequenceDiagram
    participant App as App Thread
    participant LGen as LeafSegmentIdGenerator
    participant Async as Async Fetch Thread
    participant Fetcher as IdBlockFetcher
    participant DB as Database

    App->>+LGen: new LeafSegmentIdGenerator(fetcher, "user", step=1000)
    LGen->>Fetcher: fetchAndReturnNextBlock("user", 1000)
    Fetcher->>DB: UPDATE & SELECT Block
    DB-->>Fetcher: [1001, 2000]
    Fetcher-->>LGen: Segment A
    LGen-->>-App: Ready

    %% In Memory Generation
    App->>LGen: generate()
    Note over LGen: Serves from memory via AtomicLong.incrementAndGet()
    LGen-->>App: 1001

    %% Reach Threshold
    Note over App, LGen: Generating items 1002 to 1899 locally...
    App->>+LGen: generate()
    Note over LGen: Hits 90% Threshold (e.g., 1900 > 0.9 * 1000)
    
    %% Async Trigger
    LGen->>Async: triggerAsyncFetch() (Run Async)
    LGen-->>-App: 1900 (Returns instantly to user)
    
    %% Background Work
    Async->>+Fetcher: fetchAndReturnNextBlock("user", 1000)
    Fetcher->>DB: UPDATE & SELECT Block
    DB-->>Fetcher: [2001, 3000]
    Fetcher-->>-Async: Segment B
    Note over Async,LGen: Stores Segment B as nextSegment
    
    %% Final Exhaustion
    Note over App, LGen: Generating items 1901 to 2000 locally...
    App->>+LGen: generate()
    Note over LGen: Segment A Exhausted (val > max)
    Note over LGen: Buffer swap! currentSegment = nextSegment (Segment B)
    LGen-->>-App: 2001
```

## 2. Flowchart: Generation Algorithm with Threshold
This flowchart demonstrates the `generate()` logic and how the engine determines when to trigger background tasks without blocking the main application thread.

```mermaid
flowchart TD
    Start([Start generate]) --> Inc[val = currentSegment.getAndIncrement]
    Inc --> CheckBounds{val <= max?}
    
    CheckBounds -- Yes --> CheckThreshold{Is 90% threshold exceeded?}
    CheckBounds -- No --> LockCheck[Acquire Swap Lock]
    
    CheckThreshold -- Yes --> CheckInFlight{Is fetch in progress?}
    CheckThreshold -- No --> ReturnVal([Return val])
    
    CheckInFlight -- Yes --> ReturnVal
    CheckInFlight -- No --> TriggerAsync[Trigger Async Block Fetch]
    TriggerAsync --> ReturnVal
    
    LockCheck --> Recheck[Re-check currentSegment exhausted]
    Recheck -- Yes --> HasNextReady{Is nextSegment ready?}
    HasNextReady -- Yes --> SwapBuffer[currentSegment = nextSegment]
    HasNextReady -- No --> SyncFetch[Synchronously fetch new Segment]
    
    SwapBuffer --> ReleaseLock[Release Lock]
    SyncFetch --> ReleaseLock
    
    ReleaseLock --> LoopBack[Loop Back to top]
```

## 3. Component Diagram
Structural view of how the dual-buffer generator decouples the application thread from the database infrastructure using the memory layout interface abstract.

```mermaid
flowchart TD
    subgraph ClientApp [Client Application]
        AppThreads[Concurrent App Threads]
    end

    subgraph LeafModule [leaf-segment Module]
        LGen[LeafSegmentIdGenerator]
        SegmentA[Segment Buffer A Primary]
        SegmentB[Segment Buffer B Secondary]
        AsyncExecutor[Async ThreadPool]
    end

    subgraph Dependency [Integration Layer]
        BlockFetcher[IdBlockFetcher Interface]
    end

    subgraph DBInfra [Database Infrastructure]
        DB[(Global ID Allocator Table)]
    end

    AppThreads -->|Calls generate| LGen
    LGen -->|Reads| SegmentA
    
    LGen -->|Triggers at 90%| AsyncExecutor
    AsyncExecutor -->|Pre-fetches into| SegmentB
    
    LGen -->|When exhausted| SegmentA
    SegmentA -.->|Swaps Active Buffer| SegmentB
    
    AsyncExecutor -->|Calls| BlockFetcher
    LGen -->|Sync Failover Calls| BlockFetcher
    
    BlockFetcher -->|Network Queries| DB
```
