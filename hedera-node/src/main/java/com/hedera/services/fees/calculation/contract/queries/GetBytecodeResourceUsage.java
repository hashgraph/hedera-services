package com.hedera.services.fees.calculation.contract.queries;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GetBytecodeResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(GetTxnRecordResourceUsage.class);

	private static final byte[] EMPTY_BYTECODE = new byte[0];

	private final SmartContractFeeBuilder usageEstimator;

	public GetBytecodeResourceUsage(SmartContractFeeBuilder usageEstimator) {
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasContractGetBytecode();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageGivenType(query, view, query.getContractGetBytecode().getHeader().getResponseType());
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		try {
			var op = query.getContractGetBytecode();
			var bytecode = view.bytecodeOf(op.getContractID()).orElse(EMPTY_BYTECODE);
			return usageEstimator.getContractByteCodeQueryFeeMatrices(bytecode.length, type);
		} catch (Exception illegal) {
			log.warn("Usage estimation unexpectedly failed for {}!", query);
			throw new IllegalArgumentException(illegal);
		}
	}
}
