package com.hedera.services.queries.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractGetInfo;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordResponse;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetContractInfoAnswer implements AnswerService {
	public static final String CONTRACT_INFO_CTX_KEY = GetContractInfoAnswer.class.getSimpleName() + "_contractInfo";

	private final OptionValidator validator;

	public GetContractInfoAnswer(OptionValidator validator) {
		this.validator = validator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getContractGetInfo().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getContractGetInfo().getHeader().getResponseType());
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

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		var id = query.getContractGetInfo().getContractID();

		return validator.queryableContractStatus(id, view.contracts());
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return ContractGetInfo;
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		var paymentTxn = query.getContractGetInfo().getHeader().getPayment();
		return Optional.ofNullable(uncheckedFrom(paymentTxn));
	}

	private Response responseFor(
			Query query,
			StateView view,
			ResponseCodeEnum validity,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		var op = query.getContractGetInfo();
		var response = ContractGetInfoResponse.newBuilder();

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
				.setContractGetInfo(response)
				.build();
	}

	@SuppressWarnings("unchecked")
	private void setAnswerOnly(
			ContractGetInfoResponse.Builder response,
			StateView view,
			ContractGetInfoQuery op,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		if (queryCtx.isPresent()) {
			var ctx = queryCtx.get();
			if (!ctx.containsKey(CONTRACT_INFO_CTX_KEY)) {
				response.setHeader(answerOnlyHeader(INVALID_CONTRACT_ID));
			} else {
				response.setHeader(answerOnlyHeader(OK, cost));
				response.setContractInfo((ContractGetInfoResponse.ContractInfo)ctx.get(CONTRACT_INFO_CTX_KEY));
			}
		} else {
			var info = view.infoForContract(op.getContractID());
			if (info.isEmpty()) {
				response.setHeader(answerOnlyHeader(INVALID_CONTRACT_ID));
			} else {
				response.setHeader(answerOnlyHeader(OK, cost));
				response.setContractInfo(info.get());
			}
		}
	}
}
