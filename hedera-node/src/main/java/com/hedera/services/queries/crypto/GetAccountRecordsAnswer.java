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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.queries.meta.GetTxnRecordAnswer.PAYER_RECORDS_CTX_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

@Singleton
public class GetAccountRecordsAnswer implements AnswerService {
	private final OptionValidator optionValidator;
	private final AnswerFunctions answerFunctions;
	private final AliasManager aliasManager;

	@Inject
	public GetAccountRecordsAnswer(
			final AnswerFunctions answerFunctions,
			final OptionValidator optionValidator,
			final AliasManager aliasManager
	) {
		this.answerFunctions = answerFunctions;
		this.optionValidator = optionValidator;
		this.aliasManager = aliasManager;
	}

	@Override
	public boolean needsAnswerOnlyCost(final Query query) {
		return COST_ANSWER == query.getCryptoGetAccountRecords().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(final Query query) {
		return typicallyRequiresNodePayment(query.getCryptoGetAccountRecords().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost
	) {
		return responseFor(query, view, validity, cost, null);
	}

	@Override
	public Response responseGiven(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost,
			final Map<String, Object> queryCtx
	) {
		return responseFor(query, view, validity, cost, queryCtx);
	}

	private Response responseFor(
			final Query query,
			final StateView view,
			final ResponseCodeEnum validity,
			final long cost,
			final @Nullable Map<String, Object> queryCtx
	) {
		final var op = query.getCryptoGetAccountRecords();
		final var response = CryptoGetAccountRecordsResponse.newBuilder();

		final var type = op.getHeader().getResponseType();
		if (validity != OK) {
			response.setHeader(header(validity, type, cost));
		} else {
			final var id = aliasManager.lookUpAccount(op.getAccountID()).resolvedId();

			if (type == COST_ANSWER) {
				response.setAccountID(id);
				response.setHeader(costAnswerHeader(OK, cost));
			} else {
				setAnswerOnly(response, view, queryCtx, id);
			}
		}

		return Response.newBuilder()
				.setCryptoGetAccountRecords(response)
				.build();
	}

	@SuppressWarnings("unchecked")
	private void setAnswerOnly(
			final CryptoGetAccountRecordsResponse.Builder response,
			final StateView view,
			final @Nullable Map<String, Object> queryCtx,
			final AccountID id) {
		response.setHeader(answerOnlyHeader(OK));
		response.setAccountID(id);
		if (queryCtx != null && queryCtx.containsKey(PAYER_RECORDS_CTX_KEY)) {
			response.addAllRecords((List<TransactionRecord>) queryCtx.get(PAYER_RECORDS_CTX_KEY));
		} else {
			response.addAllRecords(answerFunctions.mostRecentRecords(view, id));
		}
	}

	@Override
	public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
		final var result = aliasManager.lookUpAccount(query.getCryptoGetAccountRecords().getAccountID());
		if (result.response() != OK) {
			return result.response();
		}
		final var id = result.resolvedId();

		return optionValidator.queryableAccountStatus(id, view.accounts());
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(final Response response) {
		return response.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return CryptoGetAccountRecords;
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
		final var paymentTxn = query.getCryptoGetAccountRecords().getHeader().getPayment();
		return Optional.of(SignedTxnAccessor.uncheckedFrom(paymentTxn));
	}
}
