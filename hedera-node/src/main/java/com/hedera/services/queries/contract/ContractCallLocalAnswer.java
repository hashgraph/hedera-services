package com.hedera.services.queries.contract;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AbstractAnswer;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ContractCallLocalAnswer extends AbstractAnswer {
	private static final Logger log = LogManager.getLogger(ContractCallLocalAnswer.class);

	public static final String CONTRACT_CALL_LOCAL_CTX_KEY =
			ContractCallLocalAnswer.class.getSimpleName() + "_localCallResponse";

	public ContractCallLocalAnswer() {
		super(
				ContractCallLocal,
				query -> query.getContractCallLocal().getHeader().getPayment(),
				query -> query.getContractCallLocal().getHeader().getResponseType(),
				response -> response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode(),
				(query, view) -> FAIL_INVALID);
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public Response responseGiven(
			Query query,
			StateView view,
			ResponseCodeEnum validity,
			long cost,
			Map<String, Object> queryCtx
	) {
		throw new AssertionError("Not implemented!");
	}
}
