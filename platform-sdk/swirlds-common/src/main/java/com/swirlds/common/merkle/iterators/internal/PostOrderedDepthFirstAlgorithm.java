/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#POST_ORDERED_DEPTH_FIRST POST_ORDER_DEPTH_FIRST}.
 */
public class PostOrderedDepthFirstAlgorithm implements MerkleIterationAlgorithm {

    /**
     * The initial capacity for the array implementing the stack. It's much better to choose something a bit
     * larger than to pay the penalty of resizing the array list.
     */
    private static final int INITIAL_STACK_CAPACITY = 1024;

    /**
     * This list is used to implement a stack.
     */
    private final List<MerkleNode> stack = new ArrayList<>(INITIAL_STACK_CAPACITY);

    /**
     * {@inheritDoc}
     */
    @Override
    public void push(final MerkleNode node) {
        stack.add(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode pop() {
        return stack.remove(stack.size() - 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode peek() {
        return stack.get(stack.size() - 1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return stack.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
        for (int childIndex = parent.getNumberOfChildren() - 1; childIndex >= 0; childIndex--) {
            pushNode.accept(parent, childIndex);
        }
    }
}
