package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenUnfreezeUsage extends TokenUsage<TokenUnfreezeUsage> {
	private TokenUnfreezeUsage(TransactionBody tokenUnfreezeOp, TxnUsageEstimator usageEstimator) {
		super(tokenUnfreezeOp, usageEstimator);
	}

	public static TokenUnfreezeUsage newEstimate(TransactionBody tokenUnfreezeOp, SigUsage sigUsage) {
		return new TokenUnfreezeUsage(tokenUnfreezeOp, estimatorFactory.get(sigUsage, tokenUnfreezeOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenUnfreezeUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenUnfreeze();
		addRefBpt(op.getToken());
		addAccountBpt();
		return usageEstimator.get();
	}
}
