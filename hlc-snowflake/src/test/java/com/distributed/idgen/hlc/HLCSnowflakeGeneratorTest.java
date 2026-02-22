package com.distributed.idgen.hlc;

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

@DisplayName("HLCSnowflakeGenerator")
class HLCSnowflakeGeneratorTest {

    private HLCSnowflakeGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HLCSnowflakeGenerator(5); // worker = 5
    }

    // -----------------------------------------------------------------------
    // HybridLogicalClock record
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("HybridLogicalClock")
    class HLCTest {

        @Test
        @DisplayName("Should pack and unpack correctly")
        void shouldPackAndUnpack() {
            var hlc = new HybridLogicalClock(1234567890L, 42);
            long packed = hlc.pack();
            var unpacked = HybridLogicalClock.unpack(packed);

            assertThat(unpacked.physicalTime()).isEqualTo(1234567890L);
            assertThat(unpacked.logicalCount()).isEqualTo(42);
        }

        @Test
        @DisplayName("Pack of zero should be zero")
        void packZeroShouldBeZero() {
            var hlc = new HybridLogicalClock(0, 0);
            assertThat(hlc.pack()).isZero();
        }

        @Test
        @DisplayName("HLC.of() should have logical counter 0")
        void ofShouldHaveZeroLogicalCount() {
            var hlc = HybridLogicalClock.of(999L);
            assertThat(hlc.physicalTime()).isEqualTo(999L);
            assertThat(hlc.logicalCount()).isZero();
        }
    }

    // -----------------------------------------------------------------------
    // HLC advancement logic
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("HLC advancement")
    class AdvancementTest {

        @Test
        @DisplayName("Physical time advance resets logical counter to 0")
        void physicalTimeAdvanceShouldResetCounter() {
            var current = new HybridLogicalClock(1000L, 50);
            var advanced = generator.advance(current, 1001L); // pt > stored

            assertThat(advanced.physicalTime()).isEqualTo(1001L);
            assertThat(advanced.logicalCount()).isZero();
        }

        @Test
        @DisplayName("Same physical time increments logical counter")
        void sameTimeShouldIncrementCounter() {
            var current = new HybridLogicalClock(1000L, 10);
            var advanced = generator.advance(current, 1000L); // pt == stored

            assertThat(advanced.physicalTime()).isEqualTo(1000L);
            assertThat(advanced.logicalCount()).isEqualTo(11);
        }

        @Test
        @DisplayName("Clock drift (pt < stored) increments counter without regressing physical time")
        void clockDriftShouldIncrementCounterNotRollBackTime() {
            var current = new HybridLogicalClock(1000L, 5);
            var advanced = generator.advance(current, 999L); // pt < stored — drift!

            // Physical time must NOT regress
            assertThat(advanced.physicalTime()).isGreaterThanOrEqualTo(1000L);
            // Logical counter should tick up
            assertThat(advanced.logicalCount()).isGreaterThan(5);
        }
    }

    // -----------------------------------------------------------------------
    // ID generation properties
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ID generation")
    class IdGenerationTest {

        @Test
        @DisplayName("Should generate a positive ID")
        void shouldGeneratePositiveId() {
            assertThat(generator.generate()).isPositive();
        }

        @Test
        @DisplayName("Should generate monotonically increasing IDs")
        void shouldBeMonotonic() {
            long prev = generator.generate();
            for (int i = 0; i < 100; i++) {
                long next = generator.generate();
                assertThat(next).isGreaterThanOrEqualTo(prev);
                prev = next;
            }
        }

        @RepeatedTest(5)
        @DisplayName("Should generate 1,000 unique IDs per run")
        void shouldGenerateUniqueIds() {
            int count = 1_000;
            Set<Long> ids = new HashSet<>(count);
            for (int i = 0; i < count; i++) {
                ids.add(generator.generate());
            }
            assertThat(ids).hasSize(count);
        }

        @Test
        @DisplayName("Should remain resilient when clock drifts backward")
        void shouldRemainResilientOnClockDrift() {
            // Simulate a generator whose clock drifts back
            HLCSnowflakeGenerator driftGen = new HLCSnowflakeGenerator(0) {
                private int callCount = 0;

                @Override
                long currentEpochMillis() {
                    // Normal time → drift → normal again
                    if (callCount < 100)
                        return ++callCount * 1L;
                    if (callCount < 200) {
                        callCount++;
                        return 50L;
                    } // drift back
                    return ++callCount * 1L;
                }
            };

            // Must NOT throw — this is the core HLC guarantee
            assertThatNoException().isThrownBy(() -> {
                Set<Long> ids = new HashSet<>();
                for (int i = 0; i < 200; i++) {
                    ids.add(driftGen.generate());
                }
                assertThat(ids).hasSize(200); // all unique despite drift
            });
        }
    }

    // -----------------------------------------------------------------------
    // Bit layout and parsing
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Parse / bit layout")
    class ParseTest {

        @Test
        @DisplayName("Parsed worker ID should match generator's worker ID")
        void parsedWorkerIdShouldMatch() {
            var gen = new HLCSnowflakeGenerator(7);
            long id = gen.generate();
            var components = HLCSnowflakeGenerator.parse(id);
            assertThat(components.workerId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Parsed timestamp should be close to current time")
        void parsedTimestampShouldBeRecent() {
            long before = System.currentTimeMillis();
            long id = generator.generate();
            long after = System.currentTimeMillis();

            var components = HLCSnowflakeGenerator.parse(id);
            assertThat(components.epochMillis())
                    .isGreaterThanOrEqualTo(before)
                    .isLessThanOrEqualTo(after + 10);
        }

        @Test
        @DisplayName("Sequence should be within 0–65535")
        void sequenceShouldBeInRange() {
            for (int i = 0; i < 50; i++) {
                var c = HLCSnowflakeGenerator.parse(generator.generate());
                assertThat(c.sequence()).isBetween(0L, 65535L);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Remote timestamp reception
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Remote timestamp reception")
    class RemoteTimestampTest {

        @Test
        @DisplayName("Should advance HLC when receiving a future remote timestamp")
        void shouldAdvanceOnFutureRemoteTimestamp() {
            long currentHlcTime = generator.getCurrentHlc().physicalTime();
            // Create a remote HLC 10 seconds in the future
            long remoteTs = (currentHlcTime + 10_000L) << 16;
            generator.receiveRemoteTimestamp(remoteTs);

            assertThat(generator.getCurrentHlc().physicalTime())
                    .isGreaterThanOrEqualTo(currentHlcTime);
        }
    }

    // -----------------------------------------------------------------------
    // Concurrency
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Concurrency")
    class ConcurrencyTest {

        @Test
        @DisplayName("Should generate unique IDs from multiple concurrent threads")
        void shouldGenerateUniqueIdsFromMultipleThreads() throws Exception {
            int threads = 8;
            int idsPerThread = 500;
            int total = threads * idsPerThread;

            ExecutorService exec = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                futures.add(exec.submit(() -> {
                    latch.await();
                    List<Long> result = new ArrayList<>(idsPerThread);
                    for (int i = 0; i < idsPerThread; i++) {
                        result.add(generator.generate());
                    }
                    return result;
                }));
            }

            latch.countDown();
            Set<Long> all = new HashSet<>(total);
            for (Future<List<Long>> f : futures) {
                all.addAll(f.get());
            }
            exec.shutdown();

            assertThat(all).hasSize(total);
        }
    }

    @Test
    @DisplayName("strategyName should mention HLC")
    void strategyNameShouldMentionHLC() {
        assertThat(generator.strategyName()).contains("HLC");
    }
}
