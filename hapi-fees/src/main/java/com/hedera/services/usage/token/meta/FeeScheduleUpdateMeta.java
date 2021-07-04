package com.hedera.services.usage.token.meta;

public class FeeScheduleUpdateMeta {
	private final long effConsensusTime;
	private final int numBytesInNewFeeScheduleRepr;
	private final int numBytesInGrpcFeeScheduleRepr;

	public FeeScheduleUpdateMeta(
			long effConsensusTime,
			int numBytesInNewFeeScheduleRepr,
			int numBytesInGrpcFeeScheduleRepr
	) {
		this.effConsensusTime = effConsensusTime;
		this.numBytesInNewFeeScheduleRepr = numBytesInNewFeeScheduleRepr;
		this.numBytesInGrpcFeeScheduleRepr = numBytesInGrpcFeeScheduleRepr;
	}

	public long effConsensusTime() {
		return effConsensusTime;
	}

	public int numBytesInNewFeeScheduleRepr() {
		return numBytesInNewFeeScheduleRepr;
	}

	public int numBytesInGrpcFeeScheduleRepr() {
		return numBytesInGrpcFeeScheduleRepr;
	}
}
