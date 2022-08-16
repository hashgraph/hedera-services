/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;

public class MerkleAccountTokensPlaceholder extends PartialMerkleLeaf implements MerkleLeaf {
    private static final int MERKLE_VERSION = 1;
    private static final long RUNTIME_CONSTRUCTABLE_ID = 0x4dd9cde14aae5f8eL;
    private static final long[] ALWAYS_EMPTY_IDS = new long[0];

    public MerkleAccountTokensPlaceholder() {
        // RuntimeConstructable
    }

    /* --- MerkleLeaf --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        in.readLongArray(Integer.MAX_VALUE);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLongArray(ALWAYS_EMPTY_IDS);
    }

    @Override
    public MerkleAccountTokensPlaceholder copy() {
        setImmutable(true);
        return new MerkleAccountTokensPlaceholder();
    }
}
