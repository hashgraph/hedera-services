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

package com.swirlds.virtualmap.datasource;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.imageio.IIOException;

/**
 * In memory implementation of VirtualDataSource for use in testing.
 *
 * @param <K>
 * 		the type for keys
 * @param <V>
 * 		the type for values
 */
public class InMemoryDataSource<K extends VirtualKey<? super K>, V extends VirtualValue>
        implements VirtualDataSource<K, V> {

    private static final String NEGATIVE_PATH_MESSAGE = "path is less than 0";

    private final String name;
    private final ConcurrentHashMap<Long, Hash> hashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, VirtualLeafRecord<K, V>> leafRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Long> keyToPathMap = new ConcurrentHashMap<>();
    private volatile long firstLeafPath = -1;
    private volatile long lastLeafPath = -1;
    private volatile boolean closed = false;

    /**
     * Create a new InMemoryDataSource
     *
     * @param name
     * 		data source name
     * @param keySizeBytes
     * 		the number of bytes a key takes when serialized
     * @param keyConstructor
     * 		constructor to create new keys for deserialization
     * @param valueSizeBytes
     * 		the number of bytes a value takes when serialized
     * @param valueConstructor
     * 		constructor to create new values for deserialization
     */
    public InMemoryDataSource(
            final String name,
            int keySizeBytes,
            Supplier<K> keyConstructor,
            int valueSizeBytes,
            Supplier<V> valueConstructor) {
        this.name = name;
    }

    public InMemoryDataSource(InMemoryDataSource<K, V> copy) {
        this.name = copy.name;
        this.firstLeafPath = copy.firstLeafPath;
        this.lastLeafPath = copy.lastLeafPath;
        this.hashes.putAll(copy.hashes);
        this.leafRecords.putAll(copy.leafRecords);
        this.keyToPathMap.putAll(copy.keyToPathMap);
    }

    public String getName() {
        return name;
    }

    /**
     * Close the data source
     */
    @Override
    public void close() {
        hashes.clear();
        leafRecords.clear();
        keyToPathMap.clear();
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * Save a bulk set of changes to internal nodes and leaves.
     *
     * @param firstLeafPath
     * 		the new path of first leaf node
     * @param lastLeafPath
     * 		the new path of last leaf node
     * @param internalRecords
     * 		stream of new internal nodes and updated internal nodes
     * @param leafRecordsToAddOrUpdate
     * 		stream of new leaf nodes and updated leaf nodes
     * @param leafRecordsToDelete
     * 		stream of new leaf nodes to delete, The leaf record's key and path have to be populated, all other data can
     * 		be null.
     */
    @Override
    public void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualInternalRecord> internalRecords,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException {

        if (closed) {
            throw new IOException("Data Source is closed");
        }

        if (firstLeafPath < 1 && firstLeafPath != -1) {
            throw new IllegalArgumentException("An illegal first leaf path was provided: " + firstLeafPath);
        }

        final var validLastLeafPath = (lastLeafPath == firstLeafPath && (lastLeafPath == -1 || lastLeafPath == 1))
                || lastLeafPath > firstLeafPath;
        if (!validLastLeafPath) {
            throw new IllegalArgumentException("An illegal last leaf path was provided. lastLeafPath=" + lastLeafPath
                    + ", firstLeafPath=" + firstLeafPath);
        }

        deleteInternalRecords(firstLeafPath);
        deleteLeafRecords(leafRecordsToDelete);
        saveInternalRecords(lastLeafPath, internalRecords);
        saveLeafRecords(firstLeafPath, lastLeafPath, leafRecordsToAddOrUpdate);
        // Save the leaf paths for later validation checks and to let us know when to delete internals
        this.firstLeafPath = firstLeafPath;
        this.lastLeafPath = lastLeafPath;
    }

    /**
     * Load the record for a leaf node by key
     *
     * @param key
     * 		the key for a leaf
     * @return the leaf's record if one was stored for the given key or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(K key) throws IOException {
        Objects.requireNonNull(key, "Key cannot be null");
        final Long path = keyToPathMap.get(key);
        if (path == null) {
            return null;
        }
        assert path >= firstLeafPath && path <= lastLeafPath : "Found an illegal path in keyToPathMap!";
        final VirtualLeafRecord<K, V> leafRecord = loadLeafRecord(path);

        if (!leafRecord.getKey().equals(key)) {
            throw new RuntimeException("record has invalid key");
        }

        return leafRecord;
    }

    /**
     * Load the record for a leaf node by path
     *
     * @param path
     * 		the path for a leaf
     * @return the leaf's record if one was stored for the given path or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(long path) throws IOException {
        if (path < 0) {
            throw new IllegalArgumentException(NEGATIVE_PATH_MESSAGE);
        }

        if (path < firstLeafPath) {
            throw new IllegalArgumentException(
                    "path[" + path + "] is less than the firstLeafPath[" + firstLeafPath + "]");
        }

        if (path > lastLeafPath) {
            throw new IllegalArgumentException(
                    "path[" + path + "] is larger than the lastLeafPath[" + lastLeafPath + "]");
        }

        final var rec = leafRecords.get(path);
        assert rec != null
                : "When looking up leaves, we should never be asked to look up a leaf that doesn't exist. path=" + path;
        return new VirtualLeafRecord<>(rec.getPath(), rec.getKey(), rec.getValue());
    }

    /**
     * Find the path of the given key
     * @param key
     * 		the key for a path
     * @return the path or INVALID_PATH if not stored
     * @throws IOException
     * 		If there was a problem locating the key
     */
    @Override
    public long findKey(final K key) throws IOException {
        final Long path = keyToPathMap.get(key);
        return (path == null) ? INVALID_PATH : path;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash loadHash(long path) {
        if (path < 0) {
            throw new IllegalArgumentException(NEGATIVE_PATH_MESSAGE);
        }

        // It may be that some code is trying to iterate over an internal node that has never
        // been created or saved. We have to be prepared for that case.
        if (path != 0 && path > lastLeafPath) {
            return null;
        }

        return hashes.get(path);
    }

    /**
     * This is a no-op implementation.
     *
     * {@inheritDoc}
     */
    @Override
    public void snapshot(final Path snapshotDirectory) {
        // nop
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void copyStatisticsFrom(final VirtualDataSource<K, V> that) {
        // this database has no statistics
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerMetrics(final Metrics metrics) {
        // this database has no statistics
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VirtualKeySet<K> buildKeySet() {
        return new InMemoryKeySet<>();
    }

    // =================================================================================================================
    // private methods

    private void saveInternalRecords(final long maxValidPath, final Stream<VirtualInternalRecord> internalRecords)
            throws IOException {
        final var itr = internalRecords.iterator();
        while (itr.hasNext()) {
            final var rec = itr.next();
            final var path = rec.getPath();
            final var hash =
                    Objects.requireNonNull(rec.getHash(), "The hash of a saved internal record cannot be null");

            if (path < 0) {
                throw new IIOException("Internal record for " + path + " is bogus. It cannot be < 0");
            }

            if (path > maxValidPath) {
                throw new IOException(
                        "Internal record for " + path + " is bogus. It cannot be > last leaf path " + maxValidPath);
            }

            this.hashes.put(path, hash);
        }
    }

    private void saveLeafRecords(
            final long firstLeafPath, final long lastLeafPath, final Stream<VirtualLeafRecord<K, V>> leafRecords)
            throws IOException {
        final var itr = leafRecords.iterator();
        while (itr.hasNext()) {
            final var rec = itr.next();
            final var path = rec.getPath();
            final var key = Objects.requireNonNull(rec.getKey(), "Key cannot be null");
            final var value = rec.getValue(); // Not sure if this can be null or not.

            if (path < firstLeafPath) {
                throw new IIOException(
                        "Leaf record for " + path + " is bogus. It cannot be < first leaf path " + firstLeafPath);
            }

            if (path > lastLeafPath) {
                throw new IOException(
                        "Leaf record for " + path + " is bogus. It cannot be > last leaf path " + lastLeafPath);
            }

            this.leafRecords.put(path, new VirtualLeafRecord<>(path, key, value));
            this.keyToPathMap.put(key, path);
        }
    }

    private void deleteInternalRecords(final long firstLeafPath) {
        for (long i = firstLeafPath; i < this.firstLeafPath; i++) {
            this.hashes.remove(i);
        }
    }

    private void deleteLeafRecords(final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete) {
        final var itr = leafRecordsToDelete.iterator();
        while (itr.hasNext()) {
            final var rec = itr.next();
            this.keyToPathMap.remove(rec.getKey());
            this.leafRecords.remove(rec.getPath());
        }
    }
}
