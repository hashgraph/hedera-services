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

package com.swirlds.virtualmap.internal;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import java.io.UncheckedIOException;

/**
 * Provides access to all records.
 * @param <K>
 *     The key
 * @param <V>
 *     The value
 */
public interface RecordAccessor<K extends VirtualKey<? super K>, V extends VirtualValue> {

    /**
     * Gets the {@link VirtualRecord} at a given path. If there is no record at tht path, null is returned.
     *
     * @param path
     * 		The path of the node.
     * @return
     * 		Null if the record doesn't exist. Either the path is bad, or the record has been deleted,
     * 		or the record has never been created.
     * @throws UncheckedIOException
     * 		If we fail to access the data store, then a catastrophic error occurred and
     * 		an UncheckedIOException is thrown.
     */
    VirtualRecord findRecord(final long path);

    /**
     * Gets the {@link VirtualInternalRecord} at a given path. If there is no
     * record at tht path, null is returned.
     *
     * @param path
     * 		The path of the internal node.
     * @return
     * 		Null if the internal record doesn't exist. Either the path is bad, or the record has been deleted,
     * 		or the record has never been created.
     * @throws UncheckedIOException
     * 		If we fail to access the data store, then a catastrophic error occurred and
     * 		an UncheckedIOException is thrown.
     */
    VirtualInternalRecord findInternalRecord(final long path);

    /**
     * Reads the internal record at {@code path} into OS file cache (warms it), but don't deserialize it into Java heap.
     * This gives us a free cache at the OS level without storing anything into Java heap.
     * @param path
     */
    void warmInternalRecord(final long path);

    /**
     * Locates and returns a leaf node based on the given key. If the leaf
     * node already exists in memory, then the same instance is returned each time.
     * If the node is not in memory, then a new instance is returned. To save
     * it in memory, set <code>cache</code> to true. If the key cannot be found in
     * the data source, then null is returned.
     *
     * @param key
     * 		The key. Must not be null.
     * @param copy
     * 		Whether to make a fast copy if needed.
     * @return The leaf, or null if there is not one.
     * @throws UncheckedIOException
     * 		If we fail to access the data store, then a catastrophic error occurred and
     * 		an UncheckedIOException is thrown.
     */
    VirtualLeafRecord<K, V> findLeafRecord(final K key, final boolean copy);

    /**
     * Locates and returns a leaf node based on the path. If the leaf
     * node already exists in memory, then the same instance is returned each time.
     * If the node is not in memory, then a new instance is returned. To save
     * it in memory, set <code>cache</code> to true. If the leaf cannot be found in
     * the data source, then null is returned.
     *
     * @param path
     * 		The path
     * @param copy
     * 		Whether to make a fast copy if needed.
     * @return The leaf, or null if there is not one.
     * @throws UncheckedIOException
     * 		If we fail to access the data store, then a catastrophic error occurred and
     * 		an UncheckedIOException is thrown.
     */
    VirtualLeafRecord<K, V> findLeafRecord(final long path, final boolean copy);

    /**
     * Finds the path of the given key.
     * @param key
     * 		The key. Must not be null.
     * @return The path or INVALID_PATH if the key is not found.
     */
    long findKey(final K key);

    /**
     * Gets the data source backed by this {@link RecordAccessor}
     *
     * @return
     * 		The data source. Will not be null.
     */
    VirtualDataSource<K, V> getDataSource(); // I'd actually like to remove this some day...

    /**
     * Gets the cache.
     *
     * @return
     * 		The cache. This will never be null.
     */
    VirtualNodeCache<K, V> getCache();
}
