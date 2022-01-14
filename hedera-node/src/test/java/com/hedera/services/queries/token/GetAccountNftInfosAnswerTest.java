package com.hedera.services.queries.token;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosResponse;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GetAccountNftInfosAnswerTest {
	GetAccountNftInfosAnswer subject;
	Transaction paymentTxn;
	StateView view;
	private long fee = 1_234L;
	private final ByteString ledgerId = ByteString.copyFromUtf8("0xff");

	String node = "0.0.3";
	String payer = "0.0.1";
	AccountID accountId = asAccount("0.0.2");
	AccountID invalidAccountId = asAccount("0.0.4");
	MerkleMap<EntityNum, MerkleAccount> accountMap;

	private List<TokenNftInfo> accountNftInfos;
	long start = 0, end = 2;

	OptionValidator optionValidator;

	@BeforeEach
	void setup() {
		view = mock(StateView.class);
		optionValidator = mock(OptionValidator.class);
		subject = new GetAccountNftInfosAnswer(optionValidator);

		accountNftInfos = new ArrayList<>(List.of(
				TokenNftInfo.newBuilder()
						.setLedgerId(ledgerId)
						.setMetadata(ByteString.copyFromUtf8("stuff1"))
						.build(),
				TokenNftInfo.newBuilder()
						.setLedgerId(ledgerId)
						.setMetadata(ByteString.copyFromUtf8("stuff2"))
						.build(),
				TokenNftInfo.newBuilder()
						.setLedgerId(ledgerId)
						.setMetadata(ByteString.copyFromUtf8("stuff3"))
						.build()
		));
		accountMap = new MerkleMap<>();
		accountMap.put(
				EntityNum.fromAccountId(accountId),
				new MerkleAccount());
	}

	@Test
	void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, accountId, start, end)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, accountId, start, end)));
	}

	@Test
	void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, accountId, start, end)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, accountId, start, end)));
	}

	@Test
	void checksValidityProperly() throws Throwable {
		// given:
		given(view.numNftsOwnedBy(accountId)).willReturn(3L);
		given(view.accounts()).willReturn(accountMap);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(optionValidator.queryableAccountStatus(accountId, accountMap)).willReturn(OK);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, start, end), view);

		// then:
		assertEquals(OK, validity);
	}

	@Test
	void checksExceededQueryRangeProperly() throws Throwable {
		// given:
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(INVALID_QUERY_RANGE);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, start, end), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void checksQueryRangeLargerThanNftCount() throws Throwable {
		// given:
		given(view.numNftsOwnedBy(accountId)).willReturn(1L);
		given(view.accounts()).willReturn(accountMap);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(optionValidator.queryableAccountStatus(accountId, accountMap)).willReturn(OK);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, start, end), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void checksInvalidAccountId() throws Throwable {
		// given:
		given(view.numNftsOwnedBy(accountId)).willReturn(10L);
		given(view.accounts()).willReturn(accountMap);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(optionValidator.queryableAccountStatus(accountId, accountMap)).willReturn(INVALID_ACCOUNT_ID);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, start, end), view);

		// then:
		assertEquals(INVALID_ACCOUNT_ID, validity);
	}

	@Test
	void fallsbackToCheckContractId() throws Throwable {
		// given:
		given(view.numNftsOwnedBy(accountId)).willReturn(10L);
		given(view.accounts()).willReturn(accountMap);
		given(optionValidator.nftMaxQueryRangeCheck(start, end)).willReturn(OK);
		given(optionValidator.queryableAccountStatus(accountId, accountMap)).willReturn(INVALID_ACCOUNT_ID);
		given(optionValidator.queryableContractStatus(EntityIdUtils.asContract(accountId), accountMap)).willReturn(OK);

		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, start, end), view);

		// then:
		assertEquals(OK, validity);
	}

	@Test
	void properCanonicalFunction() {
		assertEquals(HederaFunctionality.TokenGetAccountNftInfos, subject.canonicalFunction());
	}

	@Test
	void getsValidity() {
		// given:
		Response response = Response.newBuilder().setTokenGetAccountNftInfos(
				TokenGetAccountNftInfosResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, accountId, start, end);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
	}

	@Test
	void checksInvalidRangesProperly() throws Throwable {
		// when:
		var validity = subject.checkValidity(validQuery(ANSWER_ONLY, 0, accountId, end, start), view);

		// then:
		assertEquals(INVALID_QUERY_RANGE, validity);
	}

	@Test
	void getsTheInfo() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, accountId, start, end);

		given(view.infoForAccountNfts(accountId, start, end)).willReturn(Optional.of(accountNftInfos));

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetAccountNftInfos());
		assertTrue(response.getTokenGetAccountNftInfos().hasHeader(), "Missing response header!");
		assertEquals(OK, response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getTokenGetAccountNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetAccountNftInfos().getHeader().getCost());
		// and:
		var actual = response.getTokenGetAccountNftInfos().getNftsList();
		assertEquals(accountNftInfos, actual);
	}

	@Test
	void getsInfoFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, accountId, start, end);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetAccountNftInfosAnswer.ACCOUNT_NFT_INFO_CTX_KEY, accountNftInfos);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		var opResponse = response.getTokenGetAccountNftInfos();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(accountNftInfos, opResponse.getNftsList());
	}

	@Test
	void validatesQueryContext() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, accountId, start, end);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		var opResponse = response.getTokenGetAccountNftInfos();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(INVALID_ACCOUNT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertTrue(opResponse.getNftsList().isEmpty());
	}

	@Test
	void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, accountId, start, end);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetAccountNftInfos());
		assertEquals(OK, response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getTokenGetAccountNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetAccountNftInfos().getHeader().getCost());
	}

	@Test
	void setsCorrectHeaderOnFailedValidity() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, accountId, start, end);

		// when:
		Response response = subject.responseGiven(query, view, INVALID_ACCOUNT_ID, fee);

		// then:
		assertTrue(response.hasTokenGetAccountNftInfos());
		assertEquals(INVALID_ACCOUNT_ID,
				response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getTokenGetAccountNftInfos().getHeader().getResponseType());
		assertEquals(fee, response.getTokenGetAccountNftInfos().getHeader().getCost());
	}

	@Test
	void failsToGetTheInfo() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, invalidAccountId, start, end);

		given(view.infoForAccountNfts(invalidAccountId, start, end)).willReturn(Optional.empty());

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTokenGetAccountNftInfos());
		assertTrue(response.getTokenGetAccountNftInfos().hasHeader(), "Missing response header!");
		assertEquals(INVALID_ACCOUNT_ID,
				response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getTokenGetAccountNftInfos().getHeader().getResponseType());
		assertEquals(0, response.getTokenGetAccountNftInfos().getHeader().getCost());
		// and:
		var actual = response.getTokenGetAccountNftInfos().getNftsList();
		assertTrue(actual.isEmpty());
	}

	private Query validQuery(ResponseType type, long payment, AccountID id, long start, long end) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		TokenGetAccountNftInfosQuery.Builder op = TokenGetAccountNftInfosQuery.newBuilder()
				.setHeader(header)
				.setStart(start)
				.setEnd(end)
				.setAccountID(id);
		return Query.newBuilder().setTokenGetAccountNftInfos(op).build();
	}
}
