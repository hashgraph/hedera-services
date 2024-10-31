/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.utility;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Objects;

/**
 * A utility node that contains a long value.
 */
public class MerkleLong extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0x46cd791173861c4cL;

    private static final int CLASS_VERSION = 1;

    private long value;

    public MerkleLong() {}

    public MerkleLong(final long value) {
        this.value = value;
    }

    protected MerkleLong(final MerkleLong that) {
        super(that);
        value = that.value;
        that.setImmutable(true);
    }

    /**
     * get the long value in {@link MerkleLong}
     */
    public long getValue() {
        return value;
    }

    /**
     * Increment the long value by 1
     */
    public void increment() {
        throwIfImmutable();
        value++;
        invalidateHash();
    }

    /**
     * Decrement the long value by 1
     */
    public void decrement() {
        throwIfImmutable();
        value--;
        invalidateHash();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleLong copy() {
        throwIfImmutable();
        return new MerkleLong(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        value = in.readLong();
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
    public int getClassVersion() {
        return CLASS_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MerkleLong)) {
            return false;
        }

        final MerkleLong that = (MerkleLong) o;
        return value == that.value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("value", value).toString();
    }
}
