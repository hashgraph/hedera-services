package com.hedera.services.state.merkle.v3.offheap;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of hashes, it stores them in 46Mb direct buffers and adds buffers as needed
 */
public final class OffHeapHashList {
    private static final int HASH_SIZE =  Integer.BYTES+ DigestType.SHA_384.digestLength();
    private static final int NUM_HASHES_PER_CHUNK = 1_000_000;
    private static final int MEMORY_CHUNK_SIZE = NUM_HASHES_PER_CHUNK*HASH_SIZE;
    private final List<ByteBuffer> data = new CopyOnWriteArrayList<>();
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);

    /**
     * Get hash for a node with given index
     *
     * @param index the index to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    public Hash get(long index) throws IOException {
        if (index <= maxIndexThatCanBeStored.get()) {
            return Hash.fromByteBuffer(getBuffer(index));
        } else {
            return null;
        }
    }

    /**
     * Put a hash at given index
     *
     * @param index the index of the node to save hash for, if nothing has been stored for this index before it will be created.
     * @param hash a non-null hash to write
     */
    public void put(long index, Hash hash) {
        // expand data if needed
        maxIndexThatCanBeStored.updateAndGet(currentValue -> {
            while (index > currentValue) { // need to expand
                data.add(ByteBuffer.allocateDirect(MEMORY_CHUNK_SIZE));
                currentValue += NUM_HASHES_PER_CHUNK;
            }
            return currentValue;
        });
        // get the right buffer
        Hash.toByteBuffer(hash,getBuffer(index));
    }

    /**
     * Get the ByteBuffer for a given key, assumes the buffer is already created.
     *
     * @param index the index to buffer
     * @return The ByteBuffer contain that index
     */
    private ByteBuffer getBuffer(long index) {
        int bufferIndex = (int) (index / (long) NUM_HASHES_PER_CHUNK);
        ByteBuffer buffer = data.get(bufferIndex).slice();
        int subIndex = (int)(index % NUM_HASHES_PER_CHUNK);
        int offset = HASH_SIZE * subIndex;
        buffer.position(offset);
        buffer.limit(offset+HASH_SIZE);
        return buffer;
    }

    /**
     * toString for debugging
     */
    @Override
    public String toString() {
        return "OffHeapHashList{" +
                "num of chunks=" + data.size() +
                ", maxIndexThatCanBeStored=" + maxIndexThatCanBeStored +
                '}';
    }
}