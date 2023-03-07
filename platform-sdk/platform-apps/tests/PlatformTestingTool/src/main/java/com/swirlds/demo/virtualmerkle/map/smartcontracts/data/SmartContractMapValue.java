/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.swirlds.demo.virtualmerkle.random.PTTRandom;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This is the value of a key value pair from a smart contract.
 * Please, read {@link SmartContractMapKey} to understand
 * smart contract's data.
 */
public final class SmartContractMapValue implements VirtualValue {
    private static final long CLASS_ID = 0xed6c1e1f0b6bda20L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private byte[] value;

    /**
     * This constructor makes sure that {@link #value} has exactly {@link #getSizeInBytes()}.
     */
    public SmartContractMapValue() {
        value = new byte[getSizeInBytes()];
    }

    /**
     * This constructor makes sure that {@link #value} has exactly {@link #getSizeInBytes()} random bytes.
     *
     * @param random
     * 		A {@link PTTRandom} instance to generate random bytes for {@link #value}.
     */
    public SmartContractMapValue(final PTTRandom random) {
        this.value = new byte[getSizeInBytes()];
        changeValue(random);
    }

    /**
     * Create a {@link SmartContractMapValue} from another {@link SmartContractMapValue} instance.
     *
     * @param source
     * 		A {@link SmartContractMapValue} instance to be used a the source of creation for the new one.
     */
    public SmartContractMapValue(final SmartContractMapValue source) {
        this.value = source.getValue();
    }

    /**
     * This constructor makes sure that {@link #value} has exactly {@link #getSizeInBytes()} random bytes.
     *
     * @param value
     * 		An array of bytes to represent the {@link #value}.
     * @throws IllegalArgumentException
     * 		If the given {@code value} does not have exactly {@link #getSizeInBytes()}.
     */
    public SmartContractMapValue(final byte[] value) {
        if (value.length != getSizeInBytes()) {
            throw new IllegalArgumentException("Invalid value for smart contract");
        }
        this.value = Arrays.copyOf(value, getSizeInBytes());
    }

    /**
     * Creates an instance with a array of bytes representing the given {@code value}.
     *
     * @param value
     * 		A long value.
     */
    public SmartContractMapValue(final long value) {
        this.value = ByteBuffer.allocate(getSizeInBytes()).putLong(value).array();
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
    public SmartContractMapValue copy() {
        return new SmartContractMapValue(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SmartContractMapValue asReadOnly() {
        return new SmartContractMapValue(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmartContractMapValue{" + "value=" + value + '}';
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

        final SmartContractMapValue that = (SmartContractMapValue) o;

        return new EqualsBuilder().append(value, that.value).isEquals();
    }

    public byte[] getValue() {
        return Arrays.copyOf(value, value.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(value).toHashCode();
    }

    public static int getSizeInBytes() {
        return 32;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // Raw write is used due to fixed byte count requirement
        out.write(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        value = new byte[getSizeInBytes()];
        if (in.read(value) != getSizeInBytes()) {
            throw new IOException("Failed to read the correct number of bytes!");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final ByteBuffer buffer) throws IOException {
        buffer.put(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        buffer.get(this.value, 0, getSizeInBytes());
    }

    /**
     * Used for a special case.
     * This class also is used to store the number of key value pairs a contract has.
     * Just to avoid having a new map only for that.
     *
     * @return The {@link Long} value stored inside {@link #value}.
     */
    public long getValueAsLong() {
        return ByteBuffer.wrap(value).getLong();
    }

    /**
     * Changes the value to a new array of random bytes.
     *
     * @param pttRandom
     * 		A {@link PTTRandom} instance to generate random bytes.
     */
    public void changeValue(final PTTRandom pttRandom) {
        for (int i = 0; i < getSizeInBytes(); i++) {
            this.value[i] = pttRandom.nextByte();
        }
    }
}
