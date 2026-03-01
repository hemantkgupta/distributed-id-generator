package com.distributed.idgen.leafsegment;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class LeafSegmentIdGeneratorTest {

    static class MockIdBlockFetcher implements IdBlockFetcher {
        private long currentMaxId = 0;
        private final boolean delayFetch;

        MockIdBlockFetcher(boolean delayFetch) {
            this.delayFetch = delayFetch;
        }

        @Override
        public synchronized long[] fetchAndReturnNextBlock(String bizTag, int stepCap) {
            if (delayFetch) {
                try {
                    Thread.sleep(50); // Simulate network DB lag
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            long start = currentMaxId + 1;
            long end = currentMaxId + stepCap;
            currentMaxId = end;
            return new long[]{start, end};
        }
    }

    @Test
    public void testSequentialSingleThread() {
        IdBlockFetcher mockFetcher = new MockIdBlockFetcher(false);
        LeafSegmentIdGenerator generator = new LeafSegmentIdGenerator(mockFetcher, "users", 100);

        for (int i = 1; i <= 250; i++) { // Wraps across 3 blocks
            long id = generator.generate();
            Assertions.assertEquals(i, id);
        }
    }

    @Test
    public void testConcurrencyWithDualBuffer() throws InterruptedException, ExecutionException {
        IdBlockFetcher mockFetcher = new MockIdBlockFetcher(true); // 50ms lag
        LeafSegmentIdGenerator generator = new LeafSegmentIdGenerator(mockFetcher, "orders", 1000, 0.5f);

        int threadCount = 10;
        int generatePerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<List<Long>>> tasks = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startLatch.await();
                List<Long> ids = new ArrayList<>();
                for (int j = 0; j < generatePerThread; j++) {
                    ids.add(generator.generate());
                }
                return ids;
            });
        }

        List<Future<List<Long>>> futures = tasks.stream()
                .map(executor::submit)
                .toList();
        startLatch.countDown();
        Set<Long> uniqueIds = new ConcurrentSkipListSet<>();

        for (Future<List<Long>> future : futures) {
            List<Long> results = future.get();
            uniqueIds.addAll(results);
        }

        executor.shutdown();

        // 10 threads * 500 = 5000 IDs
        Assertions.assertEquals(5000, uniqueIds.size(), "All generated IDs must be unique");

        long minId = uniqueIds.stream().mapToLong(v -> v).min().orElse(0);
        long maxId = uniqueIds.stream().mapToLong(v -> v).max().orElse(0);
        Assertions.assertEquals(1L, minId);
        Assertions.assertEquals(5000L, maxId);
    }
}
