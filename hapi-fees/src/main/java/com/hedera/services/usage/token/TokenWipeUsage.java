package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenWipeUsage extends TokenUsage<TokenWipeUsage> {
	private TokenWipeUsage(TransactionBody tokenWipeOp, TxnUsageEstimator usageEstimator) {
		super(tokenWipeOp, usageEstimator);
	}

	public static TokenWipeUsage newEstimate(TransactionBody tokenWipeOp, SigUsage sigUsage) {
		return new TokenWipeUsage(tokenWipeOp, estimatorFactory.get(sigUsage, tokenWipeOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenWipeUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenWipe();
		addRefBpt(op.getToken());
		addAmountBpt();
		addAccountBpt();
		addTransfersRecordRb(1, 1);
		return usageEstimator.get();
	}
}
