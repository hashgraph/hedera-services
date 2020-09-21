package com.hedera.services.usage;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hederahashgraph.fee.FeeBuilder.BASIC_TX_BODY_SIZE;

public interface EstimatorUtils {
	default int baseBodyBytes(TransactionBody txn) {
		return BASIC_TX_BODY_SIZE + txn.getMemoBytes().size();
	}

	default long nonDegenerateDiv(long dividend, int divisor) {
		return (dividend == 0) ? 0 : Math.max(1, dividend / divisor);
	}

	default long relativeLifetime(TransactionBody txn, long expiry) {
		long effectiveNow = txn.getTransactionID().getTransactionValidStart().getSeconds();
		return expiry - effectiveNow;
	}

	long baseNetworkRbs();
	FeeData withDefaultTxnPartitioning(FeeComponents usage, long networkRbh, int numPayerKeys);
	FeeData withDefaultQueryPartitioning(FeeComponents usage);
	UsageEstimate baseEstimate(TransactionBody txn, SigUsage sigUsage);
}
