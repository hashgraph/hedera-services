package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.TransactionBody;

@FunctionalInterface
public interface EstimatorFactory {
	TxnUsageEstimator get(SigUsage sigUsage, TransactionBody txn, EstimatorUtils utils);
}
