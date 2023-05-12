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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class holds the key to find the bytecode for a smart contract.
 */
public final class SmartContractByteCodeMapKey implements VirtualLongKey {
    private static final long CLASS_ID = 0xbc79f9cbac162595L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long contractId;

    public SmartContractByteCodeMapKey() {
        this(-1);
    }

    /**
     * Given the {@code contractId} of a smart contract, two other ids are computed
     * to enforce that this key has {@code 3} longs to look similar to what
     * we have in services.
     *
     * @param contractId
     * 		The id of the smart contract that is the owner of the bytecode
     * 		that can be found by this key.
     */
    public SmartContractByteCodeMapKey(final long contractId) {
        this.contractId = contractId;
    }

    public static int getSizeInBytes() {
        return Long.BYTES;
    }

    @Override
    public long getKeyAsLong() {
        return contractId;
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
        out.writeLong(contractId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        contractId = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final ByteBuffer buffer) throws IOException {
        buffer.putLong(contractId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        contractId = buffer.getLong();
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

        final SmartContractByteCodeMapKey that = (SmartContractByteCodeMapKey) o;

        return new EqualsBuilder().append(contractId, that.contractId).isEquals();
    }

    /**
     * Verifies if the content from {@code buffer} is equal to the content of this instance.
     *
     * @param buffer
     * 		The buffer with data to be compared with this class.
     * @param version
     * 		The version of the data inside the given {@code buffer}.
     * @return {@code true} if the content from the buffer has the same data as this instance.
     *        {@code false}, otherwise.
     * @throws IOException
     */
    public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
        return buffer.getLong() == this.contractId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(contractId).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmartContractByteCodeMapKey{" + "contractId=" + contractId + '}';
    }

    /**
     * @return The id of the contract which owns the byte code that can be found by this key.
     */
    public long getContractId() {
        return contractId;
    }
}
