/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/** Representation of a 256bit unsigned int, stored internally as a big-endian byte array. */
@SuppressWarnings({"PointlessBitwiseExpression", "unused"})
public class ContractValue implements VirtualValue {
    public static final int MERKLE_VERSION = 1;
    public static final int SERIALIZED_SIZE = 32;
    public static final long RUNTIME_CONSTRUCTABLE_ID = 0xd7c4802f00979857L;
    /** this is the raw big-endian data for the unit256 */
    private byte[] uint256Value;
    /** if this class is immutable */
    private boolean isImmutable = false;

    private static final String IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR =
            "Tried to set value on immutable " + "ContractValue";

    public static ContractValue from(final UInt256 value) {
        return new ContractValue(value.toArray());
    }

    /** Construct a zero ContractValue */
    public ContractValue() {
        this.uint256Value = new byte[32];
    }

    /**
     * Construct a new ContractValue with its 8 least significant bytes set from a long
     *
     * @param value long value to be used
     */
    public ContractValue(long value) {
        setValue(value);
    }

    /**
     * Construct a new ContractValue with a BigInteger, see setValue(BigInteger) for details
     *
     * @param value BigInteger value to be used
     */
    public ContractValue(BigInteger value) {
        Objects.requireNonNull(value);
        setValue(value);
    }

    /**
     * Construct a new ContractValue directly with a 32 byte big endian value. A copy is not made!
     * It is assumed you will not mutate the byte[] after you call this method.
     *
     * @param bigEndianValue big endian value to be used
     */
    public ContractValue(byte[] bigEndianValue) {
        Objects.requireNonNull(bigEndianValue);
        setValue(bigEndianValue);
    }

    /**
     * Directly gets the value of this ContractValue to the given byte array by reference. A copy is
     * not made! It is assumed you will not mutate the byte[] after you call this method.
     *
     * @return a big endian uint256 as byte[32]
     */
    public byte[] getValue() {
        return uint256Value;
    }

    public UInt256 asUInt256() {
        return UInt256.fromBytes(Bytes32.wrap(uint256Value));
    }

    /**
     * Gets the value of this ContractValue as a BigInteger
     *
     * @return BigInteger value of ContractValue
     */
    public BigInteger asBigInteger() {
        return new BigInteger(this.uint256Value, 0, 32);
    }

    /**
     * Gets the lower 8 bytes of the value of this ContractValue as a long
     *
     * @return long value of ContractValue
     */
    public long asLong() {
        return (((long) uint256Value[24] << 56)
                + ((long) (uint256Value[25] & 255) << 48)
                + ((long) (uint256Value[26] & 255) << 40)
                + ((long) (uint256Value[27] & 255) << 32)
                + ((long) (uint256Value[28] & 255) << 24)
                + ((uint256Value[29] & 255) << 16)
                + ((uint256Value[30] & 255) << 8)
                + ((uint256Value[31] & 255) << 0));
    }

    /**
     * Directly sets the value of this ContractValue to the given byte array by reference. A copy is
     * not made! It is assumed you will not mutate the byte[] after you call this method.
     *
     * @param bigEndianUint256Value a big endian uint256 as byte[32]
     */
    public void setValue(byte[] bigEndianUint256Value) {
        Objects.requireNonNull(bigEndianUint256Value);
        if (isImmutable)
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        if (bigEndianUint256Value.length != 32) {
            throw new IllegalArgumentException(
                    "Tried to set ContractValue value with array that is not 32 bytes.");
        }
        this.uint256Value = bigEndianUint256Value;
    }

    /**
     * Set the value with a BigInteger. If the BigInteger is signed then the sign is dropped. If the
     * BigInteger is longer than 32 byte then just the 32 least significant bytes are taken.
     *
     * @param value BigInteger, should really be unsigned and less than or equal to 32 bytes
     */
    public void setValue(BigInteger value) {
        Objects.requireNonNull(value);
        if (isImmutable)
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        byte[] bigIntegerBytes = value.toByteArray();
        bigIntegerBytes[0] &= 0b01111111; // remove sign
        if (bigIntegerBytes.length == 32) {
            this.uint256Value = bigIntegerBytes;
        } else if (bigIntegerBytes.length > 32) {
            this.uint256Value = new byte[32];
            System.arraycopy(
                    bigIntegerBytes, bigIntegerBytes.length - 32, this.uint256Value, 0, 32);
        } else {
            this.uint256Value = new byte[32];
            System.arraycopy(
                    bigIntegerBytes,
                    0,
                    this.uint256Value,
                    32 - bigIntegerBytes.length,
                    bigIntegerBytes.length);
        }
    }

    /**
     * Set the value with a long, this will set the 8 least significant bytes to the long and the
     * rest to zeros.
     *
     * @param value long value
     */
    public void setValue(long value) {
        if (isImmutable)
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        this.uint256Value = new byte[32];
        this.uint256Value[24] = (byte) (value >>> 56);
        this.uint256Value[25] = (byte) (value >>> 48);
        this.uint256Value[26] = (byte) (value >>> 40);
        this.uint256Value[27] = (byte) (value >>> 32);
        this.uint256Value[28] = (byte) (value >>> 24);
        this.uint256Value[29] = (byte) (value >>> 16);
        this.uint256Value[30] = (byte) (value >>> 8);
        this.uint256Value[31] = (byte) (value >>> 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractValue contractValue = (ContractValue) o;
        return Arrays.equals(uint256Value, contractValue.uint256Value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(uint256Value);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ContractValue{");
        sb.append(asBigInteger());
        sb.append("(");
        for (int i = 0; i < 32; i++) {
            sb.append(String.format("%02X ", this.uint256Value[i]).toUpperCase());
        }
        sb.append(")}");
        return sb.toString();
    }

    @Override
    public ContractValue copy() {
        // It is immutable anyway
        return new ContractValue(this.uint256Value);
    }

    @Override
    public ContractValue asReadOnly() { // is it too expensive to make a copy here?
        ContractValue immutableValue = copy();
        immutableValue.isImmutable = true;
        return immutableValue;
    }

    // =================================================================================================================
    // Serialization Methods

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public void serialize(SerializableDataOutputStream outputStream) throws IOException {
        outputStream.write(this.uint256Value);
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.put(this.uint256Value);
    }

    @Override
    public void deserialize(SerializableDataInputStream inputStream, int i) throws IOException {
        if (isImmutable)
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        int lengthRead = inputStream.read(this.uint256Value);
        assert lengthRead == SERIALIZED_SIZE;
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {
        if (isImmutable)
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        byteBuffer.get(this.uint256Value);
    }
}
