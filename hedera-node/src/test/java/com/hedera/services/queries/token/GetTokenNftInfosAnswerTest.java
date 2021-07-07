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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfosResponse;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GetTokenNftInfosAnswerTest {

	private GetTokenNftInfosAnswer subject;
	private Transaction paymentTxn;
	private StateView view;
	private long fee = 1_234L;
	private TokenID tokenId = asToken("0.0.2");
	private TokenID invalidTokenId = asToken("0.0.4");
	private List<TokenNftInfo> tokenNftInfos;
	private long start = 0, end = 2;
	private OptionValidator optionValidator;

	@BeforeEach
	void setup() {
		view = mock(StateView.class);
		optionValidator = mock(OptionValidator.class);
		subject = new GetTokenNftInfosAnswer(optionValidator);

		tokenNftInfos = new ArrayList<>(List.of(
				TokenNftInfo.newBuilder()
						.setMetadata(ByteString.copyFromUtf8("stuff1"))
						.build(),
				TokenNftInfo.newBuilder()
						.setMetadata(ByteString.copyFromUtf8("stuff2"))
						.build(),
				TokenNftInfo.newBuilder()
						.setMetadata(ByteString.copyFromUtf8("stuff3"))
						.build()
		));
	}

	@Test
	void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, tokenId, start, end)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, tokenId, start, end)));
	}

	@Test
	void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, tokenId, start, end)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, tokenId, start, end)));
	}

	@Test
	void checksValidityProperly() throws Throwable {
		// given:
		var merkleToken = new MerkleToken();
		merkleToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
		merkleToken.setTotalSupply(2);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(view.tokenWith(tokenId)).willReturn(Optional.of(merkleToken));

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(OK, validity);
	}

	@Test
	void checksExceededQueryRangeProperly() throws Throwable {
		// given:
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(INVALID_QUERY_RANGE);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void checksQueryRangeLargerThanNftCount() throws Throwable {
		// given:
		var merkleToken = new MerkleToken();
		merkleToken.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(view.tokenWith(tokenId)).willReturn(Optional.of(merkleToken));

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void checksQueryTokenInvalid() throws Throwable {
		// given:
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(view.tokenWith(tokenId)).willReturn(Optional.empty());

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(INVALID_TOKEN_ID, validity);
	}

	@Test
	void checksQueryTokenWasDeleted() throws Throwable {
		// given:
		var merkleToken = new MerkleToken();
		merkleToken.setDeleted(true);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(view.tokenWith(tokenId)).willReturn(Optional.of(merkleToken));

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(TOKEN_WAS_DELETED, validity);
	}

	@Test
	void checksQueryNotSupported() throws Throwable {
		// given:
		var merkleToken = new MerkleToken();
		merkleToken.setTokenType(TokenType.FUNGIBLE_COMMON);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(view.tokenWith(tokenId)).willReturn(Optional.of(merkleToken));

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, start, end), view);

		// then:
		assertEquals(NOT_SUPPORTED, validity);
	}

	@Test
	void properCanonicalFunction() {
		assertEquals(HederaFunctionality.TokenGetNftInfos, subject.canonicalFunction());
	}

	@Test
	void getsValidity() {
		// given:
		Response response = Response.newBuilder().setTokenGetNftInfos(
				TokenGetNftInfosResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, tokenId, start, end);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
	}

	@Test
	void checksInvalidRangesProperly() throws Throwable {
		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, tokenId, end, start), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void getsTokenNftInfos() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, tokenId, start, end);
		given(view.infosForTokenNfts(tokenId, start, end)).willReturn(Optional.of(tokenNftInfos));

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetNftInfos());
		assertTrue(response.getTokenGetNftInfos().hasHeader(), "Missing response header!");
		assertEquals(OK, response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getTokenGetNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetNftInfos().getHeader().getCost());
		// and:
		var actual = response.getTokenGetNftInfos().getNftsList();
		assertEquals(tokenNftInfos, actual);
	}

	@Test
	void getsInfoFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, tokenId, start, end);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetTokenNftInfosAnswer.TOKEN_NFT_INFOS_CTX_KEY, tokenNftInfos);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		var opResponse = response.getTokenGetNftInfos();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(tokenNftInfos, opResponse.getNftsList());
	}

	@Test
	void validatesQueryContext() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, tokenId, start, end);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		var opResponse = response.getTokenGetNftInfos();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(INVALID_NFT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertTrue(opResponse.getNftsList().isEmpty());
	}

	@Test
	void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, tokenId, start, end);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetNftInfos());
		assertEquals(OK, response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getTokenGetNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetNftInfos().getHeader().getCost());
	}

	@Test
	void setsCorrectHeaderOnFailedValidity() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, tokenId, start, end);

		// when:
		Response response = subject.responseGiven(query, view, INVALID_NFT_ID, fee);

		// then:
		assertTrue(response.hasTokenGetNftInfos());
		assertEquals(INVALID_NFT_ID, response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getTokenGetNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetNftInfos().getHeader().getCost());
	}

	@Test
	void failsToGetTheInfo() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, invalidTokenId, start, end);

		given(view.infosForTokenNfts(invalidTokenId, start, end)).willReturn(Optional.empty());

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetNftInfos());
		assertTrue(response.getTokenGetNftInfos().hasHeader(), "Missing response header!");
		assertEquals(INVALID_NFT_ID, response.getTokenGetNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getTokenGetNftInfos().getHeader().getResponseType());
		assertEquals(0, response.getTokenGetNftInfos().getHeader().getCost());
		// and:
		var actual = response.getTokenGetNftInfos().getNftsList();
		assertTrue(actual.isEmpty());
	}

	private Query validQuery(ResponseType type, long payment, TokenID id, long start, long end) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer("0.0.1", COMPLEX_KEY_ACCOUNT_KT, "0.0.3", payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		TokenGetNftInfosQuery.Builder op = TokenGetNftInfosQuery.newBuilder()
				.setHeader(header)
				.setStart(start)
				.setEnd(end)
				.setTokenID(id);
		return Query.newBuilder().setTokenGetNftInfos(op).build();
	}
}
