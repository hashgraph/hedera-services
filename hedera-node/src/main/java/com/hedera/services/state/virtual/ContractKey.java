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

import static com.hedera.services.state.virtual.KeyPackingUtils.deserializeUint256Key;
import static com.hedera.services.state.virtual.KeyPackingUtils.serializePackedBytes;
import static com.hedera.services.state.virtual.KeyPackingUtils.serializePackedBytesToBuffer;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

/**
 * The key of a key/value pair used by a Smart Contract for storage purposes.
 *
 * <p>We only store the number part of the contract ID as the ideas ia there will be a virtual
 * merkle tree for each shard and realm.
 */
public final class ContractKey implements VirtualKey<ContractKey> {
    /** The shifts required to deserialize a big-endian contractId with leading zeros omitted */
    private static final int[] BIT_SHIFTS = {0, 8, 16, 24, 32, 40, 48, 56};
    /** The estimated average size for a contract key when serialized */
    public static final int ESTIMATED_AVERAGE_SIZE =
            20; // assume 50% full typically, max size is (1 + 8 + 32)
    /** this is the number part of the contract address */
    private long contractId;
    /** number of the least significant bytes in contractId that contain ones. Max is 8 */
    private byte contractIdNonZeroBytes;
    /** this is the raw data for the unit256 */
    private int[] uint256Key;
    /** number of the least significant bytes in uint256Key that contain ones. Max is 32 */
    private byte uint256KeyNonZeroBytes;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0xb2c0a1f733950abdL;
    static final int MERKLE_VERSION = 1;

    public ContractKey() {
        // there has to be a default constructor for deserialize
    }

    public static ContractKey from(final AccountID id, final UInt256 key) {
        return new ContractKey(id.getAccountNum(), key.toArray());
    }

    public static ContractKey from(final long accountNum, final UInt256 key) {
        return new ContractKey(accountNum, key.toArray());
    }

    public ContractKey(long contractId, long key) {
        setContractId(contractId);
        setKey(key);
    }

    public ContractKey(long contractId, byte[] data) {
        this(contractId, KeyPackingUtils.asPackedInts(data));
    }

    public ContractKey(long contractId, int[] key) {
        setContractId(contractId);
        setKey(key);
    }

    public static int[] asPackedInts(final UInt256 evmKey) {
        return KeyPackingUtils.asPackedInts(evmKey.toArrayUnsafe());
    }

    public long getContractId() {
        return contractId;
    }

    public void setContractId(long contractId) {
        this.contractId = contractId;
        this.contractIdNonZeroBytes = KeyPackingUtils.computeNonZeroBytes(contractId);
    }

    public int[] getKey() {
        return uint256Key;
    }

