package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenRevokeKycUsage extends TokenUsage<TokenRevokeKycUsage> {
	private TokenRevokeKycUsage(TransactionBody tokenRevokeKycOp, TxnUsageEstimator usageEstimator) {
		super(tokenRevokeKycOp, usageEstimator);
	}

	public static TokenRevokeKycUsage newEstimate(TransactionBody tokenRevokeKycOp, SigUsage sigUsage) {
		return new TokenRevokeKycUsage(tokenRevokeKycOp, estimatorFactory.get(sigUsage, tokenRevokeKycOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenRevokeKycUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenRevokeKyc();
		addRefBpt(op.getToken());
		addAccountBpt();
		return usageEstimator.get();
	}
}
