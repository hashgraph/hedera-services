package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.FastCopyable;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * Interface for a fast copyable index from Key to slot long.
 *
 * @param <K> type for key into index
 */
public interface FCSlotIndex<K extends VKey> extends FastCopyable {
    /** Special Location for when not found */
    public static final long NOT_FOUND_LOCATION = Long.MAX_VALUE;

    /**
     * Get slot index for given key
     *
     * @param key the key to find slot index for
     * @return slot index stored for key if there is one or NOT_FOUND_LOCATION if not found
     */
    long getSlot(K key) throws IOException;

    /**
     * Get slot index for given key, if nothing is stored for key then call newValueSupplier to get a slot index and
     * store it for key.
     *
     * @param key the key to get or store slot index
     * @param newValueSupplier supplier to get a slot index if nothing was stored for key
     * @return either the slot index that was stored for key or the new slot index provided by supplier
     */
    long getSlotIfAbsentPut(K key, LongSupplier newValueSupplier) throws IOException;

    /**
     * Put a slot into index at given key. This will replace an existing slot if one existed on the key.
     *
     * TODO do we need to check for a update use case and return it so that its data can be removed from slot store?
     *
     * @param key the key to store the slot index at
     * @param slot index for a slot to store for given key
     */
    void putSlot(K key, long slot) throws IOException;

    /**
     * Remove a slot from index that has given key
     *
     * @param key the key who's slot should be removed
     * @return the slot index if one was removed or NOT_FOUND_LOCATION if there was no slot stored with key
     */
    long removeSlot(K key) throws IOException;

    /**
     * Get number of keys in this version of index. This needs to be thread safe independent of read/write locks.
     *
     * @return count of keys
     */
    int keyCount();

    /**
     * Acquire a write lock for the given location
     *
     * @param keyHash the keyHash we want to be able to write to
     * @return stamp representing the lock that needs to be returned to releaseWriteLock
     */
    Object acquireWriteLock(int keyHash);

    /**
     * Release a previously acquired write lock
     *
     * @param keyHash the keyHash we are done writing to
     * @param lockStamp stamp representing the lock that you got from acquireWriteLock
     */
    void releaseWriteLock(int keyHash, Object lockStamp);

    /**
     * Acquire a read lock for the given location
     *
     * @param keyHash the keyHash we want to be able to read from
     * @return stamp representing the lock that needs to be returned to releaseReadLock
     */
    Object acquireReadLock(int keyHash);

    /**
     * Release a previously acquired read lock
     *
     * @param keyHash the keyHash we are done reading from
     * @param lockStamp stamp representing the lock that you got from acquireReadLock
     */
    void releaseReadLock(int keyHash, Object lockStamp);

    /**
     * Create fast copy of this FCSlotIndex
     *
     * @return fast versioned copy of this FCSlotIndex
     */
    @Override
    FCSlotIndex<K> copy();
}
