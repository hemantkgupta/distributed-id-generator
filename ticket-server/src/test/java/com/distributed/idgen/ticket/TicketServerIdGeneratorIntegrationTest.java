package com.distributed.idgen.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("TicketServerIdGenerator integration")
class TicketServerIdGeneratorIntegrationTest {

    private static final String UPDATE_SQL = "UPDATE ticket_counter SET id = nextval('ticket_seq') WHERE stub = 'a'";
    private static final String SELECT_SQL = "SELECT id FROM ticket_counter WHERE stub = 'a'";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws SQLException {
        PGSimpleDataSource pgDataSource = new PGSimpleDataSource();
        pgDataSource.setURL(POSTGRES.getJdbcUrl());
        pgDataSource.setUser(POSTGRES.getUsername());
        pgDataSource.setPassword(POSTGRES.getPassword());
        this.dataSource = pgDataSource;

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS ticket_counter");
            statement.execute("DROP SEQUENCE IF EXISTS ticket_seq");
            statement.execute("CREATE SEQUENCE ticket_seq START WITH 1");
            statement.execute("CREATE TABLE ticket_counter (stub TEXT PRIMARY KEY, id BIGINT NOT NULL)");
            statement.execute("INSERT INTO ticket_counter(stub, id) VALUES ('a', 0)");
        }
    }

    @Test
    @DisplayName("Should generate strictly sequential IDs against a real PostgreSQL backend")
    void shouldGenerateStrictlySequentialIds() {
        TicketServerIdGenerator generator = new TicketServerIdGenerator(dataSource, UPDATE_SQL, SELECT_SQL);

        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(generator.generate());
        }

        assertThat(ids).containsExactlyElementsOf(expectedSequence(100));
    }

    @Test
    @DisplayName("Should generate unique contiguous IDs under concurrent access against PostgreSQL")
    void shouldGenerateUniqueContiguousIdsConcurrently() throws Exception {
        TicketServerIdGenerator generator = new TicketServerIdGenerator(dataSource, UPDATE_SQL, SELECT_SQL);

        int threads = 8;
        int idsPerThread = 25;
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<List<Long>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                List<Long> ids = new ArrayList<>(idsPerThread);
                for (int j = 0; j < idsPerThread; j++) {
                    ids.add(generator.generate());
                }
                return ids;
            }));
        }

        startLatch.countDown();

        Set<Long> uniqueIds = new HashSet<>();
        List<Long> allIds = new ArrayList<>(threads * idsPerThread);
        for (Future<List<Long>> future : futures) {
            List<Long> ids = future.get();
            uniqueIds.addAll(ids);
            allIds.addAll(ids);
        }
        executor.shutdown();

        allIds.sort(Long::compareTo);
        assertThat(uniqueIds).hasSize(threads * idsPerThread);
        assertThat(allIds).containsExactlyElementsOf(expectedSequence(threads * idsPerThread));
    }

    private static List<Long> expectedSequence(int count) {
        List<Long> expected = new ArrayList<>(count);
        for (long value = 1; value <= count; value++) {
            expected.add(value);
        }
        return expected;
    }
}
