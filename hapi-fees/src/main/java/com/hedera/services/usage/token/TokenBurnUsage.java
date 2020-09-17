package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenBurnUsage extends TokenUsage<TokenBurnUsage> {
	private TokenBurnUsage(TransactionBody tokenBurnOp, TxnUsageEstimator usageEstimator) {
		super(tokenBurnOp, usageEstimator);
	}

	public static TokenBurnUsage newEstimate(TransactionBody tokenBurnOp, SigUsage sigUsage) {
		return new TokenBurnUsage(tokenBurnOp, estimatorFactory.get(sigUsage, tokenBurnOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenBurnUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenBurn();
		addRefBpt(op.getToken());
		addAmountBpt();
		addTransfersRecordRb(1, 1);
		return usageEstimator.get();
	}
}
