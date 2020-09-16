package com.hedera.services.usage;

import java.util.Objects;

public class SigUsage {
	private final int numSigs;
	private final int sigsSize;
	private final int numPayerKeys;

	public SigUsage(int numSigs, int sigsSize, int numPayerKeys) {
		this.numSigs = numSigs;
		this.sigsSize = sigsSize;
		this.numPayerKeys = numPayerKeys;
	}

	public int numSigs() {
		return numSigs;
	}

	public int sigsSize() {
		return sigsSize;
	}

	public int numPayerKeys() {
		return numPayerKeys;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || o.getClass() != SigUsage.class) {
			return false;
		}
		SigUsage that = (SigUsage) o;
		return this.numSigs == that.numSigs && this.sigsSize == that.sigsSize && this.numPayerKeys == that.numPayerKeys;
	}

	@Override
	public int hashCode() {
		return Objects.hash(numSigs, sigsSize, numPayerKeys);
	}

	@Override
	public String toString() {
		return String.format("SigUsage{numSigs=%d, sigsSize=%d, numPayerKeys=%d}", numSigs, sigsSize, numPayerKeys);
	}
}
