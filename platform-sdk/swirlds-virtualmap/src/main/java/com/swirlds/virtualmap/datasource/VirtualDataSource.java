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
import java.util.stream.Stream;

/**
 * Defines a data source, used with {@code VirtualMap}, to implement a virtual tree. Both in-memory and
 * on-disk data sources may be written. When constructing a {@code VirtualMap}, create a concrete data source
 * implementation.
 * <p>
 * The {@link VirtualDataSource} defines methods for getting the root node, and for looking up a leaf node
 * by key.
 * <p>
 * The nodes returned by the methods on this interface represent the *LATEST* state on disk. Once retrieved,
 * the nodes can be fast-copied for later versions, and persisted to disk via *archive*.
 * <p>
 * Each datasource instance works for a single type of K and V
 * <p>
 * <strong>YOU MUST NEVER ASK FOR SOMETHING YOU HAVE NOT PREVIOUSLY WRITTEN.</strong> If you do, you will get
 * very strange exceptions. This is deemed acceptable because guarding against it would require obnoxious
 * performance degradation.
 *
 * @param <K>
 * 		The key for a leaf node.
 * @param <V>
 * 		The type of leaf node.
 */
@SuppressWarnings("unused")
public interface VirtualDataSource<K extends VirtualKey<? super K>, V extends VirtualValue> {

    /** nominal value for a invalid path */
    int INVALID_PATH = -1;

    /**
     * Close the data source
     *
     * @throws IOException
     * 		If there was a problem closing the data source
     */
    void close() throws IOException;

    /**
     * Save a bulk set of changes to internal nodes and leaves.
     * <p><strong>YOU MUST NEVER ASK FOR SOMETHING YOU HAVE NOT PREVIOUSLY WRITTEN.</strong></p>
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
     * @throws IOException
     * 		If there was a problem saving changes to data source
     */
    void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualInternalRecord> internalRecords,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException;

    /**
     * Load the record for a leaf node by key
     *
     * @param key
     * 		the key for a leaf
     * @return the leaf's record if one was stored for the given key or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException;

    /**
     * Load the record for a leaf node by path
     *
     * @param path
     * 		the path for a leaf
     * @return the leaf's record if one was stored for the given path or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException;

    /**
     * Find the path of the given key
     * @param key
     * 		the key for a path
     * @return the path or INVALID_PATH if not stored
     * @throws IOException
     * 		If there was a problem locating the key
     */
    long findKey(final K key) throws IOException;

    /**
     * Load the record for an internal node by path.
     *
     * @param path
     * 		The path for an internal node
     * @return
     * 		The internal node's record if one was stored for the given path or null if not stored
     * @throws IOException
     * 		If there was a problem reading the internal record
     */
    default VirtualInternalRecord loadInternalRecord(final long path) throws IOException {
        return loadInternalRecord(path, true);
    }

    /**
     * Load the record for an internal node by path and deserialize it from bytes, if requested.
     *
     * @param path
     * 		The path for an internal node
     * @param deserialize
     * 		When set to false, a Java object is not deserialized but the OS still loads it
     *      and the OS file cache is populated. This may speed up subsequent reads
     * @return
     * 		The internal node's record if one was stored for the given path; {@code null} if not stored or
     * 		deserialization is not requested
     * @throws IOException
     * 		If there was a problem reading the internal record
     */
    VirtualInternalRecord loadInternalRecord(final long path, final boolean deserialize) throws IOException;

    /**
     * Reads the internal record at {@code path} into OS file cache, but don't deserialize it into Java heap.
     * This gives us a free cache at the OS level without storing anything into Java heap.
     *
     * @param path
     * 		The path for an internal node
     * @throws IOException
     * 		If there was a problem reading the internal record
     */
    default void warmInternalRecord(final long path) throws IOException {
        loadInternalRecord(path, false);
    }

    /**
     * Load the hash for a leaf
     *
     * NOTE: Called during the hashing phase ONLY. Never called on non-existent nodes.
     *
     * @param path
     * 		the path to the leaf
     * @return leaf's hash or null if no leaf hash is stored for the given path
     * @throws IOException
     * 		If there was a problem loading the leaf's hash from data source
     */
    Hash loadLeafHash(final long path) throws IOException;

    /**
     * Write a snapshot of the current state of the database at this moment in time. This will need to be called between
     * calls to saveRecords to have a reliable state. This will block till the snapshot is completely created.
     * <p><b>
     * IMPORTANT, after this is completed the caller owns the directory. It is responsible for deleting it when it
     * is no longer needed.
     * </b></p>
     *
     * @param snapshotDirectory
     * 		Directory to put snapshot into, it will be created if it doesn't exist.
     * @throws IOException
     * 		If there was a problem writing the current database out to the given directory
     */
    void snapshot(final Path snapshotDirectory) throws IOException;

    /**
     * Switch this database instance to use the statistics registered in another database. Required due
     * to statistics restriction that prevents stats from being registered after initial boot up process.
     *
     * @param that
     * 		the database with statistics to copy
     */
    void copyStatisticsFrom(VirtualDataSource<K, V> that);

    /**
     * Register all statistics with an object that manages statistics.
     *
     * @param metrics
     * 		reference to the metrics system
     */
    void registerMetrics(final Metrics metrics);

    /**
     * Start background compaction process, if it is not already running.
     */
    default void startBackgroundCompaction() {}

    /**
     * Stop background compaction process, if it is running.
     */
    default void stopBackgroundCompaction() {}

    /**
     * Build an empty {@link VirtualKeySet}. This key set should be compatible with data in this data source,
     * but should otherwise have no direct connection to the data in this data source.
     *
     * @return a new key set
     */
    VirtualKeySet<K> buildKeySet();
}
