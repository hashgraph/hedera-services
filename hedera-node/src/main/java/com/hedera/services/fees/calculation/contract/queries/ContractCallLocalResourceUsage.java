package com.hedera.services.fees.calculation.contract.queries;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.queries.contract.ContractCallLocalAnswer;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.queries.AnswerService.NO_QUERY_CTX;
import static com.hedera.services.queries.contract.ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;

public class ContractCallLocalResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(ContractCallLocalResourceUsage.class);

	private final ContractCallLocalAnswer.LegacyLocalCaller delegate;
	private final SmartContractFeeBuilder usageEstimator;
	private final GlobalDynamicProperties properties;

	public ContractCallLocalResourceUsage(
			ContractCallLocalAnswer.LegacyLocalCaller delegate,
			SmartContractFeeBuilder usageEstimator,
			GlobalDynamicProperties properties
	) {
		this.delegate = delegate;
		this.properties = properties;
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(Query query) {
		return query.hasContractCallLocal();
	}

	@Override
	public FeeData usageGiven(Query query, StateView view) {
		return usageFor(query, view, query.getContractCallLocal().getHeader().getResponseType(), NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGivenType(Query query, StateView view, ResponseType type) {
		return usageFor(query, view, type, NO_QUERY_CTX);
	}

	@Override
	public FeeData usageGiven(Query query, StateView view, Map<String, Object> queryCtx) {
		return usageFor(
				query,
				view,
				query.getContractCallLocal().getHeader().getResponseType(),
				Optional.of(queryCtx));
	}

	private FeeData usageFor(Query query, StateView view, ResponseType type, Optional<Map<String, Object>> queryCtx) {
		try {
			var op = query.getContractCallLocal();
			ContractCallLocalResponse response;
			if (queryCtx.isEmpty()) {
				response = dummyResponse(op.getContractID());
			} else {
				response = delegate.perform(op, Instant.now().getEpochSecond());
				queryCtx.get().put(CONTRACT_CALL_LOCAL_CTX_KEY, response);
			}
			var nonGasUsage = usageEstimator.getContractCallLocalFeeMatrices(
					op.getFunctionParameters().size(),
					response.getFunctionResult(),
					type);
			var ans = nonGasUsage.toBuilder()
					.setNodedata(nonGasUsage.getNodedata().toBuilder().setGas(op.getGas()))
					.build();
			return ans;
		} catch (Exception e) {
			log.warn("Usage estimation unexpectedly failed for {}!", query, e);
			throw new IllegalArgumentException(e);
		}
	}

	ContractCallLocalResponse dummyResponse(ContractID target) {
		return ContractCallLocalResponse.newBuilder()
				.setFunctionResult(ContractFunctionResult.newBuilder()
								.setContractCallResult(ByteString.copyFrom(new byte[properties.localCallEstRetBytes()]))
								.setContractID(target))
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(OK))
				.build();
	}
}
