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

package com.swirlds.common.merkle.synchronization.views;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import java.io.IOException;
import java.util.List;

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
     * Get the hashes of the children. Hashes should be in the same order as the children. Null children should
     * cause the null hash to be in the returned list.
     *
     * @param parent
     * 		the parent in question
     * @return a list of the parent's child hashes
     * @throws MerkleSynchronizationException
     * 		if the parent is actually a leaf node or if any of the children don't have a hash
     */
    List<Hash> getChildHashes(T parent);

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
}
