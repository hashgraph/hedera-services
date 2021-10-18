package com.hedera.services.state.merkle;

import com.hedera.services.state.merkle.internals.ContractStorageKey;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.IOException;

import static com.hedera.services.state.merkle.internals.ContractStorageKey.BYTES_PER_UINT256;

public class MerkleContractStorageValue extends AbstractMerkleLeaf implements Keyed<ContractStorageKey> {
	private static final int CURRENT_VERSION = 1;
	private static final long CLASS_ID = 0xf0b1f0a667ca207cL;

	private byte[] key;
	private byte[] value;
	private long contractNum;

	public MerkleContractStorageValue() {
		/* RuntimeConstructable */
	}

	public MerkleContractStorageValue(final byte[] value) {
		this.value = value;
	}

	public MerkleContractStorageValue(MerkleContractStorageValue that) {
		this.key = that.key;
		this.value = that.value;
		this.contractNum = that.contractNum;
	}

	public void setValue(byte[] value) {
		throwIfImmutable("Cannot set an immutable value to " + CommonUtils.hex(value));
		this.value = value;
	}

	@Override
	public MerkleContractStorageValue copy() {
		setImmutable(true);
		return new MerkleContractStorageValue(this);
	}

	public byte[] getValue() {
		return value;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		contractNum = in.readLong();
		key = in.readByteArray(BYTES_PER_UINT256);
		value = in.readByteArray(BYTES_PER_UINT256);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(contractNum);
		out.writeByteArray(key);
		out.writeByteArray(value);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public ContractStorageKey getKey() {
		return new ContractStorageKey(contractNum, key);
	}

	@Override
	public void setKey(ContractStorageKey contractStorageKey) {
		this.contractNum = contractStorageKey.getContractNum();
		this.key = contractStorageKey.getKey();
	}

	@Override
	public String toString() {
		return "MerkleContractStorageValue{" +
				"key=" + CommonUtils.hex(key) +
				", value=" + CommonUtils.hex(value) +
				", contractNum=" + contractNum +
				'}';
	}
}
