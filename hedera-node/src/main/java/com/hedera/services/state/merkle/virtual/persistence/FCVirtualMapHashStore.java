package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.fcmap.FCVirtualRecord;

import java.io.IOException;

/**
 * Interface for Fast Copyable Virtual Map Data Store
 *
 * It should be thread safe and can be used by multiple-threads.
 *
 * @param <HK> The type for hash keys, must implement SelfSerializable
 */
public interface FCVirtualMapHashStore<HK extends SelfSerializable>
        extends FastCopyable {

    /** Open storage, release() is used to close */
    void open() throws IOException;

    /** Sync any changes to disk, used during testing */
    void sync();

    /**
     * Check if this store contains a hash by key
     *
     * @param hashKey The key of the hash to check for
     * @return true if that hash is stored, false if it is not known
     */
    boolean containsHash(HK hashKey);

    /**
     * Delete a stored hash from storage, if it is stored.
     *
     * @param hashKey The key of the hash to delete
     */
    void deleteHash(HK hashKey);

    /**
     * Load a tree hash node from storage
     *
     * @param hashKey The key of the hash to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    Hash loadHash(HK hashKey) throws IOException;

    /**
     * Save the hash for a imaginary hash node into storage
     *
     * @param hashKey The key of the hash to save
     * @param hashData The hash's data to store
     */
    void saveHash(HK hashKey, Hash hashData) throws IOException;

    @Override
    FCVirtualMapHashStore<HK> copy();
}
