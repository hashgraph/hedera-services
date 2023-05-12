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

package com.swirlds.common.merkle.impl;

import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.internal.AbstractMerkleNode;

/**
 * This abstract implements boilerplate functionality for a {@link MerkleLeaf}. Classes that implement
 * {@link MerkleLeaf} are not required to extend this class, but absent a reason it is recommended to avoid
 * re-implementation of this code.
 */
public non-sealed class PartialMerkleLeaf extends AbstractMerkleNode {

    public PartialMerkleLeaf() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isLeaf() {
        return true;
    }

    /**
     * Copy constructor.
     */
    protected PartialMerkleLeaf(final PartialMerkleLeaf that) {
        super(that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void onDestroy() {
        destroyNode();
    }
}
