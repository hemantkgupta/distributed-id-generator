package com.distributed.idgen.ticket;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Ticket Server (Flickr's Centralised DB Auto-Increment) ID generator.
 *
 * <h2>Overview</h2>
 * The Ticket Server pattern uses a relational database's native auto-increment
 * functionality to guarantee monotonically increasing, globally unique, numeric IDs.
 * It uses a `REPLACE INTO` trick (or similar UPSERT functionality) on a table with a
 * single row to continuously iterate a primary key.
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>✅ Perfectly sequential numeric IDs (1, 2, 3...)</li>
 * <li>✅ Extremely simple concept and implementation</li>
 * <li>❌ Centralised bottleneck — not truly decentralised like Snowflake or UUID</li>
 * <li>❌ Needs a database connection (Single Point of Failure)</li>
 * </ul>
 */
public class TicketServerIdGenerator implements IdGenerator<Long> {

    private final DataSource dataSource;
    private final String updateSql;
    private final String selectSql;

    /**
     * Constructs a Ticket Server ID generator using standard Flickr-style MySQL queries.
     * Table structure assumed:
     * CREATE TABLE Tickets64 ( id bigint(20) unsigned NOT NULL auto_increment, stub char(1) NOT NULL default '', PRIMARY KEY  (id), UNIQUE KEY stub (stub) )
     *
     * @param dataSource The database DataSource
     */
    public TicketServerIdGenerator(DataSource dataSource) {
        this(dataSource, "REPLACE INTO Tickets64 (stub) VALUES ('a')", "SELECT LAST_INSERT_ID()");
    }

    /**
     * Constructs a Ticket Server ID generator with custom SQL statements depending on the DB engine.
     *
     * @param dataSource The database DataSource
     * @param updateSql  The SQL to increment the sequence (e.g., REPLACE INTO, INSERT...ON CONFLICT, UPDATE)
     * @param selectSql  The SQL to retrieve the generated ID (e.g., SELECT LAST_INSERT_ID(), RETURNING id)
     */
    public TicketServerIdGenerator(DataSource dataSource, String updateSql, String selectSql) {
        this.dataSource = dataSource;
        this.updateSql = updateSql;
        this.selectSql = selectSql;
    }

    @Override
    public Long generate() {
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                 PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                
                updateStmt.executeUpdate();
                
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        conn.commit();
                        return id;
                    } else {
                        conn.rollback();
                        throw new IdGenerationException("No ID was returned by the select statement.");
                    }
                }
            } catch (SQLException e) {
                conn.rollback();
                throw new IdGenerationException("Failed to generate ID from ticket server", e);
            } finally {
                // Restore auto-commit state
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new IdGenerationException("Failed to obtain database connection", e);
        }
    }

    @Override
    public String strategyName() {
        return "Ticket Server (Central DB Auto-Increment)";
    }
}
