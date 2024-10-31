/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.utils;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.Keyed;
import java.io.IOException;
import java.util.Objects;

public class LongMerkleLeaf extends LongLeaf implements Keyed<Long> {
    // For serialization
    private static final long CLASS_ID = 998829382944383L;
    private static final int VERSION_0 = 0;
    private static final int CURRENT_VERSION = VERSION_0;

    private long key = 0L;

    public LongMerkleLeaf() {}

    public LongMerkleLeaf(long key, long value) {
        super(value);
        this.key = key;
    }

    @Override
    public Long getKey() {
        return key;
    }

    @Override
    public void setKey(Long aLong) {
        this.key = aLong;
    }

    /** {@inheritDoc} */
    @Override
    public LongMerkleLeaf copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new LongMerkleLeaf(key, getValue());
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(final SerializableDataInputStream in, int ver) throws IOException {
        // We cannot parse streams from future versions.
        if (ver > VERSION_0) {
            throw new IllegalArgumentException("The version number of the stream is too new.");
        }

        super.deserialize(in, ver);
        this.key = in.readLong();
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        super.serialize(out);
        out.writeLong(this.key);
    }

    /** {@inheritDoc} */
    @Override
    public int getClassVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongMerkleLeaf that)) return false;
        if (!super.equals(o)) return false;
        return key == that.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), key);
    }
}
