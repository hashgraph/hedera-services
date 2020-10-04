package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;

public abstract class QueryUsage {
	private long rb = 0;
	private long tb = BASIC_QUERY_HEADER;

	public FeeData get() {
		var usage = FeeComponents.newBuilder()
				.setBpt(tb)
				.setBpr(rb)
				.build();
		return ESTIMATOR_UTILS.withDefaultQueryPartitioning(usage);
	}

	protected void updateRb(long amount) {
		rb += amount;
	}

	protected void updateTb(long amount) {
		tb += amount;
	}
}
