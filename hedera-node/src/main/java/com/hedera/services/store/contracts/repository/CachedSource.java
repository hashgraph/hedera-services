package com.hedera.services.store.contracts.repository;

import java.util.Collection;

public interface CachedSource<Key, Value> extends Source<Key, Value> {
    Source<Key, Value> getSource();

    Collection<Key> getModified();

    boolean hasModified();

    long estimateCacheSize();

    interface BytesKey<Value> extends CachedSource<byte[], Value> {
    }
}