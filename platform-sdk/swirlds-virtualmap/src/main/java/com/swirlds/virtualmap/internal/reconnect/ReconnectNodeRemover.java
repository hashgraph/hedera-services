/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;
import static com.swirlds.common.threading.interrupt.Uninterruptable.tryToSleep;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import com.swirlds.virtualmap.internal.RecordAccessor;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Unlike some data structures like MerkleMap that rebuild all metadata after a reconnect, a virtual map's metadata
 * is very large and is prohibitive to rebuild in its entirety. This means that the virtual map's metadata must
 * be modified as new data is streamed from the teacher. This class is responsible for removing metadata entries
 * for data that his no longer present in the tree after a reconnect.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class ReconnectNodeRemover<K extends VirtualKey<? super K>, V extends VirtualValue> {

    private static final Logger logger = LogManager.getLogger(ReconnectNodeRemover.class);

    private static final int QUEUE_CAPACITY = 100_000;

    /**
     * The first leaf path of the new tree being received.
     */
    private long newFirstLeafPath;

    /**
     * The last leaf path of the new tree being received.
     */
    private long newLastLeafPath;

    /**
     * Can be used to access the tree being constructed.
     */
    private final RecordAccessor<K, ?> oldRecords;

    /**
     * The first leaf path of the original merkle tree.
     */
    private final long oldFirstLeafPath;

    /**
     * The last leaf path of the original merkle tree.
     */
    private final long oldLastLeafPath;

    /**
     * Contains keys that have been encountered so far during the reconnect.
     */
    private final VirtualKeySet<K> encounteredKeys;

    /**
     * Keys that need to be removed from the data store.
     */
    private Map<K, Long /* path */> keysToBeRemoved = new HashMap<>();

    /**
     * The path of most recent node handled. Since nodes are handled in path order, this value
     * will only increase over time.
     */
    private final AtomicLong handledPath = new AtomicLong(-1);

    /**
     * Describes a new node that was received during a reconnect. Key will be null if the node is an internal.
     *
     * @param path
     * 		the path of the node
     * @param isLeaf
     * 		true if the node is a leaf node
     * @param key
     * 		if the node is a leaf node then this is the node's key, otherwise null
     * @param <K>
     * 		the type of the key
     */
    private record ReceivedNode<K>(long path, boolean isLeaf, K key) {}

    private final StandardWorkGroup workGroup;

    private final QueueThread<ReceivedNode<K>> workQueue;

    private final AtomicBoolean exceptionEncountered = new AtomicBoolean(false);

    /**
     * Create an object responsible for removing virtual map nodes during a reconnect.
     *
     * @param threadManager
     * 		responsible for creating new threads
     * @param oldRecords
     * 		a record accessor for the original map from before the reconnect
     * @param encounteredKeys
     * 		records the keys that have been encountered so far during the reconnect
     * @param oldFirstLeafPath
     * 		the original first leaf path from before the reconnect
     * @param oldLastLeafPath
     * 		the original last leaf path from before the reconnect
     */
    public ReconnectNodeRemover(
            final ThreadManager threadManager,
            final StandardWorkGroup workGroup,
            final RecordAccessor<K, ?> oldRecords,
            final VirtualKeySet<K> encounteredKeys,
            final long oldFirstLeafPath,
            final long oldLastLeafPath) {

        this.workGroup = workGroup;

        this.oldRecords = oldRecords;
        this.oldFirstLeafPath = oldFirstLeafPath;
        this.oldLastLeafPath = oldLastLeafPath;

        this.encounteredKeys = Objects.requireNonNull(encounteredKeys);

        final QueueThreadConfiguration<ReceivedNode<K>> config = threadManager.newQueueThreadConfiguration();
        this.workQueue = config.setCapacity(QUEUE_CAPACITY)
                .setComponent("reconnect")
                .setThreadName("vm-node-remover")
                .setHandler(this::handler)
                .setExceptionHandler(this::exceptionHandler)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();

        workGroup.execute("node-removal", () -> workQueue.buildSeed().inject());
    }

    /**
     * This method is called if there is an exception on the background thread.
     */
    private void exceptionHandler(final Thread t, final Throwable cause) {
        logger.error(EXCEPTION.getMarker(), "exception on VM reconnect node removal thread");
        workGroup.handleError(cause);
        exceptionEncountered.set(true);
    }

    /**
     * Set the first/last leaf path for the tree as it will be after reconnect is completed. Expected
     * to be called before the first node is passed to this object.
     *
     * @param newFirstLeafPath
     * 		the first leaf path after reconnect completes
     * @param newLastLeafPath
     * 		the last leaf path after reconnect completes
     */
    public void setPathInformation(final long newFirstLeafPath, final long newLastLeafPath) {
        this.newFirstLeafPath = newFirstLeafPath;
        this.newLastLeafPath = newLastLeafPath;
    }

    /**
     * Register the receipt of a new internal node. If the internal node is in the position that was
     * formally occupied by a leaf node then this method will ensure that the old leaf node is properly
     * deleted.
     *
     * @param path
     * 		the path of the node
     */
    public void newInternalNode(final long path) {
        abortAndLogIfInterrupted(
                () -> workQueue.put(new ReceivedNode<>(path, false, null)),
                "reconnect virtual map node removal thread interrupted");
    }

    /**
     * Register the receipt of a new leaf node. If the leaf node is in the position that was formally occupied by
     * a leaf node then this method will ensure that the old leaf node is properly deleted. If the leaf node is
     * in the position that was formally occupied by an internal node then this method will ensure all leaves
     * in the subtree are properly deleted.
     *
     * @param path
     * 		the path to the node
     * @param newKey
     * 		the key of the new leaf node
     */
    public void newLeafNode(final long path, final K newKey) {
        abortAndLogIfInterrupted(
                () -> workQueue.put(new ReceivedNode<>(path, true, newKey)),
                "reconnect virtual map node removal thread interrupted");
    }

    /**
     * Return a stream of keys that require deletion.
     *
     * @param requiredPath
     * 		the minimum path that must be processed before the stream is returned
     * @return a stream of keys to be deleted. Only the key and path in these records
     * 		are populated, all other data is uninitialized.
     */
    public Stream<VirtualLeafRecord<K, V>> getRecordsToDelete(final long requiredPath) {
        while (handledPath.get() < requiredPath && !exceptionEncountered.get()) {
            tryToSleep(Duration.ofMillis(1));
        }

        if (exceptionEncountered.get()) {
            throw new RuntimeException("VirtualMap reconnect node removal thread has crashed");
        }

        workQueue.pause();
        final Stream<VirtualLeafRecord<K, V>> stream = keysToBeRemoved.entrySet().stream()
                .map((final Map.Entry<K, Long> entry) ->
                        new VirtualLeafRecord<>(entry.getValue(), null, entry.getKey(), null));

        // We can't just clear the map, as doing so will disrupt the stream constructed above.
        keysToBeRemoved = new HashMap<>();

        workQueue.resume();

        return stream;
    }

    /**
     * Handles elements from the work queue.
     *
     * @param receivedNode
     * 		a node that was received during a reconnect
     */
    private void handler(final ReceivedNode<K> receivedNode) {
        final ReplacedNodeType replacedType = getReplacedNodeType(receivedNode.path());
        if (receivedNode.isLeaf()) {
            handleLeaf(receivedNode.path, replacedType, receivedNode.key);
        } else {
            handleInternal(receivedNode.path, replacedType);
        }

        handledPath.set(receivedNode.path);
    }

    /**
     * Describes the type of node that is being replaced.
     */
    private enum ReplacedNodeType {
        LEAF,
        INTERNAL,
        NO_NODE
    }

    /**
     * Get the type of the node that is being replaced
     *
     * @param path
     * 		the path to the node
     * @return the type of node being replaced
     */
    private ReplacedNodeType getReplacedNodeType(final long path) {
        if (path < oldFirstLeafPath) {
            return ReplacedNodeType.INTERNAL;
        } else if (path <= oldLastLeafPath) {
            return ReplacedNodeType.LEAF;
        } else {
            return ReplacedNodeType.NO_NODE;
        }
    }

    /**
     * Handles the receipt of a leaf node from the teacher. Executed on the queue thread.
     *
     * @param path
     * 		the path to the node
     * @param key
     * 		the leaf node's key
     */
    private void handleLeaf(final long path, final ReplacedNodeType replacedType, final K key) {
        if (path < newFirstLeafPath || path > newLastLeafPath) {
            throw new IllegalStateException(
                    "Expected leaf path between " + newFirstLeafPath + " and " + newLastLeafPath + ", got " + path);
        }

        switch (replacedType) {
            case LEAF -> removeLeaf(path);
            case INTERNAL -> removeInternal(path);
        }

        // If we think we need to remove a key but find out that the leaf has actually just been moved,
        // then we don't actually need to remove it.
        keysToBeRemoved.remove(key);

        encounteredKeys.add(key);
    }

    /**
     * Handles the receipt of an internal node from the teacher. Executed on the queue thread.
     *
     * @param path
     * 		the path ot the node
     */
    private void handleInternal(final long path, final ReplacedNodeType replacedType) {
        if (path < 0 || (path >= newFirstLeafPath && newFirstLeafPath != -1)) {
            throw new IllegalStateException(
                    "Expected internal node path between 0 and " + newFirstLeafPath + ", got " + path);
        }

        if (replacedType == ReplacedNodeType.LEAF) {
            removeLeaf(path);
        }
    }

    /**
     * Remove a leaf node at a given position.
     *
     * @param path
     * 		the path of the leaf being removed
     */
    private void removeLeaf(final long path) {
        final K originalKey = oldRecords.findLeafRecord(path, false).getKey();

        if (!encounteredKeys.contains(originalKey)) {
            keysToBeRemoved.put(originalKey, path);
        }
    }

    /**
     * Remove an internal node at a given position.
     */
    private void removeInternal(final long path) {
        final long leftChildPath = Path.getLeftChildPath(path);
        final long rightChildPath = Path.getRightChildPath(path);

        if (getReplacedNodeType(leftChildPath) == ReplacedNodeType.LEAF) {
            removeLeaf(leftChildPath);
        } else {
            removeInternal(leftChildPath);
        }

        if (getReplacedNodeType(rightChildPath) == ReplacedNodeType.LEAF) {
            removeLeaf(rightChildPath);
        } else {
            removeInternal(rightChildPath);
        }
    }

    /**
     * Stop the work thread.
     */
    public void close() {
        workQueue.stop();
    }
}
