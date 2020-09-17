package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenGrantKycUsage extends TokenUsage<TokenGrantKycUsage> {
	private TokenGrantKycUsage(TransactionBody tokenGrantKycOp, TxnUsageEstimator usageEstimator) {
		super(tokenGrantKycOp, usageEstimator);
	}

	public static TokenGrantKycUsage newEstimate(TransactionBody tokenGrantKycOp, SigUsage sigUsage) {
		return new TokenGrantKycUsage(tokenGrantKycOp, estimatorFactory.get(sigUsage, tokenGrantKycOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenGrantKycUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenGrantKyc();
		addRefBpt(op.getToken());
		addAccountBpt();
		return usageEstimator.get();
	}
}
