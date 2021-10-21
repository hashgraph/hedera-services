package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import static com.hedera.services.state.merkle.virtual.ContractKey.deserializeContractID;
import static com.hedera.services.state.merkle.virtual.ContractKey.deserializeUnit256Key;
import static com.hedera.services.state.merkle.virtual.ContractKey.getContractIdNonZeroBytesFromPacked;
import static com.hedera.services.state.merkle.virtual.ContractKey.getUint256KeyNonZeroBytesFromPacked;

/**
 * KeySerializer for ContractKeys
 */
public class ContractKeySerializer implements KeySerializer<ContractKey> {
	@Override
	public boolean equals(ByteBuffer buf, int version, ContractKey contractKey) throws IOException {
		byte packedSize = buf.get();
		final byte contractIdNonZeroBytes = getContractIdNonZeroBytesFromPacked(packedSize);
		if (contractIdNonZeroBytes != contractKey.getContractIdNonZeroBytes()) return false;
		final byte uint256KeyNonZeroBytes = getUint256KeyNonZeroBytesFromPacked(packedSize);
		if (uint256KeyNonZeroBytes != contractKey.getUint256KeyNonZeroBytes()) return false;
		final long contractId = deserializeContractID(contractIdNonZeroBytes, buf, ByteBuffer::get);
		if (contractId != contractKey.getContractId()) return false;
		final int[] uint256Key = deserializeUnit256Key(uint256KeyNonZeroBytes, buf, ByteBuffer::get);
		return Arrays.equals(uint256Key, contractKey.getKey());
	}

	/**
	 * Get if the number of bytes a data item takes when serialized is variable or fixed
	 *
	 * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
	 */
	@Override
	public boolean isVariableSize() {
		return true;
	}

	/**
	 * Get the number of bytes a data item takes when serialized
	 *
	 * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
	 */
	@Override
	public int getSerializedSize() {
		return DataFileCommon.VARIABLE_DATA_SIZE;
	}

	/**
	 * For variable sized data get the typical  number of bytes a data item takes when serialized
	 *
	 * @return Either for fixed size same as getSerializedSize() or an estimated typical size for data items
	 */
	@Override
	public int getTypicalSerializedSize() {
		return ContractKey.ESTIMATED_AVERAGE_SIZE;
	}

	/**
	 * Get the current data item serialization version
	 */
	@Override
	public long getCurrentDataVersion() {
		return 1;
	}

	/**
	 * Deserialize a data item from a byte buffer, that was written with given data version
	 *
	 * @param buffer
	 * 		The buffer to read from containing the data item including its header
	 * @param dataVersion
	 * 		The serialization version the data item was written with
	 * @return Deserialized data item
	 */
	@Override
	public ContractKey deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
		Objects.requireNonNull(buffer);
		ContractKey contractKey = new ContractKey();
		contractKey.deserialize(buffer, (int) dataVersion);
		return contractKey;
	}

	/**
	 * Serialize a data item including header to the output stream returning the size of the data written
	 *
	 * @param data
	 * 		The data item to serialize
	 * @param outputStream
	 * 		Output stream to write to
	 */
	@Override
	public int serialize(ContractKey data, SerializableDataOutputStream outputStream) throws IOException {
		Objects.requireNonNull(data);
		Objects.requireNonNull(outputStream);
		return data.serializeReturningByteWritten(outputStream);
	}

	/**
	 * Deserialize key size from the given byte buffer
	 *
	 * @param buffer
	 * 		Buffer to read from
	 * @return The number of bytes used to store the key, including for storing the key size if needed.
	 */
	@Override
	public int deserializeKeySize(ByteBuffer buffer) {
		return ContractKey.readKeySize(buffer);
	}
}
