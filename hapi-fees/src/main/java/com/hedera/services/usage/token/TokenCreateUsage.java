package com.hedera.services.usage.token;

import com.hedera.services.usage.TxnUsageEstimator;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenCreateUsage extends TxnUsageEstimator<TokenCreateUsage> {
	public TokenCreateUsage() {
		super(ESTIMATOR_UTILS);
	}

	@Override
	protected TokenCreateUsage self() {
		return this;
	}
}
