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
package com.hedera.services.state.virtual;

import static com.hedera.services.state.virtual.KeyPackingUtils.computeNonZeroBytes;
import static com.hedera.services.state.virtual.KeyPackingUtils.serializePackedBytesToBuffer;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualValue;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Representation of a 256bit unsigned int, stored internally as a big-endian byte array. */
@SuppressWarnings({"PointlessBitwiseExpression", "unused"})
public class IterableContractValue implements VirtualValue {
    public static final int ITERABLE_VERSION = 2;

    public static final int NON_ITERABLE_SERIALIZED_SIZE = 32;
    public static final int ITERABLE_SERIALIZED_SIZE = DataFileCommon.VARIABLE_DATA_SIZE;

    public static final long RUNTIME_CONSTRUCTABLE_ID = 0xabf8bd64a87ee740L;

    // The raw big-endian data for the uint256 EVM value
    private byte[] uint256Value;
    // Marks if this instance is immutable
    private boolean isImmutable = false;

    // The previous key in the doubly-linked list of the owning contract's storage mappings; this is
    // null if this
    // value belongs to the root mapping in the list
    private int[] prevUint256Key;
    // Number of the low-order bytes in prevUint256Key that contain ones
    private byte prevUint256KeyNonZeroBytes;
    // The next key in the doubly-linked list of the owning contract's storage mappings; this is
    // null if this value
    // belongs to the root mapping in the list
    private int[] nextUint256Key;
    // Number of the low-order bytes in nextUint256Key that contain ones
    private byte nextUint256KeyNonZeroBytes;

    private static final String IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR =
            "Tried to set value on immutable ContractValue";

    public static IterableContractValue from(final UInt256 value) {
        return new IterableContractValue(value.toArray());
    }

    /** Construct a zero ContractValue */
    public IterableContractValue() {
        this.uint256Value = new byte[32];
    }

    /**
     * Construct a new ContractValue with its 8 least significant bytes set from a long
     *
     * @param value long value to be used
     */
    public IterableContractValue(long value) {
        setValue(value);
    }

    /**
     * Construct a new ContractValue with a BigInteger, see setValue(BigInteger) for details
     *
     * @param value BigInteger value to be used
     */
    public IterableContractValue(BigInteger value) {
        Objects.requireNonNull(value);
        setValue(value);
    }

    /**
     * Construct a new ContractValue directly with a 32 byte big endian value. A copy is not made!
     * It is assumed you will not mutate the byte[] after you call this method.
     *
     * @param bigEndianValue big endian value to be used
     */
    public IterableContractValue(byte[] bigEndianValue) {
        Objects.requireNonNull(bigEndianValue);
        setValue(bigEndianValue);
    }

