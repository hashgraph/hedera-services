package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface EstimatorUtils {
	long baseNetworkRbh();
	FeeData withDefaultPartitioning(FeeComponents usage, long networkRbh, int numPayerKeys);
	FeeComponents.Builder newBaseEstimate(TransactionBody txn, SigUsage sigUsage);
}
