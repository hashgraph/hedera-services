package com.hedera.services.queries.contract;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AbstractAnswer;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class ContractCallLocalAnswer extends AbstractAnswer {
	private static final Logger log = LogManager.getLogger(ContractCallLocalAnswer.class);

	public static final String CONTRACT_CALL_LOCAL_CTX_KEY =
			ContractCallLocalAnswer.class.getSimpleName() + "_localCallResponse";

	@FunctionalInterface
	public interface LegacyLocalCaller {
		ContractCallLocalResponse perform(ContractCallLocalQuery query, long now) throws Exception;
	}

	private final LegacyLocalCaller delegate;

	public ContractCallLocalAnswer(LegacyLocalCaller delegate, OptionValidator validator) {
		super(
				ContractCallLocal,
				query -> query.getContractCallLocal().getHeader().getPayment(),
				query -> query.getContractCallLocal().getHeader().getResponseType(),
				response -> response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode(),
				(query, view) -> {
					var op = query.getContractCallLocal();
					if (op.getGas() < 0) {
						return CONTRACT_NEGATIVE_GAS;
					} else {
						return validator.queryableContractStatus(op.getContractID(), view.contracts());
					}
				});
		this.delegate = delegate;
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		return responseFor(query, view, validity, cost, NO_QUERY_CTX);
	}

	@Override
	public Response responseGiven(
			Query query,
			StateView view,
			ResponseCodeEnum validity,
			long cost,
			Map<String, Object> queryCtx
	) {
		return responseFor(query, view, validity, cost, Optional.of(queryCtx));
	}

	private Response responseFor(
			Query query,
			StateView view,
			ResponseCodeEnum validity,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		var op = query.getContractCallLocal();
		var response = ContractCallLocalResponse.newBuilder();

		var type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				setAnswerOnly(response, view, op, cost, queryCtx);
			}
		}

		return Response.newBuilder()
				.setContractCallLocal(response)
				.build();
	}

	@SuppressWarnings("unchecked")
	private void setAnswerOnly(
			ContractCallLocalResponse.Builder response,
			StateView view,
			ContractCallLocalQuery op,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		if (queryCtx.isPresent()) {
			var ctx = queryCtx.get();
			if (!ctx.containsKey(CONTRACT_CALL_LOCAL_CTX_KEY)) {
				throw new IllegalStateException("Query context had no cached local call result!");
			} else {
				response.mergeFrom(
						withCid((ContractCallLocalResponse)ctx.get(CONTRACT_CALL_LOCAL_CTX_KEY), op.getContractID()));
			}
		} else {
			/* If answering from a zero-stake node, there are no node payments, and the
			usage estimator won't have cached the result it got from the local call. */
			try {
				var delegateResponse = delegate.perform(op, Instant.now().getEpochSecond());
				response.mergeFrom(withCid(delegateResponse, op.getContractID()));
			} catch (Exception e) {
				response.setHeader(answerOnlyHeader(FAIL_INVALID, cost));
			}
		}
	}

	private ContractCallLocalResponse withCid(ContractCallLocalResponse response, ContractID target) {
		return response.toBuilder()
						.setFunctionResult(response.getFunctionResult().toBuilder()
								.setContractID(target))
						.build();
	}
}