    public IterableContractValue(
            final byte[] bigEndianValue, final int[] prevUint256Key, final int[] nextUint256Key) {
        Objects.requireNonNull(bigEndianValue);
        setValue(bigEndianValue);
        setExplicitPrevKey(prevUint256Key);
        setExplicitNextKey(nextUint256Key);
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
    public void setValue(final byte[] bigEndianUint256Value) {
        Objects.requireNonNull(bigEndianUint256Value);
        if (isImmutable) {
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        }
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
        if (isImmutable) {
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        }
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
        if (isImmutable) {
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        }
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
    public boolean isImmutable() {
        return isImmutable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (IterableContractValue) o;
        return Arrays.equals(uint256Value, that.uint256Value)
                && Arrays.equals(prevUint256Key, that.prevUint256Key)
                && Arrays.equals(nextUint256Key, that.nextUint256Key);
    }

    @Override
    public int hashCode() {
        var result = Arrays.hashCode(uint256Value);
        result = 31 * result + Arrays.hashCode(prevUint256Key);
        return 31 * result + Arrays.hashCode(nextUint256Key);
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
        sb.append("), prevKey=")
                .append(KeyPackingUtils.readableContractStorageKey(prevUint256Key))
                .append(", nextKey=")
                .append(KeyPackingUtils.readableContractStorageKey(nextUint256Key))
                .append("}");
        return sb.toString();
    }

    @Override
    public IterableContractValue copy() {
        isImmutable = true;
        return new IterableContractValue(uint256Value, prevUint256Key, nextUint256Key);
    }

    @Override
    public IterableContractValue asReadOnly() { // is it too expensive to make a copy here?
        final var readOnlyThat =
                new IterableContractValue(uint256Value, prevUint256Key, nextUint256Key);
        readOnlyThat.isImmutable = true;
        return readOnlyThat;
    }

    // =================================================================================================================
    // Iteration support methods

    /**
     * Given the id of the owning contract (which must be known to the caller), returns the previous
     * key in the doubly-linked list of the owning contract's storage mappings; or null if this
     * value is in the root mapping.
     *
     * @param contractId the id of the contract to which this value belongs
     * @return the key to retrieve the previous mapping in this contract's storage list, null if
     *     none exists
     */
    public ContractKey getPrevKeyScopedTo(final long contractId) {
        return prevUint256Key != null ? new ContractKey(contractId, prevUint256Key) : null;
    }

    public int[] getExplicitPrevKey() {
        return prevUint256Key;
    }

    /**
     * Given the id of the owning contract (which must be known to the caller), returns the next key
     * in the doubly-linked list of the owning contract's storage mappings; or null if this value is
     * in the last mapping.
     *
     * @param contractId the id of the contract to which this value belongs
     * @return the key to retrieve the previous mapping in this contract's storage list, null if
     *     none exists
     */
    public ContractKey getNextKeyScopedTo(final long contractId) {
        return nextUint256Key != null ? new ContractKey(contractId, nextUint256Key) : null;
    }

    public int[] getExplicitNextKey() {
        return nextUint256Key;
    }

    /**
     * Nulls out this value's memory of a preceding key in the doubly-linked list of the owning
     * contract's storage mappings.
     */
    public void markAsRootMapping() {
        throwIfImmutable("Cannot mark an immutable value as the root mapping");
        prevUint256Key = null;
    }

    /**
     * Nulls out this value's memory of a following key in the doubly-linked list of the owning
     * contract's storage mappings.
     */
    public void markAsLastMapping() {
        throwIfImmutable("Cannot mark an immutable value as the last mapping");
        nextUint256Key = null;
    }

    /**
     * Given the 256-bit key of an EVM storage mapping, updates this value to track the implied
     * mapping as the previous one in the doubly-linked list of the owning contract's storage
     * mappings.
     *
     * @param evmKey the EVM key of the previous mapping in the owning contract's storage
     */
    public void setPrevKey(@NotNull final int[] evmKey) {
        throwIfImmutable("Cannot set the previous key on an immutable value");
        setExplicitPrevKey(evmKey);
    }

    /**
     * Given the 256-bit key of an EVM storage mapping, updates this value to track the implied
     * mapping as the next one in the doubly-linked list of the owning contract's storage mappings.
     *
     * @param evmKey the EVM key of the next mapping in the owning contract's storage
     */
    public void setNextKey(@NotNull final int[] evmKey) {
        throwIfImmutable("Cannot set the next key on an immutable value");
        setExplicitNextKey(evmKey);
    }

    // =================================================================================================================
    // Serialization Methods
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return ITERABLE_VERSION;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.write(uint256Value);
        KeyPackingUtils.serializePossiblyMissingKey(
                prevUint256Key, prevUint256KeyNonZeroBytes, out);
        KeyPackingUtils.serializePossiblyMissingKey(
                nextUint256Key, nextUint256KeyNonZeroBytes, out);
    }

    @Override
    public void serialize(final ByteBuffer out) throws IOException {
        out.put(uint256Value);
        serializePossiblyMissingKeyToBuffer(prevUint256Key, prevUint256KeyNonZeroBytes, out);
        serializePossiblyMissingKeyToBuffer(nextUint256Key, nextUint256KeyNonZeroBytes, out);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        if (isImmutable) {
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        }
        final var lengthRead = in.read(this.uint256Value);
        assert lengthRead == NON_ITERABLE_SERIALIZED_SIZE;
        deserializeKeys(in, SerializableDataInputStream::readByte);
    }

    @Override
    public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
        if (isImmutable) {
            throw new IllegalStateException(IMMUTABLE_CONTRACT_VALUE_MANIPULATION_ERROR);
        }
        buffer.get(this.uint256Value);
        deserializeKeys(buffer, ByteBuffer::get);
    }

    // --- Internal helpers
    private <D> void deserializeKeys(final D in, final KeyPackingUtils.ByteReaderFunction<D> reader)
            throws IOException {
        byte marker = reader.read(in);
        if (marker != KeyPackingUtils.MISSING_KEY_SENTINEL) {
            prevUint256KeyNonZeroBytes = marker;
            prevUint256Key =
                    KeyPackingUtils.deserializeUint256Key(prevUint256KeyNonZeroBytes, in, reader);
        }
        marker = reader.read(in);
        if (marker != KeyPackingUtils.MISSING_KEY_SENTINEL) {
            nextUint256KeyNonZeroBytes = marker;
            nextUint256Key =
                    KeyPackingUtils.deserializeUint256Key(nextUint256KeyNonZeroBytes, in, reader);
        }
    }

    private void setExplicitPrevKey(final int[] packedEvmKey) {
        this.prevUint256Key = packedEvmKey;
        if (packedEvmKey != null) {
            this.prevUint256KeyNonZeroBytes = computeNonZeroBytes(prevUint256Key);
        }
    }

    private void setExplicitNextKey(final int[] packedEvmKey) {
        this.nextUint256Key = packedEvmKey;
        if (packedEvmKey != null) {
            this.nextUint256KeyNonZeroBytes = computeNonZeroBytes(nextUint256Key);
        }
    }

    private void serializePossiblyMissingKeyToBuffer(
            final @Nullable int[] key, final byte nonZeroBytes, final ByteBuffer out) {
        if (key == null) {
            out.put(KeyPackingUtils.MISSING_KEY_SENTINEL);
        } else {
            out.put(nonZeroBytes);
            serializePackedBytesToBuffer(key, nonZeroBytes, out);
        }
    }

    // --- Only used by unit tests
    @VisibleForTesting
    byte getPrevUint256KeyNonZeroBytes() {
        return prevUint256KeyNonZeroBytes;
    }

    @VisibleForTesting
    byte getNextUint256KeyNonZeroBytes() {
        return nextUint256KeyNonZeroBytes;
    }
}
