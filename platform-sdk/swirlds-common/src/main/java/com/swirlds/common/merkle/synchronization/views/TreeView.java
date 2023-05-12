/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;

/**
 * Describes methods used by the reconnect algorithm to interact with various types of merkle trees.
 *
 * @param <T>
 * 		an object that represents a node in the tree
 */
public interface TreeView<T> extends AutoCloseable {

    /**
     * Check if a node is an internal node.
     *
     * @param node
     * 		the node in question
     * @param isOriginal
     * 		true if the node is from the original tree. This will always be {@code true}
     * 		when called for the teacher.
     * @return true if the node is internal
     */
    boolean isInternal(T node, boolean isOriginal);

    /**
     * Get the child count of an internal node.
     *
     * @param node
     * 		the node in question
     * @return the child count of the node
     * @throws MerkleSynchronizationException
     * 		if the node in question is a leaf node
     */
    int getNumberOfChildren(T node);

    /**
     * Get the class ID of a node.
     *
     * @param node
     * 		the node in question
     * @return the class ID of the node
     */
    long getClassId(T node);

    /**
     * Convert a root of a custom tree from abstract T form into a merkle node object.
     *
     * @param node
     * 		the node in question, guaranteed to only be called on the roots of trees that define custom reconnect views
     * @return the merkle node object
     */
    MerkleNode getMerkleRoot(T node);

    /**
     * Called when reconnect has been completed and this view is no longer required to exist.
     */
    @Override
    default void close() {
        // override this method to perform required cleanup after a reconnect
    }
}
