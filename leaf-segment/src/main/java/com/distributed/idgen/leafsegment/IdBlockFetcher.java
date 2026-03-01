package com.distributed.idgen.leafsegment;

/**
 * Interface defining the external dependency to fetch a block of IDs
 * and return its bounds.
 */
public interface IdBlockFetcher {
    /**
     * Atomically claims the next block of IDs from the persistent storage
     * for a given business tag.
     *
     * @param bizTag  The string identifier/tag for the ID sequence.
     * @param stepCap The size of the block to request.
     * @return An array of two Longs: [StartOfBlock, EndOfBlockInclusive]
     */
    long[] fetchAndReturnNextBlock(String bizTag, int stepCap);
}
