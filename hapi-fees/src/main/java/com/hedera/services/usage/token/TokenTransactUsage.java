package com.hedera.services.usage.token;

import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TokenTransfer;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

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
		long xferBytes = 0;
		for (TokenTransfer transfer : op.getTransfersList()) {
			xferBytes += refBpt(transfer.getToken());
		}
		xferBytes += op.getTransfersCount() * (BASIC_ENTITY_ID_SIZE + AMOUNT_REPR_BYTES);
		usageEstimator.addBpt(xferBytes);
		
//		addTransfersRecordRb();

		return usageEstimator.get();
	}
}
