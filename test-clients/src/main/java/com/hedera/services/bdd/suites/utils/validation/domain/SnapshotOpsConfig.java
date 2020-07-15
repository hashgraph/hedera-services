package com.hedera.services.bdd.suites.utils.validation.domain;

public class SnapshotOpsConfig {
	final int DEFAULT_MEMO_LENGTH = 32;

	Long bytecode;
	Integer memoLength = DEFAULT_MEMO_LENGTH;

	public Integer getMemoLength() {
		return memoLength;
	}

	public void setMemoLength(Integer memoLength) {
		this.memoLength = memoLength;
	}

	public Long getBytecode() {
		return bytecode;
	}

	public void setBytecode(Long bytecode) {
		this.bytecode = bytecode;
	}
}
