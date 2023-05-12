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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.data;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A smart contract has a list of key value pairs to store its data.
 * Each pair has a key and a value. A key is made of the {@code contractId}
 * and the {@code keyValuePairIndex} which is its index on the
 * smart contract list.
 */
public final class SmartContractMapKey implements VirtualKey {
    private static final long CLASS_ID = 0x3760716d0ab5b622L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long contractId;

    private long keyValuePairIndex;

    /**
     * Builds a {@link SmartContractMapKey} with default values.
     */
    public SmartContractMapKey() {
        this(0L, 0L);
    }

    /**
     * Builds a {@link SmartContractMapKey} for the contract with id  {@code contractId},
     * and key value pair with index {@code keyValuePairIndex}.
     *
     * @param contractId
     * @param keyValuePairIndex
     */
    public SmartContractMapKey(final long contractId, final long keyValuePairIndex) {
        this.contractId = contractId;
        this.keyValuePairIndex = keyValuePairIndex;
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

    public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
        return contractId == buffer.getLong() && keyValuePairIndex == buffer.getLong();
    }

    public static int getSizeInBytes() {
        return 2 * Long.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final ByteBuffer buffer) throws IOException {
        buffer.putLong(contractId);
        buffer.putLong(keyValuePairIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        contractId = buffer.getLong();
        keyValuePairIndex = buffer.getLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(contractId);
        out.writeLong(keyValuePairIndex);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        contractId = in.readLong();
        keyValuePairIndex = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SmartContractMapKey that = (SmartContractMapKey) o;

        return new EqualsBuilder()
                .append(contractId, that.contractId)
                .append(keyValuePairIndex, that.keyValuePairIndex)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(contractId)
                .append(keyValuePairIndex)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmartContractMapKey{" + "contractId=" + contractId + ", keyValuePairIndex=" + keyValuePairIndex + '}';
    }
}
