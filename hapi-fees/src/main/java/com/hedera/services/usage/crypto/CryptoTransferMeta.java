package com.hedera.services.usage.crypto;

public class CryptoTransferMeta {
	private final int tokenMultiplier;
	private final int numTokensInvolved;
	private final int numTokensTransfers;

	public CryptoTransferMeta(int tokenMultiplier, int numTokensInvolved, int numTokensTransfers) {
		this.tokenMultiplier = tokenMultiplier;
		this.numTokensInvolved = numTokensInvolved;
		this.numTokensTransfers = numTokensTransfers;
	}

	public int getTokenMultiplier() {
		return tokenMultiplier;
	}

	public int getNumTokensInvolved() {
		return numTokensInvolved;
	}

	public int getNumTokensTransfers() {
		return numTokensTransfers;
	}
}
