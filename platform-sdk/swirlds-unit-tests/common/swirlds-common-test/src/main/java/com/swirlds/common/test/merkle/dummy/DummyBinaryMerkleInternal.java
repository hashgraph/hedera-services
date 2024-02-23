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

package com.swirlds.common.test.merkle.dummy;

import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;

/**
 * A binary merkle node for testing purposes.
 */
public class DummyBinaryMerkleInternal extends PartialBinaryMerkleInternal implements MerkleInternal {

    private static final long CLASS_ID = 0x1a2da64b35e8430fL;
    private static final int VERSION = 1;

    public DummyBinaryMerkleInternal() {}

    private DummyBinaryMerkleInternal(final DummyBinaryMerkleInternal that) {
        super(that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DummyBinaryMerkleInternal copy() {
        return new DummyBinaryMerkleInternal(this);
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
        return VERSION;
    }
}
