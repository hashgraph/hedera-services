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

package com.swirlds.common.merkle.iterators.internal;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import java.util.function.ObjIntConsumer;

/**
 * Defines algorithm specific behavior for a merkle iterator.
 */
public interface MerkleIterationAlgorithm {

    /**
     * Push a node into the stack/queue.
     */
    void push(MerkleNode node);

    /**
     * Remove and return the next item in the stack/queue.
     */
    MerkleNode pop();

    /**
     * Return the next item in the stack/queue but do not remove it.
     */
    MerkleNode peek();

    /**
     * Get the number of elements in the stack/queue.
     */
    int size();

    /**
     * Call pushNode on all of a merkle node's children.
     *
     * @param parent
     * 		the parent with children to push
     * @param pushNode
     * 		a method to be used to push children. First argument is the parent, second argument is the child index.
     */
    void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode);
}
