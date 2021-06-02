package com.hedera.services.usage.crypto;

public class BaseTransactionMeta {
	private final int memoUtf8Bytes;
	private final int numExplicitTransfers;

	public BaseTransactionMeta(int memoUtf8Bytes, int numExplicitTransfers) {
		this.memoUtf8Bytes = memoUtf8Bytes;
		this.numExplicitTransfers = numExplicitTransfers;
	}

	public int getMemoUtf8Bytes() {
		return memoUtf8Bytes;
	}

	public int getNumExplicitTransfers() {
		return numExplicitTransfers;
	}
}
