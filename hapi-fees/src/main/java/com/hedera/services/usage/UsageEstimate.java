package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.FeeComponents;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.HRS_DIVISOR;

public class UsageEstimate {
	static EstimatorUtils estimatorUtils = ESTIMATOR_UTILS;

	private long rbs, sbs;
	private final FeeComponents.Builder base;

	public UsageEstimate(FeeComponents.Builder base) {
		this.base = base;
	}

	public void addRbs(long amount) {
		rbs += amount;
	}

	public void addSbs(long amount) {
		sbs += amount;
	}

	public FeeComponents.Builder base() {
		return base;
	}

	public FeeComponents build() {
		return base
				.setSbh(estimatorUtils.nonDegenerateDiv(sbs, HRS_DIVISOR))
				.setRbh(estimatorUtils.nonDegenerateDiv(rbs, HRS_DIVISOR))
				.build();
	}
}
