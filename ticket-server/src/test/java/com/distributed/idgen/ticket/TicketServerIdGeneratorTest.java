package com.distributed.idgen.ticket;

import com.distributed.idgen.common.IdGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TicketServerIdGeneratorTest {

    private DataSource dataSource;
    private Connection connection;
    private PreparedStatement updateStmt;
    private PreparedStatement selectStmt;
    private ResultSet resultSet;

    private TicketServerIdGenerator generator;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        updateStmt = mock(PreparedStatement.class);
        selectStmt = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(updateStmt)
                .thenReturn(selectStmt);
        when(selectStmt.executeQuery()).thenReturn(resultSet);

        generator = new TicketServerIdGenerator(dataSource);
    }

    @Test
    void testGenerateSuccessfully() throws SQLException {
        when(connection.getAutoCommit()).thenReturn(true);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong(1)).thenReturn(42L);

        Long id = generator.generate();

        assertEquals(42L, id);
        
        // Inside try
        verify(connection).setAutoCommit(false);
        verify(updateStmt).executeUpdate();
        verify(selectStmt).executeQuery();
        verify(connection).commit();
        // Inside finally
        verify(connection).setAutoCommit(true); 
    }

    @Test
    void testGenerateFailsWhenNoResult() throws SQLException {
        when(resultSet.next()).thenReturn(false);

        IdGenerationException ex = assertThrows(IdGenerationException.class, () -> generator.generate());
        assertTrue(ex.getMessage().contains("No ID was returned"));

        verify(connection).rollback();
    }

    @Test
    void testGenerateFailsOnSqlException() throws SQLException {
        when(updateStmt.executeUpdate()).thenThrow(new SQLException("DB Down"));

        IdGenerationException ex = assertThrows(IdGenerationException.class, () -> generator.generate());
        assertTrue(ex.getMessage().contains("Failed to generate ID"));

        verify(connection).rollback();
    }
}
