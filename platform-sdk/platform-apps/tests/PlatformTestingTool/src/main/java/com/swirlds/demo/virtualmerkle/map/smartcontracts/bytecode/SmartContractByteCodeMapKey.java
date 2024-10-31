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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.util.Objects;

/**
 * This class holds the key to find the bytecode for a smart contract.
 */
public final class SmartContractByteCodeMapKey implements VirtualKey {

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

    static int getSizeInBytes() {
        return Long.BYTES;
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(contractId);
    }

    public void serialize(final WritableSequentialData out) {
        out.writeLong(contractId);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        contractId = in.readLong();
    }

    void deserialize(final ReadableSequentialData in) {
        contractId = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final SmartContractByteCodeMapKey that = (SmartContractByteCodeMapKey) other;
        return contractId == that.contractId;
    }

    /**
     * Verifies if the content from {@code buffer} is equal to the content of this instance.
     *
     * @param buffer
     * 		The buffer with data to be compared with this class.
     * @return {@code true} if the content from the buffer has the same data as this instance.
     *        {@code false}, otherwise.
     */
    boolean equals(final BufferedData buffer) {
        return buffer.readLong() == this.contractId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(contractId);
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
