package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

import static com.swirlds.jasperdb.utilities.NonCryptographicHashing.perm64;

/**
 * The key of a key/value pair used by a Smart Contract for storage purposes.
 *
 * We only store the number part of the contract ID as the ideas ia there will be a virtual merkle tree for each shard
 * and realm.
 */
public final class ContractKey implements VirtualKey<ContractKey> {
	/** The shifts required to deserialize a big-endian contractId with leading zeros omitted */
	private static final int[] BIT_SHIFTS = { 0, 8, 16, 24, 32, 40, 48, 56 };
	/** The estimated average size for a contract key when serialized */
	public static final int ESTIMATED_AVERAGE_SIZE = 20; // assume 50% full typically, max size is (1 + 8 + 32)
	/** The max size for a contract key when serialized */
	public static final int MAX_SIZE = 1 + Long.SIZE + 32;
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

	public ContractKey(long contractId, long key) {
		setContractId(contractId);
		setKey(key);
	}

	public ContractKey(long contractId, byte[] data) {
		this(contractId, asPackedInts(data));
	}

	public ContractKey(long contractId, int[] key) {
		setContractId(contractId);
		setKey(key);
	}

	public static int[] asPackedInts(final byte[] data) {
		if (data == null || data.length != 32) {
			throw new IllegalArgumentException("Key data must be non-null and 32 bytes long");
		}
		return new int[] {
				data[0] << 24 | (data[1] & 255) << 16 | (data[2] & 255) << 8 | (data[3] & 255),
				data[4] << 24 | (data[5] & 255) << 16 | (data[6] & 255) << 8 | (data[7] & 255),
				data[8] << 24 | (data[9] & 255) << 16 | (data[10] & 255) << 8 | (data[11] & 255),
				data[12] << 24 | (data[13] & 255) << 16 | (data[14] & 255) << 8 | (data[15] & 255),
				data[16] << 24 | (data[17] & 255) << 16 | (data[18] & 255) << 8 | (data[19] & 255),
				data[20] << 24 | (data[21] & 255) << 16 | (data[22] & 255) << 8 | (data[23] & 255),
				data[24] << 24 | (data[25] & 255) << 16 | (data[26] & 255) << 8 | (data[27] & 255),
				data[28] << 24 | (data[29] & 255) << 16 | (data[30] & 255) << 8 | (data[31] & 255),
		};
	}

	public long getContractId() {
		return contractId;
	}

	public void setContractId(long contractId) {
		this.contractId = contractId;
		this.contractIdNonZeroBytes = computeNonZeroBytes(contractId);
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
		setKey(new int[] { 0, 0, 0, 0, 0, 0, (int) (key >> Integer.SIZE), (int) key });
	}

