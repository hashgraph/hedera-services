/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
public interface VirtualDataSource<K extends VirtualKey, V extends VirtualValue> {

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
     * Save a batch of data to data store.
     * <p>
     * If you call this method where not all data is provided to cover the change in
     * firstLeafPath and lastLeafPath, then any reads after this call may return rubbish or throw
     * obscure exceptions for any internals or leaves that have not been written. For example, if
     * you were to grow the tree by more than 2x, and then called this method in batches, be aware
     * that if you were to query for some record between batches that hadn't yet been saved, you
     * will encounter problems.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param pathHashRecordsToUpdate stream of records with hashes to update, it is assumed this is sorted by
     *     path and each path only appears once.
     * @param leafRecordsToAddOrUpdate stream of new leaf nodes and updated leaf nodes
     * @param leafRecordsToDelete stream of new leaf nodes to delete, The leaf record's key and path
     *     have to be populated, all other data can be null.
     * @throws IOException If there was a problem saving changes to data source
     */
    default void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            @NonNull final Stream<VirtualHashRecord> pathHashRecordsToUpdate,
            @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException {
        saveRecords(
                firstLeafPath,
                lastLeafPath,
                pathHashRecordsToUpdate,
                leafRecordsToAddOrUpdate,
                leafRecordsToDelete,
                false);
    }

    /**
     * Save a bulk set of changes to internal nodes and leaves.
     *
     * @param firstLeafPath
     * 		the new path of first leaf node
     * @param lastLeafPath
     * 		the new path of last leaf node
     * @param pathHashRecordsToUpdate
     * 		stream of dirty hash records to update
     * @param leafRecordsToAddOrUpdate
     * 		stream of new leaf nodes and updated leaf nodes
     * @param leafRecordsToDelete
     * 		stream of new leaf nodes to delete, The leaf record's key and path have to be populated, all other data can
     * 		be null.
     * @param isReconnectContext if the save is in the context of a reconnect
     * @throws IOException
     * 		If there was a problem saving changes to data source
     */
    void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            @NonNull final Stream<VirtualHashRecord> pathHashRecordsToUpdate,
            @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            @NonNull final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete,
            final boolean isReconnectContext)
            throws IOException;

    /**
     * Load the record for a leaf node by key.
     *
     * @param key
     * 		the key for a leaf
     * @return the leaf's record if one was stored for the given key or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    @Nullable
    VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException;

    /**
     * Load the record for a leaf node by path. If the path is outside the current data source's
     * leaf path range, this method returns {@code null}.
     *
     * @param path
     * 		the path for a leaf
     * @return the leaf's record if one was stored for the given path or null if not stored
     * @throws IOException
     * 		If there was a problem reading the leaf record
     */
    @Nullable
    VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException;

    /**
     * Find the path of the given key.
     *
     * @param key
     * 		the key for a path
     * @return the path or INVALID_PATH if not stored
     * @throws IOException
     * 		If there was a problem locating the key
     */
    long findKey(final K key) throws IOException;

    /**
     * Load a virtual node hash by path. If the path is outside [0, last leaf path] range, this
     * method returns {@code null}.
     *
     * @param path virtual node path
     * @return The node's record if one was stored for the given path; {@code null} if not stored or
     * 	  		deserialization is not requested
     * @throws IOException
     * 		If there was a problem loading the leaf's hash from data source
     */
    @Nullable
    Hash loadHash(final long path) throws IOException;

    /**
     * Load a virtual node hash by path and, if found, write it to the specified output stream. This
     * method helps avoid (de)serialization overhead during reconnects on the teacher side. Instead of
     * loading bytes from disk, then deserializing them into {@link Hash} objects, and then serializing
     * again to socket output stream, this method can write the bytes directly to the stream.
     *
     * <p>Written bytes must be 100% identical to how hashes are serialized using {@link
     * Hash#serialize(SerializableDataOutputStream)} method.
     *
     * @param path Virtual node path
     * @param out Output stream to write the hash, if found
     * @return If the hash was found and written to the stream
     * @throws IOException If an I/O error occurred
     */
    default boolean loadAndWriteHash(final long path, final SerializableDataOutputStream out) throws IOException {
        final Hash hash = loadHash(path);
        if (hash == null) {
            return false;
        }
        hash.serialize(out);
        return true;
    }

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
     * Enables background compaction process. Compaction starts on the next flush.
     */
    void enableBackgroundCompaction();

    /**
     * Cancels all compactions that are currently running and disables background compaction process.
     */
    void stopAndDisableBackgroundCompaction();

    /**
     * Provides estimation how much space is needed to store the given number of internal / leaf nodes in the data
     * source. This estimation is used to decide when to flush virtual node caches to data sources.
     *
     * @param dirtyInternals Number of dirty internal nodes in the node cache
     * @param dirtyLeaves    Number of dirty leaf nodes in the node cache
     * @return Estimated space needed to store the given number of nodes in the data source, in bytes
     */
    long estimatedSize(long dirtyInternals, long dirtyLeaves);

    long getFirstLeafPath();

    long getLastLeafPath();
}
