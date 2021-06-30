package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.swirlds.fchashmap.FCHashMap;

/**
 * An implementation of FCSlotIndex using FCHashMap
 *
 * @param <K> data type for key
 */
public class FCSlotIndexUsingFCHashMap<K> implements FCSlotIndex<K> {
    private FCHashMap<K,Long> map;

    public FCSlotIndexUsingFCHashMap() {
        this.map = map = new FCHashMap<>();
    }

    public FCSlotIndexUsingFCHashMap(FCSlotIndexUsingFCHashMap<K> toCopy) {
        map = toCopy.map.copy();
    }

    @Override
    public long getSlot(K key) {
        return map.getOrDefault(key, FCSlotIndex.NOT_FOUND_LOCATION);
    }

    @Override
    public void putSlot(K key, long slot) {
        map.put(key, slot);
    }

    @Override
    public long removeSlot(K key) {
        Long removedIndex = map.remove(key);
        return removedIndex == null ? NOT_FOUND_LOCATION : removedIndex;
    }

    @Override
    public int keyCount() {
        return map.size();
    }

    @Override
    public FCSlotIndexUsingFCHashMap<K> copy() {
        return new FCSlotIndexUsingFCHashMap<>(this);
    }

    @Override
    public boolean isImmutable() {
        return map.isImmutable();
    }

    @Override
    public void release() {
        map.release();
    }

    @Override
    public boolean isReleased() {
        return map.isReleased();
    }
}