	public void setKey(int[] uint256Key) {
		if (uint256Key == null || uint256Key.length != 8) {
			throw new IllegalArgumentException(
					"The key cannot be null and the key's packed int array size must be 8");
		}
		this.uint256Key = uint256Key;
		this.uint256KeyNonZeroBytes = computeNonZeroBytes(uint256Key);
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

	/**
	 * Special hash to make sure we get good distribution.
	 */
	@Override
	public int hashCode() {
		return (int) perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(perm64(
				contractId) ^ uint256Key[7]) ^ uint256Key[6]) ^ uint256Key[5]) ^ uint256Key[4]) ^
				uint256Key[3]) ^ uint256Key[2]) ^ uint256Key[1]) ^ uint256Key[0]);
	}

	@Override
	public String toString() {
		return "ContractKey{id=" + contractId + "(" + Long.toHexString(contractId).toUpperCase() + "), key=" +
				getKeyAsBigInteger() + "(" + Arrays.stream(uint256Key).mapToObj(Integer::toHexString).collect(
				Collectors.joining(",")).toUpperCase()
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
		for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
			out.write(getUint256Byte(b));
		}
		return 1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes;
	}

	@Override
	public void serialize(ByteBuffer byteBuffer) throws IOException {
		byteBuffer.put(getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
		for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
			byteBuffer.put((byte) (contractId >> (b * 8)));
		}
		for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
			byteBuffer.put(getUint256Byte(b));
		}
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int i) throws IOException {
		final byte packedSize = in.readByte();
		this.contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
		this.uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
		this.contractId = deserializeContractID(contractIdNonZeroBytes, in, SerializableDataInputStream::readByte);
		this.uint256Key = deserializeUnit256Key(uint256KeyNonZeroBytes, in, SerializableDataInputStream::readByte);

	}

	@Override
	public void deserialize(ByteBuffer buf, int i) throws IOException {
		final byte packedSize = buf.get();
		this.contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
		this.uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
		this.contractId = deserializeContractID(contractIdNonZeroBytes, buf, ByteBuffer::get);
		this.uint256Key = deserializeUnit256Key(uint256KeyNonZeroBytes, buf, ByteBuffer::get);
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	/**
	 * Read the key size in bytes from a byte buffer containing a serialized ContractKey
	 *
	 * @param buf
	 * 		The buffer to read from, its position will be restored after we read
	 * @return the size in byte for the key contained in buffer.
	 */
	public static int readKeySize(ByteBuffer buf) {
		final byte packedSize = buf.get();
		buf.position(buf.position() - 1); // move position back, like we never read anything
		return 1 + getContractIdNonZeroBytesFromPacked(packedSize) + getUint256KeyNonZeroBytesFromPacked(packedSize);
	}

	// =================================================================================================================
	// Private methods, left package for UnitTests

	/**
	 * Deserialize long contract id from data source
	 *
	 * @param contractIdNonZeroBytes
	 * 		the number of non-zero bytes stored for the long contract id
	 * @param dataSource
	 * 		The data source to read from
	 * @param reader
	 * 		function to read a byte from the data source
	 * @param <D>
	 * 		type for data source, e.g. ByteBuffer or InputStream
	 * @return long contract id read
	 * @throws IOException
	 * 		If there was a problem reading
	 */
	static <D> long deserializeContractID(
			final byte contractIdNonZeroBytes,
			final D dataSource,
			final ByteReaderFunction<D> reader
	) throws IOException {
		long contractId = 0;
		/* Bytes are encountered in order of significance (big-endian) */
		for (int byteI = 0, shiftI = contractIdNonZeroBytes - 1; byteI < contractIdNonZeroBytes; byteI++, shiftI--) {
			contractId |= ((long) reader.read(dataSource) & 255) << BIT_SHIFTS[shiftI];
		}
		return contractId;
	}

	/**
	 * Deserialize uint256 from data source
	 *
	 * @param uint256KeyNonZeroBytes
	 * 		the number of non-zero bytes stored for the uint
	 * @param dataSource
	 * 		The data source to read from
	 * @param reader
	 * 		function to read a byte from the data source
	 * @param <D>
	 * 		type for data source, e.g. ByteBuffer or InputStream
	 * @return unit256 read as an int[8]
	 * @throws IOException
	 * 		If there was a problem reading
	 */
	static <D> int[] deserializeUnit256Key(
			final byte uint256KeyNonZeroBytes,
			final D dataSource,
			final ByteReaderFunction<D> reader
	) throws IOException {
		final int[] uint256 = new int[8];
		for (int i = 7; i >= 0; i--) {
			int integer = 0;
			if (uint256KeyNonZeroBytes >= (4 + (i * Integer.BYTES)))
				integer |= ((long) reader.read(dataSource) & 255) << 24;
			if (uint256KeyNonZeroBytes >= (3 + (i * Integer.BYTES)))
				integer |= ((long) reader.read(dataSource) & 255) << 16;
			if (uint256KeyNonZeroBytes >= (2 + (i * Integer.BYTES)))
				integer |= ((long) reader.read(dataSource) & 255) << 8;
			if (uint256KeyNonZeroBytes >= (1 + (i * Integer.BYTES))) integer |= ((long) reader.read(dataSource) & 255);
			uint256[7 - i] = integer;
		}
		return uint256;
	}

	/**
	 * Get contractIdNonZeroBytes and uint256KeyNonZeroBytes packed into a single byte.
	 *
	 * contractIdNonZeroBytes is in the range of 0-8
	 * uint256KeyNonZeroBytes is in the range of 0-32
	 *
	 * As those can not be packed in their entirety in a single byte we accept a minimum of 1, so we store range 1-9 and
	 * 1-32 packed into a single byte.
	 *
	 * @return packed byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
	 */
	byte getContractIdNonZeroBytesAndUint256KeyNonZeroBytes() {
		final byte contractIdNonZeroBytesMinusOne = contractIdNonZeroBytes == 0 ? (byte) 0 :
				(byte) (contractIdNonZeroBytes - 1);
		final byte uint256KeyNonZeroBytesMinusOne = uint256KeyNonZeroBytes == 0 ? (byte) 0 :
				(byte) (uint256KeyNonZeroBytes - 1);
		return (byte) ((contractIdNonZeroBytesMinusOne << 5) | uint256KeyNonZeroBytesMinusOne & 0xff);
	}

	/**
	 * get contractIdNonZeroBytes from packed byte
	 *
	 * @param packed
	 * 		byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
	 * @return contractIdNonZeroBytes
	 */
	static byte getContractIdNonZeroBytesFromPacked(byte packed) {
		return (byte) ((Byte.toUnsignedInt(packed) >> 5) + 1);
	}

	/**
	 * Get uint256KeyNonZeroBytes from packed byte
	 *
	 * @param packed
	 * 		byte containing contractIdNonZeroBytes and uint256KeyNonZeroBytes
	 * @return uint256KeyNonZeroBytes
	 */
	static byte getUint256KeyNonZeroBytesFromPacked(byte packed) {
		return (byte) ((packed & 0b00011111) + 1);
	}

	/**
	 * get contractIdNonZeroBytes for tests
	 */
	byte getContractIdNonZeroBytes() {
		return contractIdNonZeroBytes;
	}

	/**
	 * get uint256KeyNonZeroBytes for tests
	 */
	byte getUint256KeyNonZeroBytes() {
		return uint256KeyNonZeroBytes;
	}

	/**
	 * Get a single byte out of our Unit256 stored as 8 integers in an int array.
	 *
	 * @param byteIndex
	 * 		The index of the byte we want with 0 being the least significant byte, and 31 being the most
	 * 		significant.
	 * @return the byte at given index
	 */
	byte getUint256Byte(int byteIndex) {
		int intIndex = byteIndex / Integer.BYTES;
		return (byte) (uint256Key[uint256Key.length - 1 - intIndex] >> ((byteIndex - (intIndex * Integer.BYTES)) * 8));
	}

	/**
	 * Compute number of bytes of non-zero data are there from the least significant side of an int.
	 *
	 * @param num
	 * 		the int to count non-zero bits for
	 * @return the number of non-zero bytes. Minimum 1, we always write at least 1 byte even for value 0
	 */
	static byte computeNonZeroBytes(int[] num) {
		int count = 0;
		while (count < 8 && num[count] == 0) count++;
		if (count == num.length) return 1; // it is all zeros
		final int mostSignificantNonZeroInt = num[count];
		final byte bytes = computeNonZeroBytes(mostSignificantNonZeroInt);
		return (byte) (((num.length - count - 1) * Integer.BYTES) + bytes);
	}

	/**
	 * Compute number of bytes of non-zero data are there from the least significant side of an int.
	 *
	 * @param num
	 * 		the int to count non-zero bits for
	 * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for value 0
	 */
	static byte computeNonZeroBytes(int num) {
		if (num == 0) return (byte) 1;
		return (byte) Math.ceil((Integer.SIZE - Integer.numberOfLeadingZeros(num)) / 8D);
	}

	/**
	 * Compute number of bytes of non-zero data are there from the least significant side of a long.
	 *
	 * @param num
	 * 		the long to count non-zero bits for
	 * @return the number of non-zero bytes, Minimum 1, we always write at least 1 byte even for value 0
	 */
	static byte computeNonZeroBytes(long num) {
		if (num == 0) return (byte) 1;
		return (byte) Math.ceil((Long.SIZE - Long.numberOfLeadingZeros(num)) / 8D);
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

	/** Simple interface for a function that takes a object and returns a byte */
	@FunctionalInterface
	interface ByteReaderFunction<T> {

		/**
		 * Applies this function to the given argument.
		 *
		 * @param dataSource
		 * 		the function argument
		 * @return the function result
		 */
		byte read(T dataSource) throws IOException;
	}
}
