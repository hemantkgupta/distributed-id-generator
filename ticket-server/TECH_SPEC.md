# ticket-server/TECH_SPEC.md

## Overview
The `ticket-server` module generates strictly numeric `Long` sequence identifiers by utilizing standard relational databases running `REPLACE INTO` commands over a singular record table constraint. Originally popularized by Flickr around 2010.

## Method & Execution
The pattern creates a centralized database table featuring an `auto_increment` primary key sequence, relying completely on the database engine to arbitrate collisions. 

```java
// Central execution loop on the transaction server
String updateSql = "REPLACE INTO Tickets64 (stub) VALUES ('a')";
String selectSql = "SELECT LAST_INSERT_ID()";
```

## Trade-offs
- **Monotonic Sequence Perfection**: The only module in this library capable of generating an absolutely perfect chronological series `[1, 2, 3, 4, 5...]` without leaving numeric fragmentation holes (unless transactions rollback gracefully on the engine).
- **Single Point Of Failure**: Moves the ID coordination away from the independent microservice and funnels it completely down an unreliable network connection toward an overworked singleton database master node.
- **Micro-batching Strategy Loss**: The basic module issues an identical HTTP database network request for every single identifier request, resulting in substantial network lag under scale. 

## Implementation Specifics
The `TicketServerIdGenerator` utilizes a standard Java `DataSource` wrapper and intentionally turns off JDBC auto-commit functionality. This forces explicit `conn.commit()` transaction locks, ensuring deterministic SQL output results directly inside `.generate()` block boundaries. An `IdGenerationException` translates any generic SQLExceptions directly.
