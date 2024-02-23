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

package com.swirlds.merkle.map.test.pta;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;

/**
 * A merkle wrapper around {@link MapKey}.
 */
public class MerkleMapKey extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0x66ff4872c3ae8c5eL;

    private static final class ClassVersion {

        public static final int ORIGINAL = 1;
    }

    private MapKey mapKey;

    public MerkleMapKey() {}

    public MerkleMapKey(final MapKey mapKey) {
        this.mapKey = mapKey;
    }

    private MerkleMapKey(final MerkleMapKey that) {
        this.mapKey = that.mapKey.copy();
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
     * Get the map key.
     */
    public MapKey getMapKey() {
        return mapKey;
    }

    /**
     * Set the map key.
     */
    public void setMapKey(final MapKey mapKey) {
        this.mapKey = mapKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleMapKey copy() {
        return new MerkleMapKey(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeSerializable(mapKey, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        mapKey = in.readSerializable(true, MapKey::new);
    }
}
