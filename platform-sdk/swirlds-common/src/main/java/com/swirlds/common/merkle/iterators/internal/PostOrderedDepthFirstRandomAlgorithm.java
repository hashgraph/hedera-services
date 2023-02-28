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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#POST_ORDERED_DEPTH_FIRST_RANDOM
 * POST_ORDERED_DEPTH_FIRST_RANDOM}.
 */
public class PostOrderedDepthFirstRandomAlgorithm extends PostOrderedDepthFirstAlgorithm {

    private final Random random = new Random();

    /**
     * Add children to the stack in a random order.
     * {@inheritDoc}
     */
    @Override
    public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
        final List<Integer> iterationOrder = new ArrayList<>(parent.getNumberOfChildren());
        for (int childIndex = 0; childIndex < parent.getNumberOfChildren(); childIndex++) {
            iterationOrder.add(childIndex);
        }

        Collections.shuffle(iterationOrder, random);
        for (final int childIndex : iterationOrder) {
            pushNode.accept(parent, childIndex);
        }
    }
}
