# ticket-server Diagrams

This document illustrates the internal architecture, database interaction flow, and component structure of the `ticket-server` generator module. This module generates perfect sequential 64-bit strictly numeric IDs (1, 2, 3...) utilizing a standard relational database's auto-increment feature with a single table constraint.

## 1. Sequence Diagram: Initialization and Generation
This diagram shows how a client application initializes `TicketServerIdGenerator` and requests IDs. For each ID requested, the generator establishes a database transaction, executes the UPSERT (`REPLACE INTO`), fetches the returned ID, commits the transaction, and returns the result.

```mermaid
sequenceDiagram
    participant App as Client App
    participant TSGen as TicketServerIdGenerator
    participant Pool as Connection / DataSource
    participant DB as Relational Database (e.g., MySQL)

    %% Initialization Phase
    App->>+TSGen: new TicketServerIdGenerator(dataSource)
    Note over TSGen: Configures updateSql: REPLACE INTO Tickets64...<br/>selectSql: SELECT LAST_INSERT_ID()
    TSGen-->>-App: Ready for Generation

    %% ID Generation Phase
    App->>+TSGen: generate()
    
    TSGen->>+Pool: getConnection()
    Pool-->>-TSGen: Connection
    
    TSGen->>Pool: setAutoCommit(false)
    
    %% UPSERT Phase
    TSGen->>+DB: executeUpdate("REPLACE INTO Tickets64 ...")
    Note over DB: Database Engine arbitrates collision<br/>Increments sequence atomically
    DB-->>-TSGen: affectedRows
    
    %% Select Phase
    TSGen->>+DB: executeQuery("SELECT LAST_INSERT_ID()")
    DB-->>-TSGen: ResultSet [id]
    
    %% Commit Phase
    TSGen->>DB: commit()
    TSGen->>Pool: setAutoCommit(true)
    TSGen->>Pool: close() connection (Return to pool)
    
    TSGen-->>-App: 64-bit Monotonic ID (e.g. 523)
```

## 2. Flowchart: Database ID Reservation Algorithm
This flowchart details the algorithm used by `TicketServerIdGenerator` when `.generate()` is invoked. It emphasizes the strict deterministic transactional boundary surrounding the two essential SQL statements.

```mermaid
flowchart TD
    Start([Start ID Generation]) --> GetConn[Get Database Connection]
    GetConn --> BeginTxn[Disable Auto-Commit]
    
    BeginTxn --> ExecuteUpdate[Execute Update/UPSERT SQL]
    
    ExecuteUpdate --> CheckUpdate{Update Success?}
    
    CheckUpdate -- Yes --> ExecuteSelect[Execute Select SQL]
    CheckUpdate -- No --> RollbackErr1[Rollback Transaction]
    RollbackErr1 --> ThrowErr1[Throw IdGenerationException]
    
    ExecuteSelect --> CheckSelect{Row Found?}
    
    CheckSelect -- Yes --> ExtractID[Extract Generated ID]
    ExtractID --> CommitTxn[Commit Transaction]
    CommitTxn --> RestoreAC[Restore Auto-Commit]
    RestoreAC --> ReturnID([Return ID])
    
    CheckSelect -- No --> RollbackErr2[Rollback Transaction]
    RollbackErr2 --> ThrowErr2[Throw IdGenerationException: No ID returned]
    
    ThrowErr1 --> RestoreACErr[Restore Auto-Commit]
    ThrowErr2 --> RestoreACErr
    
    RestoreACErr --> EndErr([Error: ID Generation Failed])
```

## 3. Component Diagram
This flowchart structurally outlines the `ticket-server` architecture. Notice how the core engine acts primarily as a network proxy funneling requests straight toward a centralized database engine orchestrator.

```mermaid
flowchart TD
    subgraph ClientApp [Client Application]
        AppService[Application Thread]
    end

    subgraph TicketServerModule [ticket-server Module]
        TSGen[TicketServerIdGenerator]
    end

    subgraph JDBC [JDBC API]
        DataSource[javax.sql.DataSource]
        Connection[java.sql.Connection]
        PreparedStatement[java.sql.PreparedStatement]
        ResultSet[java.sql.ResultSet]
    end

    subgraph DBInfra [Database Infrastructure]
        DB[(Relational DB Master Node)]
        Table[Tickets64 Table]
    end

    AppService -->|Calls .generate| TSGen
    TSGen -->|Requires| DataSource
    
    DataSource -->|Provides| Connection
    TSGen -->|Manages Txn on| Connection
    
    Connection -->|Creates| PreparedStatement
    PreparedStatement -->|Returns| ResultSet
    
    PreparedStatement -->|Executes SQL over Network| DB
    DB -->|Relies on sequence constraint| Table
```
