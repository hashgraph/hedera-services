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
import java.util.function.ObjIntConsumer;

/**
 * Iteration algorithm for
 * {@link com.swirlds.common.merkle.iterators.MerkleIterationOrder#PRE_ORDERED_DEPTH_FIRST PRE_ORDERED_DEPTH_FIRST}.
 */
public class PreOrderedDepthFirstAlgorithm extends PostOrderedDepthFirstAlgorithm {

    /**
     * {@inheritDoc}
     */
    @Override
    public void pushChildren(final MerkleInternal parent, final ObjIntConsumer<MerkleInternal> pushNode) {
        // Swap the order of the parent and child to switch to pre-ordered
        pop();
        super.pushChildren(parent, pushNode);
        push(parent);
    }
}
