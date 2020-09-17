package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenMintUsage extends TokenUsage<TokenMintUsage> {
	private TokenMintUsage(TransactionBody tokenMintOp, TxnUsageEstimator usageEstimator) {
		super(tokenMintOp, usageEstimator);
	}

	public static TokenMintUsage newEstimate(TransactionBody tokenMintOp, SigUsage sigUsage) {
		return new TokenMintUsage(tokenMintOp, estimatorFactory.get(sigUsage, tokenMintOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenMintUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenMint();
		addRefBpt(op.getToken());
		addAmountBpt();
		addTransfersRecordRb(1, 1);
		return usageEstimator.get();
	}
}
