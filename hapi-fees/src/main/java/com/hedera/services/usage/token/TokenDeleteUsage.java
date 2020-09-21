package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenDeleteUsage extends TokenUsage<TokenDeleteUsage> {
	private TokenDeleteUsage(TransactionBody tokenDeletionOp, TxnUsageEstimator usageEstimator) {
		super(tokenDeletionOp, usageEstimator);
	}

	public static TokenDeleteUsage newEstimate(TransactionBody tokenDeletionOp, SigUsage sigUsage) {
		return new TokenDeleteUsage(tokenDeletionOp, estimatorFactory.get(sigUsage, tokenDeletionOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenDeleteUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenDeletion();
		addRefBpt(op.getToken());
		return usageEstimator.get();
	}
}
