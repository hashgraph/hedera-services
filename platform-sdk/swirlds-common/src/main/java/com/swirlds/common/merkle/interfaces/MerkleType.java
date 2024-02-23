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

package com.swirlds.common.merkle.interfaces;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;

/**
 * Describes the type of a merkle node.
 */
public interface MerkleType {

    /**
     * Check if this node is a leaf node. As fast or faster than using instanceof.
     *
     * @return true if this is a leaf node in a merkle tree.
     */
    boolean isLeaf();

    /**
     * Check if this node is a {@link MerkleInternal} node.
     *
     * @return true if this is an internal node
     */
    default boolean isInternal() {
        return !isLeaf();
    }

    /**
     * Blindly cast this merkle node into a leaf node, will fail if node is not actually a leaf node.
     */
    default MerkleLeaf asLeaf() {
        return cast();
    }

    /**
     * Blindly cast this merkle node into an internal node, will fail if node is not actually an internal node.
     */
    default MerkleInternal asInternal() {
        return cast();
    }

    /**
     * Blindly cast this merkle node into the given type, will fail if node is not actually that type.
     *
     * @param <T>
     * 		this node will be cast into this type
     */
    @SuppressWarnings("unchecked")
    default <T extends MerkleNode> T cast() {
        return (T) this;
    }
}
