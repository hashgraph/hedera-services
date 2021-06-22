package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.swirlds.fchashmap.FCHashMap;

/**
 * An implementation of FCSlotIndex using FCHashMap
 *
 * @param <K> data type for key
 */
public class FCHashMapFCSlotIndex<K> implements FCSlotIndex<K> {
    private FCHashMap<K,Long> map;

    public FCHashMapFCSlotIndex() {
        this.map = map = new FCHashMap<>();
    }

    public FCHashMapFCSlotIndex(FCHashMapFCSlotIndex<K> toCopy) {
        map = toCopy.map.copy();
    }

    @Override
    public long getSlot(K key) {
        return map.get(key);
    }

    @Override
    public void putSlot(K key, long slot) {
        map.put(key, slot);
    }

    @Override
    public long removeSlot(K key) {
        return map.remove(key);
    }

    @Override
    public int keyCount() {
        return map.size();
    }

    @Override
    public FCSlotIndex<K> copy() {
        return new FCHashMapFCSlotIndex<>(this);
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
