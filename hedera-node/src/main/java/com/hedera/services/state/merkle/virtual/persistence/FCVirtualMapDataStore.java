package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;

/**
 * Interface for Fast Copyable Virtual Map Data Store
 *
 * It should be thread safe and can be used by multiple-threads.
 *
 * @param <PK> The type for hashs keys, must implement SelfSerializable
 * @param <LP> The type for leaf paths, must implement SelfSerializable
 * @param <LK> The type for leaf keys, must implement SelfSerializable
 * @param <LD> The type for leaf data, must implement SelfSerializable
 */
public interface FCVirtualMapDataStore<PK extends SelfSerializable, LK extends SelfSerializable,
        LP extends SelfSerializable, LD extends SelfSerializable>
        extends FastCopyable {

    /** Open storage */
    void open() throws IOException;

    /** Sync any changes to disk, used during testing */
    void sync();


    /**
     * Delete a stored leaf from storage, if it is stored.
     *
     * @param leafKey The key for the leaf to delete
     * @param leafPath The path for the leaf to delete
     */
    void deleteLeaf(LK leafKey, LP leafPath);

    /**
     * Check if this store contains a leaf by key
     *
     * @param leafKey The key of the leaf to check for
     * @return true if that leaf is stored, false if it is not known
     */
    boolean containsLeafKey(LK leafKey);

    /**
     * Get the number of leaves for a given account
     *
     * @return 0 if the account doesn't exist otherwise the number of leaves stored for the account
     */
    int leafCount();

    /**
     * Load a leaf node record from storage given the key for it
     *
     * @param key The key of the leaf to find
     * @return a loaded leaf data or null if not found
     */
    LD loadLeafByKey(LK key) throws IOException;

    /**
     * Load a leaf node record from storage given a path to it
     *
     * @param leafPath The path to the leaf
     * @return a loaded leaf data or null if not found
     */
    LD loadLeafByPath(LP leafPath) throws IOException;

    /**
     * Save a VirtualTreeLeaf to storage
     *
     * @param leafKey The key for the leaf to store
     * @param leafPath The path for the leaf to store
     * @param leafData The data for the leaf to store
     */
    void saveLeaf(LK leafKey, LP leafPath, LD leafData) throws IOException;

    /**
     * Check if this store contains a hash by key
     *
     * @param hashKey The key of the hash to check for
     * @return true if that hash is stored, false if it is not known
     */
    boolean containsHash(PK hashKey);

    /**
     * Delete a stored hash from storage, if it is stored.
     *
     * @param hashKey The key of the hash to delete
     */
    void deleteHash(PK hashKey);

    /**
     * Load a tree hash node from storage
     *
     * @param hashKey The key of the hash to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    Hash loadHash(PK hashKey) throws IOException;

    /**
     * Save the hash for a imaginary hash node into storage
     *
     * @param hashKey The key of the hash to save
     * @param hashData The hash's data to store
     */
    void saveHash(PK hashKey, Hash hashData) throws IOException;

    @Override
    FCVirtualMapDataStore<PK, LK, LP, LD> copy();
}
