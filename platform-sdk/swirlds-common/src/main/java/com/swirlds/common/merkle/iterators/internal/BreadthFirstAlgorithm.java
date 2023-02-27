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
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#BREADTH_FIRST BREADTH_FIRST}.
 */
public class BreadthFirstAlgorithm implements MerkleIterationAlgorithm {

    private final Queue<MerkleNode> queue = new LinkedList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void push(final MerkleNode node) {
        queue.add(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode pop() {
        return queue.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode peek() {
        return queue.peek();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return queue.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
        for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
            pushNode.accept(parent, childIndex);
        }
    }
}
