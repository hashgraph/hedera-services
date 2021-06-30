package com.hedera.services.state.merkle.virtual.persistence.fcmmap;

import com.hedera.services.state.merkle.virtual.persistence.FCSlotIndex;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fcmap.VKey;

import java.io.IOException;
import java.util.function.LongSupplier;

/**
 * An implementation of FCSlotIndex using FCHashMap
 *
 * @param <K> data type for key
 */
public class FCSlotIndexUsingFCHashMap<K extends VKey> implements FCSlotIndex<K> {
    private FCHashMap<K,Long> map;

    public FCSlotIndexUsingFCHashMap() {
        this.map = new FCHashMap<>();
    }

    public FCSlotIndexUsingFCHashMap(FCSlotIndexUsingFCHashMap<K> toCopy) {
        map = toCopy.map.copy();
    }

    @Override
    public long getSlot(K key) throws IOException {
        return map.getOrDefault(key, FCSlotIndex.NOT_FOUND_LOCATION);
    }

    @Override
    public long getSlotIfAbsentPut(K key, LongSupplier newValueSupplier) {
        // TODO not sure how to make this thread safe ?
        long value =  map.getOrDefault(key, FCSlotIndex.NOT_FOUND_LOCATION);
        if (value == FCSlotIndex.NOT_FOUND_LOCATION) {
            value = newValueSupplier.getAsLong();
            map.put(key, value);
        }
        return value;
    }

    @Override
    public void putSlot(K key, long slot) throws IOException {
        map.put(key, slot);
    }

    @Override
    public long removeSlot(K key) throws IOException {
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

    @Override
    public Object acquireWriteLock(int keyHash) {
        return null;
    }

    @Override
    public void releaseWriteLock(int keyHash, Object lockStamp) {

    }

    @Override
    public Object acquireReadLock(int keyHash) {
        return null;
    }

    @Override
    public void releaseReadLock(int keyHash, Object lockStamp) {

    }
}
