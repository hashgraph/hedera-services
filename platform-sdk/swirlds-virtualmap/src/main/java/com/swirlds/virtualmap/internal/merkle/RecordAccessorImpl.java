/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.virtualmap.internal.merkle;

import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Implementation of {@link RecordAccessor} which, given a state, cache, and data source, provides access
 * to all records.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public class RecordAccessorImpl<K extends VirtualKey, V extends VirtualValue> implements RecordAccessor<K, V> {
    private final VirtualStateAccessor state;
    private final VirtualNodeCache<K, V> cache;
    private final VirtualDataSource<K, V> dataSource;

    /**
     * Create a new {@link RecordAccessorImpl}.
     *
     * @param state
     * 		The state. Cannot be null.
     * @param cache
     * 		The cache. Cannot be null.
     * @param dataSource
     * 		The data source. Can be null.
     */
    public RecordAccessorImpl(
            VirtualStateAccessor state, VirtualNodeCache<K, V> cache, VirtualDataSource<K, V> dataSource) {
        this.state = Objects.requireNonNull(state);
        this.cache = Objects.requireNonNull(cache);
        this.dataSource = dataSource;
    }

    /**
     * Gets the state.
     *
     * @return The state. This will never be null.
     */
    public VirtualStateAccessor getState() {
        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualNodeCache<K, V> getCache() {
        return cache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualDataSource<K, V> getDataSource() {
        return dataSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualRecord findRecord(final long path) {
        assert path != INVALID_PATH;
        if (path >= ROOT_PATH && path < state.getFirstLeafPath()) {
            return findInternalRecord(path);
        } else {
            assert path >= state.getFirstLeafPath();
            return findLeafRecord(path, false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualInternalRecord findInternalRecord(final long path) {
        assert path >= 0;

        VirtualInternalRecord node = cache.lookupInternalByPath(path, false);
        if (node == null) {
            try {
                node = dataSource.loadInternalRecord(path);
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read an internal node record from the data source", e);
            }
        }

        return node == VirtualNodeCache.DELETED_INTERNAL_RECORD ? null : node;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void warmInternalRecord(long path) {
        assert path >= 0;

        VirtualInternalRecord node = cache.lookupInternalByPath(path, false);
        if (node == null) {
            try {
                dataSource.warmInternalRecord(path);
            } catch (final IOException e) {
                throw new UncheckedIOException("Failed to read an internal node record from the data source", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualLeafRecord<K, V> findLeafRecord(final K key, final boolean copy) {
        VirtualLeafRecord<K, V> rec = cache.lookupLeafByKey(key, copy);
        if (rec == null) {
            try {
                rec = dataSource.loadLeafRecord(key);
                if (rec != null && copy) {
                    assert rec.getKey().equals(key)
                            : "The key we found from the DB does not match the one we were looking for! key=" + key;
                    rec = cache.putLeaf(rec);
                }
            } catch (final IOException ex) {
                throw new UncheckedIOException("Failed to read a leaf record from the data source by key", ex);
            }
        }

        return rec == VirtualNodeCache.DELETED_LEAF_RECORD ? null : rec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualLeafRecord<K, V> findLeafRecord(final long path, final boolean copy) {
        assert path != INVALID_PATH;
        assert path != ROOT_PATH;

        if (path < state.getFirstLeafPath() || path > state.getLastLeafPath()) {
            return null;
        }

        VirtualLeafRecord<K, V> rec = cache.lookupLeafByPath(path, copy);
        if (rec == null) {
            try {
                rec = dataSource.loadLeafRecord(path);
                if (rec != null && copy) {
                    rec = cache.putLeaf(rec);
                }
            } catch (final IOException ex) {
                throw new UncheckedIOException("Failed to read a leaf record from the data source by path", ex);
            }
        }

        return rec == VirtualNodeCache.DELETED_LEAF_RECORD ? null : rec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long findKey(final K key) {
        final VirtualLeafRecord<K, V> rec = cache.lookupLeafByKey(key, false);
        if (rec != null) {
            return rec.getPath();
        }
        try {
            return dataSource.findKey(key);
        } catch (final IOException ex) {
            throw new UncheckedIOException("Failed to find key in the data source", ex);
        }
    }
}
