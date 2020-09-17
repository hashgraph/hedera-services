package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

public class TokenTransactUsage extends TokenUsage<TokenTransactUsage> {
	private TokenTransactUsage(TransactionBody tokenTransactOp, TxnUsageEstimator usageEstimator) {
		super(tokenTransactOp, usageEstimator);
	}

	public static TokenTransactUsage newEstimate(TransactionBody tokenTransactOp, SigUsage sigUsage) {
		return new TokenTransactUsage(tokenTransactOp, estimatorFactory.get(sigUsage, tokenTransactOp, ESTIMATOR_UTILS));
	}

	@Override
	TokenTransactUsage self() {
		return this;
	}

	public FeeData get() {
		var op = tokenOp.getTokenTransfers();

		int xfers = 0;
		long xferBytes = 0;
		for (TokenRefTransferList transfer : op.getTokenTransfersList()) {
			xferBytes += TokenUsageUtils.refBpt(transfer.getToken());
			xfers += transfer.getTransfersCount();
		}
		xferBytes += xfers * usageProperties.accountAmountBytes();
		usageEstimator.addBpt(xferBytes);
		addTransfersRecordRb(op.getTokenTransfersCount(), xfers);

		return usageEstimator.get();
	}
}
