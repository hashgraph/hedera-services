package com.hedera.services.state.merkle.virtual.persistence;

import com.swirlds.common.FastCopyable;
import com.swirlds.fcmap.VKey;

import java.util.function.LongSupplier;

/**
 * Interface for a fast copyable index from Key to slot long.
 *
 * @param <K> type for key into index
 */
public interface FCSlotIndex<K extends VKey> extends FastCopyable {
    /** Special Location for when not found */
    public static final long NOT_FOUND_LOCATION = Long.MAX_VALUE;

    public long getSlot(K key);

    public long getSlotIfAbsentPut(K key, LongSupplier newValueSupplier);

    public void putSlot(K key, long slot);

    public long removeSlot(K key);

    public int keyCount();

    @Override
    FCSlotIndex<K> copy();
}