    public BigInteger getKeyAsBigInteger() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(uint256Key);
        return new BigInteger(buf.array());
    }

    public void setKey(long key) {
        setKey(new int[] {0, 0, 0, 0, 0, 0, (int) (key >> Integer.SIZE), (int) key});
    }

    public void setKey(int[] uint256Key) {
        if (uint256Key == null || uint256Key.length != 8) {
            throw new IllegalArgumentException(
                    "The key cannot be null and the key's packed int array size must be 8");
        }
        this.uint256Key = uint256Key;
        this.uint256KeyNonZeroBytes = KeyPackingUtils.computeNonZeroBytes(uint256Key);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContractKey that = (ContractKey) o;
        return contractId == that.contractId && Arrays.equals(uint256Key, that.uint256Key);
    }

    /** Special hash to make sure we get good distribution. */
    @Override
    public int hashCode() {
        return hash32(
                contractId,
                uint256Key[7],
                uint256Key[6],
                uint256Key[5],
                uint256Key[4],
                uint256Key[3],
                uint256Key[2],
                uint256Key[1],
                uint256Key[0]);
    }

    @Override
    public String toString() {
        return "ContractKey{id="
                + contractId
                + "("
                + Long.toHexString(contractId).toUpperCase()
                + "), key="
                + getKeyAsBigInteger()
                + "("
                + Arrays.stream(uint256Key)
                        .mapToObj(Integer::toHexString)
                        .collect(Collectors.joining(","))
                        .toUpperCase()
                + ")}";
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        serializeReturningByteWritten(out);
    }

    public int serializeReturningByteWritten(SerializableDataOutputStream out) throws IOException {
        out.write(getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            out.write((byte) (contractId >> (b * 8)));
        }
        serializePackedBytes(uint256Key, uint256KeyNonZeroBytes, out);
        return 1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes;
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) throws IOException {
        byteBuffer.put(getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            byteBuffer.put((byte) (contractId >> (b * 8)));
        }
        serializePackedBytesToBuffer(uint256Key, uint256KeyNonZeroBytes, byteBuffer);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        final byte packedSize = in.readByte();
        this.contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
        this.uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
        this.contractId =
                deserializeContractID(
                        contractIdNonZeroBytes, in, SerializableDataInputStream::readByte);
        this.uint256Key =
                deserializeUint256Key(
                        uint256KeyNonZeroBytes, in, SerializableDataInputStream::readByte);
    }

    @Override
    public void deserialize(ByteBuffer buf, int i) throws IOException {
        final byte packedSize = buf.get();
        this.contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
        this.uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
        this.contractId = deserializeContractID(contractIdNonZeroBytes, buf, ByteBuffer::get);
        this.uint256Key = deserializeUint256Key(uint256KeyNonZeroBytes, buf, ByteBuffer::get);
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    /**
     * Read the key size in bytes from a byte buffer containing a serialized ContractKey
     *
     * @param buf The buffer to read from, its position will be restored after we read
     * @return the size in byte for the key contained in buffer.
     */
    public static int readKeySize(ByteBuffer buf) {
        final byte packedSize = buf.get();
        buf.position(buf.position() - 1); // move position back, like we never read anything
        return 1
                + getContractIdNonZeroBytesFromPacked(packedSize)
                + getUint256KeyNonZeroBytesFromPacked(packedSize);
    }

    // =================================================================================================================
    // Private methods, left package for UnitTests

    /**
     * Deserialize long contract id from data source
     *
     * @param contractIdNonZeroBytes the number of non-zero bytes stored for the long contract id
     * @param dataSource The data source to read from
     * @param reader function to read a byte from the data source
     * @param <D> type for data source, e.g. ByteBuffer or InputStream
     * @return long contract id read
     * @throws IOException If there was a problem reading
     */
    static <D> long deserializeContractID(
            final byte contractIdNonZeroBytes,
            final D dataSource,
            final KeyPackingUtils.ByteReaderFunction<D> reader)
            throws IOException {
        long contractId = 0;
        /* Bytes are encountered in order of significance (big-endian) */
        for (int byteI = 0, shiftI = contractIdNonZeroBytes - 1;
                byteI < contractIdNonZeroBytes;
                byteI++, shiftI--) {
            contractId |= ((long) reader.read(dataSource) & 255) << BIT_SHIFTS[shiftI];
        }
        return contractId;
    }

    /**
     * Get contractIdNonZeroBytes and uint256KeyNonZeroBytes packed into a single byte.
     *
     * <p>contractIdNonZeroBytes is in the range of 0-8 uint256KeyNonZeroBytes is in the range of
     * 0-32
     *
     * <p>As those can not be packed in their entirety in a single byte we accept a minimum of 1, so
     * we store range 1-9 and 1-32 packed into a single byte.
     *
     * @return packed byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
     */
    byte getContractIdNonZeroBytesAndUint256KeyNonZeroBytes() {
        final byte contractIdNonZeroBytesMinusOne =
                contractIdNonZeroBytes == 0 ? (byte) 0 : (byte) (contractIdNonZeroBytes - 1);
        final byte uint256KeyNonZeroBytesMinusOne =
                uint256KeyNonZeroBytes == 0 ? (byte) 0 : (byte) (uint256KeyNonZeroBytes - 1);
        return (byte)
                ((contractIdNonZeroBytesMinusOne << 5) | uint256KeyNonZeroBytesMinusOne & 0xff);
    }

    /**
     * get contractIdNonZeroBytes from packed byte
     *
     * @param packed byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
     * @return contractIdNonZeroBytes
     */
    static byte getContractIdNonZeroBytesFromPacked(byte packed) {
        return (byte) ((Byte.toUnsignedInt(packed) >> 5) + 1);
    }

    /**
     * Get uint256KeyNonZeroBytes from packed byte
     *
     * @param packed byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
     * @return uint256KeyNonZeroBytes
     */
    static byte getUint256KeyNonZeroBytesFromPacked(byte packed) {
        return (byte) ((packed & 0b00011111) + 1);
    }

    /** get contractIdNonZeroBytes for tests */
    byte getContractIdNonZeroBytes() {
        return contractIdNonZeroBytes;
    }

    /** get uint256KeyNonZeroBytes for tests */
    public byte getUint256KeyNonZeroBytes() {
        return uint256KeyNonZeroBytes;
    }

    /**
     * Get a single byte out of our Unit256 stored as 8 integers in an int array.
     *
     * @param byteIndex The index of the byte we want with 0 being the least significant byte, and
     *     31 being the most significant.
     * @return the byte at given index
     */
    public byte getUint256Byte(final int byteIndex) {
        return KeyPackingUtils.extractByte(uint256Key, byteIndex);
    }

    @Override
    public int compareTo(@NotNull final ContractKey that) {
        if (this == that) {
            return 0;
        }
        final var order = Long.compare(this.contractId, that.contractId);
        if (order != 0) {
            return order;
        }
        return Arrays.compare(uint256Key, that.uint256Key);
    }

    @Override
    public int getMinimumSupportedVersion() {
        return 1;
    }
}
