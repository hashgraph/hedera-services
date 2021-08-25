package com.hedera.services.state.jasperdb.collections;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of hashes, it stores them in 46Mb direct buffers and adds buffers as needed
 *
 * TODO This differs from  interacts with Hash objects using VarHandle to directly access internal byte[] in hash object
 * TODO in attempt to get better performance. This can be replaces with a method on Hash class but will need a change
 * TODO in Swirlds.
 */
@SuppressWarnings("unused")
public final class HashListHeapArrays implements HashList {
    /** The size in bytes for a serialized hash. TODO this should be better defined somewhere */
    private static final int HASH_SIZE =  DigestType.SHA_384.digestLength();
    /** How many hashes to store in each buffer we allocate */
    private static final int NUM_HASHES_PER_CHUNK = 1_000_000;
    /** How much RAM is needed to store one buffer of hashes */
    private static final int MEMORY_CHUNK_SIZE = NUM_HASHES_PER_CHUNK*HASH_SIZE;
    /** Copy-On-Write list of buffers of data */
    private final List<byte[]> data = new CopyOnWriteArrayList<>();
    /** The current maximum index that can be stored */
    private final AtomicLong maxIndexThatCanBeStored = new AtomicLong(-1);

    /** VarHandle to the hash byte[] field in the Hash class */
    private final VarHandle hashField;

    /**
     * Create new HashListHeapArrays
     */
    public HashListHeapArrays() {
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
            byte[] buffer = data.get(bufferIndex);
            Hash hash =  new Hash(DigestType.SHA_384);
            byte[] hashValue = (byte[]) hashField.get(hash);
            System.arraycopy(buffer,offset,hashValue,0,hashValue.length);
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
                data.add(new byte[MEMORY_CHUNK_SIZE]);
                currentValue += NUM_HASHES_PER_CHUNK;
            }
            return currentValue;
        });
        // get the right buffer
        int bufferIndex = (int) (index / (long) NUM_HASHES_PER_CHUNK);
        int subIndex = (int) (index % NUM_HASHES_PER_CHUNK);
        int offset = HASH_SIZE * subIndex;
        byte[] buffer = data.get(bufferIndex);
        // get bytes from hash and write them
        byte[] hashValue = (byte[]) hashField.get(hash);
        System.arraycopy(hashValue,0,buffer,offset,hashValue.length);
    }

    /**
     * toString for debugging
     */
    @Override
    public String toString() {
        return "HashListHeapArrays{" +
                "num of chunks=" + data.size() +
                ", maxIndexThatCanBeStored=" + maxIndexThatCanBeStored +
                '}';
    }
}