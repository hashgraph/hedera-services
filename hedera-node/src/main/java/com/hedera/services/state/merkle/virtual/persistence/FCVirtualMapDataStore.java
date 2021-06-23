package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;

import java.io.IOException;

/**
 * Interface for Fast Copyable Virtual Map Data Store
 *
 * It should be thread safe and can be used by multiple-threads.
 *
 * @param <PK> The type for parents keys, must implement SelfSerializable
 * @param <PD> The type for parents data, must implement SelfSerializable
 * @param <LP> The type for leaf paths, must implement SelfSerializable
 * @param <LK> The type for leaf keys, must implement SelfSerializable
 * @param <LD> The type for leaf data, must implement SelfSerializable
 */
public interface FCVirtualMapDataStore<PK extends SelfSerializable, PD extends SelfSerializable,
        LK extends SelfSerializable, LP extends SelfSerializable, LD extends SelfSerializable>
        extends FastCopyable<FCVirtualMapDataStore<PK, PD, LK, LP, LD>> {

    /** Open storage */
    void open();

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
     * Check if this store contains a parent by key
     *
     * @param parentKey The key of the parent to check for
     * @return true if that parent is stored, false if it is not known
     */
    boolean containsParentKey(PK parentKey);

    /**
     * Delete a stored parent from storage, if it is stored.
     *
     * @param parentKey The key of the parent to delete
     */
    void deleteParent(PK parentKey);

    /**
     * Load a tree parent node from storage
     *
     * @param parentKey The key of the parent to find and load
     * @return a loaded VirtualTreeInternal with path and hash set or null if not found
     */
    PD loadParent(PK parentKey) throws IOException;

    /**
     * Save the hash for a imaginary parent node into storage
     *
     * @param parentKey The key of the parent to save
     * @param parentData The parent's data to store
     */
    void saveParentHash(PK parentKey, PD parentData) throws IOException;
}
