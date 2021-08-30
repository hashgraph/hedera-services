package com.hedera.services.state.merkle.v2.persistance;

import com.swirlds.virtualmap.VirtualKey;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A very simple implementation of LongIndex that is backed by a ConcurrentHashMap
 *
 * @param <K> the type for keys
 */
public class LongIndexInMemory<K extends VirtualKey> implements LongIndex<K> {
    private final ConcurrentHashMap<K,Long> map = new ConcurrentHashMap<>();

    @Override
    public void put(K key, long value) {
        map.put(key,value);
    }

    @Override
    public Long get(K key) {
        return map.get(key);
    }

    @Override
    public Long remove(K key) {
        return map.remove(key);
    }

    @Override
    public void close() throws IOException {map.clear();}
}
