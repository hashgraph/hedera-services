package com.hedera.services.state.merkle.internals;

import com.swirlds.common.CommonUtils;

import java.util.Arrays;
import java.util.Objects;

public class ContractStorageKey {
	public static final int BYTES_PER_UINT256 = 32;

	private final long contractNum;
	private final byte[] key;

	public ContractStorageKey(final long contractNum, final byte[] key) {
		this.contractNum = contractNum;
		this.key = key;
	}

	public long getContractNum() {
		return contractNum;
	}

	public byte[] getKey() {
		return key;
	}

	@Override
	public String toString() {
		return "ContractStorageKey{" +
				"contractNum=" + contractNum +
				", key=" + CommonUtils.hex(key) +
				'}';
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ContractStorageKey that = (ContractStorageKey) o;
		return contractNum == that.contractNum && Arrays.equals(key, that.key);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(contractNum);
		result = 31 * result + Arrays.hashCode(key);
		return result;
	}
}
