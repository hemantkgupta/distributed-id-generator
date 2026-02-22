package com.distributed.idgen.snowflake;

import com.distributed.idgen.common.IdGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SnowflakeIdGenerator")
class SnowflakeIdGeneratorTest {

    private SnowflakeIdGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SnowflakeIdGenerator(1, 1); // DC=1, Worker=1
    }

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Construction validation")
    class ConstructionValidation {

        @Test
        @DisplayName("Should create generator with valid datacenter and worker IDs")
        void shouldCreateWithValidIds() {
            assertThatNoException()
                    .isThrownBy(() -> new SnowflakeIdGenerator(0, 0));
            assertThatNoException()
                    .isThrownBy(() -> new SnowflakeIdGenerator(31, 31));
        }

        @Test
        @DisplayName("Should create generator with no-arg constructor (defaults to dc=0, worker=0)")
        void shouldCreateWithDefaultConstructor() {
            var gen = new SnowflakeIdGenerator();
            assertThat(gen.getDatacenterId()).isZero();
            assertThat(gen.getWorkerId()).isZero();
        }

        @Test
        @DisplayName("Should throw for datacenterId > 31")
        void shouldRejectInvalidDatacenterId() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("datacenterId");
        }

        @Test
        @DisplayName("Should throw for workerId > 31")
        void shouldRejectInvalidWorkerId() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workerId");
        }

        @Test
        @DisplayName("Should throw for negative datacenterId")
        void shouldRejectNegativeDatacenterId() {
            assertThatThrownBy(() -> new SnowflakeIdGenerator(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Basic ID generation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ID generation")
    class IdGeneration {

        @Test
        @DisplayName("Should generate a positive non-zero ID")
        void shouldGeneratePositiveId() {
            long id = generator.generate();
            assertThat(id).isPositive();
        }

        @Test
        @DisplayName("Should generate monotonically increasing IDs in sequence")
        void shouldGenerateMonotonicallyIncreasingIds() {
            long first = generator.generate();
            long second = generator.generate();
            long third = generator.generate();
            assertThat(second).isGreaterThanOrEqualTo(first);
            assertThat(third).isGreaterThanOrEqualTo(second);
        }

        @Test
        @DisplayName("Generated ID should be a positive 64-bit long (sign bit = 0)")
        void shouldAlwaysBePositive() {
            for (int i = 0; i < 1000; i++) {
                assertThat(generator.generate()).isPositive();
            }
        }

        @RepeatedTest(5)
        @DisplayName("Should generate 1000 unique IDs per run")
        void shouldGenerateUniqueIds() {
            int count = 1_000;
            Set<Long> ids = new HashSet<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(generator.generate());
            }
            assertThat(ids).hasSize(count);
        }
    }

    // -----------------------------------------------------------------------
    // Bit structure assertions
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Bit structure")
    class BitStructure {

        @Test
        @DisplayName("Sign bit should always be 0 (positive number guaranteed)")
        void signBitShouldBeZero() {
            long id = generator.generate();
            assertThat(id >> 63).isZero();
        }

        @Test
        @DisplayName("Parsed components should match generator configuration")
        void parseShouldRecoverDatacenterAndWorkerId() {
            var gen = new SnowflakeIdGenerator(7, 3);
            long id = gen.generate();
            var components = SnowflakeIdGenerator.parse(id);

            assertThat(components.datacenterId()).isEqualTo(7L);
            assertThat(components.workerId()).isEqualTo(3L);
            assertThat(components.rawId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Parsed timestamp should be close to the current epoch millis")
        void parsedTimestampShouldBeRecent() {
            long before = System.currentTimeMillis();
            long id = generator.generate();
            long after = System.currentTimeMillis();

            var components = SnowflakeIdGenerator.parse(id);
            assertThat(components.epochMillis())
                    .isGreaterThanOrEqualTo(before)
                    .isLessThanOrEqualTo(after + 5); // allow minor jitter
        }

        @Test
        @DisplayName("Parsed sequence should be non-negative and within 0-4095")
        void parsedSequenceShouldBeInRange() {
            for (int i = 0; i < 100; i++) {
                var components = SnowflakeIdGenerator.parse(generator.generate());
                assertThat(components.sequence()).isBetween(0L, 4095L);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Clock-drift protection
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Clock drift protection")
    class ClockDriftProtection {

        @Test
        @DisplayName("Should throw IdGenerationException when clock moves backwards")
        void shouldThrowWhenClockMovesBackwards() {
            // Use a subclass to simulate backwards clock
            SnowflakeIdGenerator backwardsClockGen = new SnowflakeIdGenerator(0, 0) {
                private int callCount = 0;

                @Override
                long currentEpochMillis() {
                    // First call returns a "future" timestamp; second call returns an earlier one
                    return (callCount++ == 0) ? 1000L : 999L;
                }
            };

            backwardsClockGen.generate(); // advances lastTimestamp to 1000
            assertThatThrownBy(backwardsClockGen::generate)
                    .isInstanceOf(IdGenerationException.class)
                    .hasMessageContaining("Clock moved backwards");
        }
    }

    // -----------------------------------------------------------------------
    // Concurrency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTest {

        @Test
        @DisplayName("Should generate unique IDs from multiple threads simultaneously")
        void shouldGenerateUniqueIdsFromMultipleThreads() throws Exception {
            int threads = 8;
            int idsPerThread = 500;
            int total = threads * idsPerThread;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    startLatch.await(); // burst simultaneously
                    List<Long> result = new ArrayList<>(idsPerThread);
                    for (int i = 0; i < idsPerThread; i++) {
                        result.add(generator.generate());
                    }
                    return result;
                }));
            }

            startLatch.countDown(); // release all threads at once

            Set<Long> allIds = new HashSet<>(total);
            for (Future<List<Long>> future : futures) {
                allIds.addAll(future.get());
            }
            executor.shutdown();

            assertThat(allIds).hasSize(total);
        }
    }

    // -----------------------------------------------------------------------
    // Strategy name
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("strategyName should return the correct label")
    void strategyNameShouldBeCorrect() {
        assertThat(generator.strategyName()).contains("Snowflake");
    }
}
