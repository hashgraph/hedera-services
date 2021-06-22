package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.common.FastCopyable;

/**
 * Interface for a fast copyable index from Key to slot long.
 *
 * @param <K> type for key into index
 */
public interface FCSlotIndex<K> extends FastCopyable<FCSlotIndex<K>> {
    /** Special Location for when not found */
    public static final long NOT_FOUND_LOCATION = Long.MAX_VALUE;

    public long getSlot(K key);

    public void putSlot(K key, long slot);

    public long removeSlot(K key);

    public int keyCount();
}
