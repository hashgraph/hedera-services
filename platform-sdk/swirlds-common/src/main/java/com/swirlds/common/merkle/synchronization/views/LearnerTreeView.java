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

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.LearningSynchronizer;
import com.swirlds.common.merkle.synchronization.stats.ReconnectMapStats;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A "view" into a merkle tree (or subtree) used to perform a reconnect operation. This view is used to access
 * the tree by the learner.
 *
 * @param <T>
 * 		the type of an object which signifies a merkle node (T may or may not actually be a MerkleNode type)
 */
public interface LearnerTreeView<T> extends LearnerExpectedLessonQueue<T>, LearnerInitializer<T>, TreeView<T> {

    /**
     * For this tree view, start all required reconnect tasks in the given work group. Learning synchronizer
     * will then wait for all tasks in the work group to complete before proceeding to the next tree view. If
     * new custom tree views are encountered, they must be added to {@code rootsToReceive}, although it isn't
     * currently supported by virtual tree views, as nested virtual maps are not supported.
     *
     * @param learningSynchronizer the learning synchronizer
     * @param workGroup the work group to run teaching task(s) in
     * @param in the input stream to read data from teacher
     * @param out the output stream to write data to teacher
     * @param subtreeListener to notify if custom tree views are encountered
     * @param reconstructedRoot the root node of the reconnected tree must be set here
     */
    void startLearnerTasks(
            final LearningSynchronizer learningSynchronizer,
            final StandardWorkGroup workGroup,
            final AsyncInputStream in,
            final AsyncOutputStream out,
            final Map<Integer, LearnerTreeView<?>> views,
            final Consumer<CustomReconnectRoot<?, ?>> subtreeListener,
            final AtomicReference<MerkleNode> reconstructedRoot,
            final Consumer<Integer> completeListener);

    /**
     * Check if this view represents the root of the state.
     *
     * @return true if this view represents the root of the state
     */
    boolean isRootOfState();

    /**
     * Get the root of the tree (or subtree).
     *
     * @return the root
     */
    T getOriginalRoot();

    /**
     * Set the child of an internal node.
     *
     * @param parent
     * 		the parent that will hold the child, may be null if the view allows null to represent
     * 		internal nodes in the subtree (although it seems unlikely that such a representation would
     * 		be very useful for views to use)
     * @param childIndex
     * 		the position of the child
     * @param child
     * 		the child, may be null if the view allows null to represent merkle leaf nodes in the subtree
     * @throws MerkleSynchronizationException
     * 		if the parent is not an internal node
     */
    void setChild(T parent, int childIndex, T child);

    /**
     * Get the child of a node.
     *
     * @param parent
     * 		the parent in question
     * @param childIndex
     * 		the index of the child
     * @return the child at the index
     * @throws MerkleSynchronizationException
     * 		if the parent is a leaf or if the child index is invalid
     */
    T getChild(T parent, int childIndex);

    /**
     * Get the hash of a node. If this view represents a tree that has null nodes within it, those nodes should cause
     * this method to return a {@link Cryptography#getNullHash() null hash}.
     *
     * @param node
     * 		the node
     * @return the hash of the node
     */
    Hash getNodeHash(T node);

    /**
     * Convert a merkle node that is the root of a subtree with a custom merkle view
     * to the type used by this view.
     *
     * @param node
     * 		the root of the tree or the root of a custom view subtree
     * @return the same nude but with the type used by this view
     */
    T convertMerkleRootToViewType(MerkleNode node);

    /**
     * Read a merkle leaf from the stream (as written by
     * {@link TeacherTreeView#serializeLeaf(SerializableDataOutputStream, Object)}).
     *
     * @param in
     * 		the input stream
     * @return the leaf
     * @throws IOException
     * 		if a problem is encountered with the stream
     */
    T deserializeLeaf(SerializableDataInputStream in) throws IOException;

    /**
     * Read a merkle internal from the stream (as written by
     * {@link TeacherTreeView#serializeInternal(SerializableDataOutputStream, Object)}).
     *
     * @param in
     * 		the input stream
     * @return the internal node
     * @throws IOException
     * 		if a problem is encountered with the stream
     */
    T deserializeInternal(SerializableDataInputStream in) throws IOException;

    /**
     * Release a leaf node.
     *
     * @param node
     * 		the node to release
     */
    void releaseNode(T node);

    /**
     * Indicates whether this learner view uses a shared or a dedicated message queue when
     * reading messages from an async input stream.
     *
     * @return true if this view uses a dedicated async input stream queue; false otherwise
     */
    default boolean usesSharedInputQueue() {
        return false;
    }

    /**
     * Record metrics related to queries about children of a given parent during reconnect.
     * <p>
     * By the definition of this method, it is obvious that the parent is an internal node in the tree.
     * However, the children may be the next level internal nodes or leaf nodes.
     * The metrics differentiate between internal and leaf children. The method of determining
     * whether a given child is an internal node or a leaf may depend on the LearnerTreeView implementation,
     * and this method allows the implementation to define this logic as appropriate.
     *
     * @param mapStats a metrics recorder
     * @param parent a parent
     * @param childIndex a child index, same as a lesson query index
     * @param nodeAlreadyPresent true if the learner tree has the query node already
     */
    default void recordHashStats(
            @NonNull final ReconnectMapStats mapStats,
            @NonNull final T parent,
            final int childIndex,
            final boolean nodeAlreadyPresent) {
        // no-op
    }
}
