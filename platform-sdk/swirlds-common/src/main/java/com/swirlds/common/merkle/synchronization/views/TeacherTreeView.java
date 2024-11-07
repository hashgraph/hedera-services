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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.base.time.Time;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.TeachingSynchronizer;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A "view" into a merkle tree (or subtree) used to perform a reconnect operation. This view is used to access
 * the tree by the teacher.
 *
 * @param <T>
 * 		the type of an object which signifies a merkle node (T may or may not actually be a MerkleNode type)
 */
public interface TeacherTreeView<T>
        extends TeacherHandleQueue<T>, TeacherResponseQueue<T>, TeacherResponseTracker<T>, TreeView<T> {

    /**
     * For this tree view, start all required reconnect tasks in the given work group. Teaching synchronizer
     * will then wait for all tasks in the work group to complete before proceeding to the next tree view. If
     * new custom tree views are encountered, they must be added to {@code subtrees}, although it isn't
     * currently supported by virtual tree views, as nested virtual maps are not supported.
     *
     * @param teachingSynchronizer the teacher synchronizer
     * @param workGroup the work group to run teaching task(s) in
     * @param in the input stream to read data from learner
     * @param out the output stream to write data to learner
     * @param subtreeListener a listener to notify when custom tree views are encountered
     */
    void startTeacherTasks(
            final TeachingSynchronizer teachingSynchronizer,
            final int viewId,
            final Time time,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Consumer<CustomReconnectRoot<?, ?>> subtreeListener,
            final Map<Integer, TeacherTreeView<?>> views,
            final Consumer<Integer> completeListener);

    /**
     * Aborts the reconnect process on the teacher side. It may be used to release resources, when
     * reconnect failed with an exception.
     */
    default void abort() {}

    /**
     * Get the root of the tree.
     *
     * @return the root
     */
    T getRoot();

    /**
     * Write data for a merkle leaf to the stream.
     *
     * @param out
     * 		the output stream
     * @param leaf
     * 		the merkle leaf
     * @throws IOException
     * 		if an IO problem occurs
     * @throws MerkleSynchronizationException
     * 		if the node is not a leaf
     */
    void serializeLeaf(SerializableDataOutputStream out, T leaf) throws IOException;

    /**
     * Serialize data required to reconstruct an internal node. Should not contain any
     * data about children, number of children, or any metadata (i.e. data that is not hashed).
     *
     * @param out
     * 		the output stream
     * @param internal
     * 		the internal node to serialize
     * @throws IOException
     * 		if a problem is encountered with the stream
     */
    void serializeInternal(SerializableDataOutputStream out, T internal) throws IOException;

    /**
     * Serialize all child hashes for a given node into a stream. Serialized bytes must be
     * identical to what out.writeSerializableList(hashesList, false, true) method writes.
     *
     * @param parent Merkle node
     * @param out The output stream
     * @throws IOException If an I/O error occurred
     */
    void writeChildHashes(T parent, SerializableDataOutputStream out) throws IOException;

    /**
     * Check if a node is the root of the tree with a custom view.
     *
     * @param node
     * 		the node in question
     * @return if the node is the root of a tree with a custom view
     */
    boolean isCustomReconnectRoot(T node);

    /**
     * It is possible to create a teacher view that is not immediately ready for use, and later becomes ready for use
     * after miscellaneous background operations complete. This method blocks until that background work is completed,
     * after which the view is ready to be used during a reconnect.
     *
     * @throws InterruptedException
     * 		if the thread is interrupted
     */
    default void waitUntilReady() throws InterruptedException {
        // By default, a view is considered "ready" after constructed.
        // If that is not the case for a view implementation, override this method.
    }

    /**
     * Indicates whether this teacher view uses a shared or a dedicated message queue when
     * reading messages from an async input stream.
     *
     * @return true if this view uses a dedicated async input stream queue; false otherwise
     */
    default boolean usesSharedInputQueue() {
        return false;
    }
}
