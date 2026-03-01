package com.distributed.idgen.leafsegment;

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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("LeafSegmentIdGenerator integration")
class LeafSegmentIdGeneratorIntegrationTest {

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
            statement.execute("DROP TABLE IF EXISTS leaf_alloc");
            statement.execute("CREATE TABLE leaf_alloc (biz_tag TEXT PRIMARY KEY, max_id BIGINT NOT NULL)");
        }
    }

    @Test
    @DisplayName("Should allocate unique contiguous IDs across two generators sharing PostgreSQL state")
    void shouldAllocateUniqueContiguousIdsAcrossGenerators() throws Exception {
        LeafSegmentIdGenerator generatorOne =
                new LeafSegmentIdGenerator(new PostgresIdBlockFetcher(dataSource), "orders", 50, 0.5f);
        LeafSegmentIdGenerator generatorTwo =
                new LeafSegmentIdGenerator(new PostgresIdBlockFetcher(dataSource), "orders", 50, 0.5f);

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<List<Long>>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> generateIds(startLatch, generatorOne, 100)));
        futures.add(executor.submit(() -> generateIds(startLatch, generatorTwo, 100)));

        startLatch.countDown();

        NavigableSet<Long> ids = new ConcurrentSkipListSet<>();
        for (Future<List<Long>> future : futures) {
            ids.addAll(future.get());
        }
        executor.shutdown();

        assertThat(ids).hasSize(200);
        assertThat(ids.first()).isEqualTo(1L);
        assertThat(ids.last()).isEqualTo(200L);
    }

    @Test
    @DisplayName("Should persist reserved upper bound in PostgreSQL for a business tag")
    void shouldPersistReservedUpperBound() throws Exception {
        LeafSegmentIdGenerator generator =
                new LeafSegmentIdGenerator(new PostgresIdBlockFetcher(dataSource), "payments", 50, 1.1f);

        for (int i = 0; i < 120; i++) {
            generator.generate();
        }

        assertThat(currentMaxId("payments")).isEqualTo(150L);
    }

    private static List<Long> generateIds(CountDownLatch startLatch, LeafSegmentIdGenerator generator, int count)
            throws Exception {
        startLatch.await();
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(generator.generate());
        }
        return ids;
    }

    private long currentMaxId(String bizTag) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement("SELECT max_id FROM leaf_alloc WHERE biz_tag = ?")) {
            statement.setString(1, bizTag);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static final class PostgresIdBlockFetcher implements IdBlockFetcher {

        private static final String RESERVE_SQL = """
                INSERT INTO leaf_alloc (biz_tag, max_id)
                VALUES (?, ?)
                ON CONFLICT (biz_tag)
                DO UPDATE SET max_id = leaf_alloc.max_id + EXCLUDED.max_id
                RETURNING max_id
                """;

        private final DataSource dataSource;

        private PostgresIdBlockFetcher(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public long[] fetchAndReturnNextBlock(String bizTag, int stepCap) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(RESERVE_SQL)) {
                statement.setString(1, bizTag);
                statement.setLong(2, stepCap);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("No range returned for bizTag " + bizTag);
                    }
                    long newMax = resultSet.getLong(1);
                    return new long[] {newMax - stepCap + 1, newMax};
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to reserve block for bizTag " + bizTag, e);
            }
        }
    }
}
