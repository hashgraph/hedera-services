package com.hedera.services.queries.crypto;

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
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetAccountRecordsAnswer implements AnswerService {
	private final OptionValidator optionValidator;
	private final AnswerFunctions answerFunctions;

	public GetAccountRecordsAnswer(
			AnswerFunctions answerFunctions,
			OptionValidator optionValidator
	) {
		this.answerFunctions = answerFunctions;
		this.optionValidator = optionValidator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getCryptoGetAccountRecords().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getCryptoGetAccountRecords().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		CryptoGetAccountRecordsQuery op = query.getCryptoGetAccountRecords();
		CryptoGetAccountRecordsResponse.Builder response = CryptoGetAccountRecordsResponse.newBuilder();

		ResponseType type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			if (type == COST_ANSWER) {
				response.setAccountID(op.getAccountID());
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				response.setHeader(answerOnlyHeader(OK));
				response.setAccountID(op.getAccountID());
				response.addAllRecords(answerFunctions.accountRecords(view, query));
			}
		}

		return Response.newBuilder()
				.setCryptoGetAccountRecords(response)
				.build();
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		AccountID id = query.getCryptoGetAccountRecords().getAccountID();

		return optionValidator.queryableAccountStatus(id, view.accounts());
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return CryptoGetAccountRecords;
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		Transaction paymentTxn = query.getCryptoGetAccountRecords().getHeader().getPayment();
		return Optional.ofNullable(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}
}
