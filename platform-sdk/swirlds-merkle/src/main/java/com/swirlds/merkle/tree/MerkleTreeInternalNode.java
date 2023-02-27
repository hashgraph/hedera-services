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

package com.swirlds.merkle.tree;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;

public final class MerkleTreeInternalNode extends PartialBinaryMerkleInternal implements MerkleInternal {

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public static final long CLASS_ID = 0x1b1c07ad7dc65f17L;

    public MerkleTreeInternalNode() {
        super();
    }

    private MerkleTreeInternalNode(final MerkleTreeInternalNode sourceNode) {
        super(sourceNode);
        setImmutable(false);
        sourceNode.setImmutable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleTreeInternalNode copy() {
        throwIfImmutable();
        throwIfDestroyed();
        return new MerkleTreeInternalNode(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
