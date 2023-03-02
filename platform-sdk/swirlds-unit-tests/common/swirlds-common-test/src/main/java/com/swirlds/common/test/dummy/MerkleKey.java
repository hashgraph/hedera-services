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

package com.swirlds.common.test.dummy;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;

/**
 * A merkle wrapper for {@link Key}.
 */
public class MerkleKey extends PartialMerkleLeaf implements MerkleLeaf {

    private Key key;

    public static final long CLASS_ID = 0x7183149501d8201dL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    public MerkleKey() {}

    public MerkleKey(final Key key) {
        this.key = key;
    }

    private MerkleKey(final MerkleKey that) {
        super(that);
        this.key = that.key;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(key, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        key = in.readSerializable(false, Key::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleKey copy() {
        return new MerkleKey(this);
    }

    /**
     * Get the key.
     */
    public Key getKey() {
        return key;
    }
}
