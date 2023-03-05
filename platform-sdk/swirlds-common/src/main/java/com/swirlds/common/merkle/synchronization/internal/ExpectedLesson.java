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

package com.swirlds.common.merkle.synchronization.internal;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This object is used to track a node for which the learner is expecting data.
 */
public class ExpectedLesson<T> {

    /**
     * Do we already have the node sent by the teacher?
     */
    private final boolean nodeAlreadyPresent;

    /**
     * The node that will be the parent of this node.
     */
    private final T parent;

    /**
     * This node's eventual position within its parent.
     */
    private final int positionInParent;

    /**
     * The node that was originally in this position within the tree.
     */
    private final T originalNode;

    /**
     * Create a record for a node for which we are expecting data.
     *
     * @param parent
     * 		the eventual parent of the node
     * @param positionInParent
     * 		the eventual position of the node within its parent
     * @param originalNode
     * 		the node that was originally in this location in the tree
     * @param nodeAlreadyPresent
     * 		does the learner already have the node being sent by the teacher?
     */
    public ExpectedLesson(
            final T parent, final int positionInParent, final T originalNode, final boolean nodeAlreadyPresent) {
        this.parent = parent;
        this.positionInParent = positionInParent;
        this.originalNode = originalNode;
        this.nodeAlreadyPresent = nodeAlreadyPresent;
    }

    /**
     * Does the learner already have the node from the teacher?
     */
    public boolean isNodeAlreadyPresent() {
        return nodeAlreadyPresent;
    }

    /**
     * Get the eventual parent of the node.
     */
    public T getParent() {
        return parent;
    }

    /**
     * Get the eventual position of the node within its parent.
     */
    public int getPositionInParent() {
        return positionInParent;
    }

    /**
     * Get the original node in this position.
     */
    public T getOriginalNode() {
        return originalNode;
    }

    /**
     * For debugging purposes
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("already present", nodeAlreadyPresent)
                .append("parent", parent)
                .append("position", positionInParent)
                .append("original", originalNode)
                .build();
    }
}
