package com.hedera.services.state.jasperdb.collections;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of hashes, it stores them in 46Mb direct buffers and adds buffers as needed
 */
public final class HashListOffHeapPrivate implements HashList {
    /** The size in bytes for a serialized hash. TODO this should be better defined somewhere */
    private static final int HASH_SIZE =  DigestType.SHA_384.digestLength();
    /** How many hashes to store in each buffer we allocate */
    private static final int NUM_HASHES_PER_CHUNK = 1_000_000;
    /** How much RAM is needed to store one buffer of hashes */
    private static final int MEMORY_CHUNK_SIZE = NUM_HASHES_PER_CHUNK*HASH_SIZE;
    /** Copy-On-Write list of buffers of data */
    private final List<ByteBuffer> data = new CopyOnWriteArrayList<>();
    /** The current maximum index that can be stored */
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);

    private final VarHandle hashField;

    public HashListOffHeapPrivate() {
        try {
            hashField = MethodHandles
                    .privateLookupIn(Hash.class, MethodHandles.lookup())
                    .findVarHandle(Hash.class, "value", byte[].class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Get hash for a node with given index
     *
     * @param index the index to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash get(long index) throws IOException {
        if (index <= maxIndexThatCanBeStored.get()) {
            int bufferIndex = (int) (index / (long) NUM_HASHES_PER_CHUNK);
            int subIndex = (int) (index % NUM_HASHES_PER_CHUNK);
            int offset = HASH_SIZE * subIndex;
            ByteBuffer buffer = data.get(bufferIndex);
            Hash hash =  new Hash(DigestType.SHA_384);
            byte[] hashValue = (byte[]) hashField.get(hash);
            buffer.position(offset);
            buffer.get(hashValue);
            return hash;
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
    @Override
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
        int bufferIndex = (int) (index / (long) NUM_HASHES_PER_CHUNK);
        int subIndex = (int) (index % NUM_HASHES_PER_CHUNK);
        int offset = HASH_SIZE * subIndex;
        ByteBuffer buffer = data.get(bufferIndex);
        // get bytes from hash and write them
        byte[] hashValue = (byte[]) hashField.get(hash);
        buffer.position(offset);
        buffer.put(hashValue);
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