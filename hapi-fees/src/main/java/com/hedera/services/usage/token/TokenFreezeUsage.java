package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenFreezeUsage extends TokenUsage<TokenFreezeUsage> {
	private TokenFreezeUsage(TransactionBody tokenFreezeOp, TxnUsageEstimator usageEstimator) {
		super(tokenFreezeOp, usageEstimator);
	}

	public static TokenFreezeUsage newEstimate(TransactionBody tokenFreezeOp, SigUsage sigUsage) {
		return new TokenFreezeUsage(tokenFreezeOp, estimatorFactory.get(sigUsage, tokenFreezeOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenFreezeUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenFreeze();
		addRefBpt(op.getToken());
		addAccountBpt();
		return usageEstimator.get();
	}
}
