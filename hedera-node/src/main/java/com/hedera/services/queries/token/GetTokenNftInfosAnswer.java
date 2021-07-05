package com.hedera.services.queries.token;

/*
 * -
 *
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosResponse;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetTokenNftInfosAnswer implements AnswerService {

	public static final String TOKEN_NFT_INFOS_CTX_KEY = GetTokenNftInfosAnswer.class.getSimpleName() + "_tokenNftInfos";

	private OptionValidator validator;

	public GetTokenNftInfosAnswer(OptionValidator validator) {
		this.validator = validator;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return COST_ANSWER == query.getTokenGetNftInfos().getHeader().getResponseType();
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return typicallyRequiresNodePayment(query.getTokenGetNftInfos().getHeader().getResponseType());
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		return responseFor(query, view, validity, cost, NO_QUERY_CTX);
	}

	@Override
	public Response responseGiven(
			Query query, StateView view, ResponseCodeEnum validity, long cost, Map<String, Object> queryCtx) {
		return responseFor(query, view, validity, cost, Optional.of(queryCtx));
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		var tokenNftInfosQuery = query.getTokenGetNftInfos();
		var id = tokenNftInfosQuery.getTokenID();

		if (tokenNftInfosQuery.getStart() >= tokenNftInfosQuery.getEnd()) {
			return INVALID_QUERY_RANGE;
		}

		var validity = validator.nftMaxQueryRangeCheck(
				tokenNftInfosQuery.getStart(), tokenNftInfosQuery.getEnd());
		if (validity != OK) {
			return validity;
		}

		var optionalToken = view.tokenWith(id);

		if (!optionalToken.isPresent()) {
			return INVALID_TOKEN_ID;
		}

		if (optionalToken.get().isDeleted()) {
			return TOKEN_WAS_DELETED;
		}

		if (!optionalToken.get().tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
			return NOT_SUPPORTED;
		}

		var nftsCount = optionalToken.get().totalSupply();

		if (!(tokenNftInfosQuery.getStart() >= 0 && tokenNftInfosQuery.getEnd() <= nftsCount)) {
			return INVALID_QUERY_RANGE;
		}

		return OK;
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return HederaFunctionality.TokenGetNftInfos;
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		var paymentTxn = query.getTokenGetNftInfos().getHeader().getPayment();
		return Optional.ofNullable(uncheckedFrom(paymentTxn));
	}

	private Response responseFor(
			Query query,
			StateView view,
			ResponseCodeEnum validity,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		var op = query.getTokenGetNftInfos();
		var response = TokenGetNftInfosResponse.newBuilder();

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
				.setTokenGetNftInfos(response)
				.build();
	}

	private void setAnswerOnly(
			TokenGetNftInfosResponse.Builder response,
			StateView view,
			TokenGetNftInfosQuery op,
			long cost,
			Optional<Map<String, Object>> queryCtx
	) {
		if (queryCtx.isPresent()) {
			var ctx = queryCtx.get();
			if (!ctx.containsKey(TOKEN_NFT_INFOS_CTX_KEY)) {
				response.setHeader(answerOnlyHeader(INVALID_NFT_ID));
			} else {
				response.setHeader(answerOnlyHeader(OK, cost));
				response.addAllNfts((List<TokenNftInfo>) ctx.get(TOKEN_NFT_INFOS_CTX_KEY));
			}
		} else {
			var info = view.infoForTokenNfts(op.getTokenID(), op.getStart(), op.getEnd());
			if (info.isEmpty()) {
				response.setHeader(answerOnlyHeader(INVALID_NFT_ID));
			} else {
				response.setHeader(answerOnlyHeader(OK, cost));
				response.setTokenID(op.getTokenID());
				response.addAllNfts(info.get());
			}
		}
	}
}
