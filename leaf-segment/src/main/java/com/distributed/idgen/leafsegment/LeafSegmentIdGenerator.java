package com.distributed.idgen.leafsegment;

import com.distributed.idgen.common.IdGenerationException;
import com.distributed.idgen.common.IdGenerator;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Leaf Segment ID generator uses a Dual-Buffer architecture.
 * <p>
 * It preemptively fetches blocks of IDs from a central store to avoid single-row
 * database bottlenecks on every ID generation. When the active block reaches a threshold,
 * a background thread asynchronously fetches the next block into a secondary buffer,
 * seamlessly swapping them when the primary runs out.
 */
public class LeafSegmentIdGenerator implements IdGenerator<Long> {

    private final IdBlockFetcher blockFetcher;
    private final String bizTag;
    private final int stepSize;
    
    // Configurable percentage to trigger next block pre-fetching (e.g. 0.9 = 90% consumed)
    private final float fetchThreshold;

    private volatile Segment currentSegment;

    // Locks to prevent multiple background fetches firing simultaneously
    private final ReentrantLock fetchLock = new ReentrantLock();
    // Represents the next reserved segment while it is being prefetched or waiting to be consumed.
    private volatile CompletableFuture<Segment> nextSegmentFuture;

    public LeafSegmentIdGenerator(IdBlockFetcher blockFetcher, String bizTag, int stepSize) {
        this(blockFetcher, bizTag, stepSize, 0.9f);
    }

    public LeafSegmentIdGenerator(IdBlockFetcher blockFetcher, String bizTag, int stepSize, float fetchThreshold) {
        this.blockFetcher = blockFetcher;
        this.bizTag = bizTag;
        this.stepSize = stepSize;
        this.fetchThreshold = fetchThreshold;
        
        // Initialize the first segment synchronously to guarantee immediate availability
        this.currentSegment = fetchNewSegment();
    }

    @Override
    public Long generate() {
        while (true) {
            Segment current = currentSegment;
            long val = current.value.getAndIncrement();

            if (val <= current.max) {
                // Determine if we need to kick off async fetch for the next segment
                long consumed = val - current.min + 1;
                if (nextSegmentFuture == null && consumed > (stepSize * fetchThreshold)) {
                    triggerAsyncFetch();
                }
                return val;
            }

            // Current segment exhausted. We must swap to the next one.
            fetchLock.lock();
            try {
                // Re-check state inside lock
                val = currentSegment.value.getAndIncrement();
                if (val <= currentSegment.max) {
                    return val;
                }

                if (nextSegmentFuture != null) {
                    currentSegment = consumePrefetchedSegment();
                } else {
                    currentSegment = fetchNewSegment();
                }
            } finally {
                fetchLock.unlock();
            }
        }
    }

    private void triggerAsyncFetch() {
        if (fetchLock.tryLock()) {
            try {
                if (nextSegmentFuture != null) {
                    return;
                }
                nextSegmentFuture = CompletableFuture.supplyAsync(this::fetchNewSegment);
            } finally {
                fetchLock.unlock();
            }
        }
    }

    private Segment consumePrefetchedSegment() {
        CompletableFuture<Segment> future = nextSegmentFuture;
        nextSegmentFuture = null;
        try {
            return future.join();
        } catch (CompletionException e) {
            // If the async prefetch failed, fall back to a synchronous fetch.
            return fetchNewSegment();
        }
    }

    private Segment fetchNewSegment() {
        try {
            long[] range = blockFetcher.fetchAndReturnNextBlock(bizTag, stepSize);
            return new Segment(range[0], range[1]);
        } catch (Exception e) {
            throw new IdGenerationException("Failed to fetch next Leaf Segment Block for tag: " + bizTag, e);
        }
    }

    @Override
    public String strategyName() {
        return "Leaf Segment (Dual-Buffer Block Generation)";
    }

    private static class Segment {
        final AtomicLong value;
        final long min;
        final long max;

        Segment(long min, long max) {
            this.value = new AtomicLong(min);
            this.min = min;
            this.max = max;
        }
    }
}
