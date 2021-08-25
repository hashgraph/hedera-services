package com.hedera.services.state.jasperdb.collections;

import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A super simple list of hashes with random access. It acts like a very simple long to hash map. It is intentionally
 * not a java util map or list as by keeping it simple it can be highly optimized performance wise.
 *
 * It is designed for concurrent access but is not atomic for writes, so two threads writing for same index would get
 * unpredictable results. This is acceptable in our use case as there is only ever one archive thread writing to hash
 * list and no readers will be reading from where we are writing to as those indexes are covered by in memory cache.
 *
 * Memory wise if we just stored an array of Hash objects they would take:
 * 32 bytes for object header + byte[] pointer + digest type pointer
 * + 16 bytes for byte[] object header + 4 bytes for byte[] length
 * + data size of 384 bits = 48 bytes.
 * So total of 100 bytes so over 2x the memory storage of just storing the digest bytes,
 */
public interface HashList {
    /**
     * Get hash for a node with given index
     *
     * @param index the index to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    Hash get(long index) throws IOException;

    /**
     * Put a hash at given index
     *
     * @param index the index of the node to save hash for, if nothing has been stored for this index before it will be created.
     * @param hash  a non-null hash to write
     */
    void put(long index, Hash hash);
}
