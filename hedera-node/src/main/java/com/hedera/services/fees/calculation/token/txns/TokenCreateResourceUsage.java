package com.hedera.services.fees.calculation.token.txns;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

public class TokenCreateResourceUsage implements TxnResourceUsageEstimator {
	public static final FeeData MOCK_TOKEN_CREATE_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(3)
					.setBpt(3)
					.setVpt(3)
					.setRbh(3)
					.setSbh(3)
					.setGas(3)
					.setTv(3)
					.setBpr(3)
					.setSbpr(3)
					.build(), 3);

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasTokenCreation();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws InvalidTxBodyException {
		return MOCK_TOKEN_CREATE_USAGE;
	}
}
