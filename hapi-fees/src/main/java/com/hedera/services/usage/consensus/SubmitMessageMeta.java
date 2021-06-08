package com.hedera.services.usage.consensus;

public class SubmitMessageMeta {
	private final int numMsgBytes;

	public SubmitMessageMeta(int numMsgBytes) {
		this.numMsgBytes = numMsgBytes;
	}

	public int getNumMsgBytes() {
		return numMsgBytes;
	}
}
