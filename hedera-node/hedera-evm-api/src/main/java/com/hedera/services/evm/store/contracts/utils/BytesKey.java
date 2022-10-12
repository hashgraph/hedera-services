package com.hedera.services.evm.store.contracts.utils;

import com.swirlds.common.utility.CommonUtils;

import java.util.Arrays;

public record BytesKey(byte[] array) {
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		BytesKey bytesKey = (BytesKey) o;
		return Arrays.equals(array, bytesKey.array);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(array);
	}

	@Override
	public String toString() {
		return "BytesKey[array=" + CommonUtils.hex(array) + "]";
	}
}
