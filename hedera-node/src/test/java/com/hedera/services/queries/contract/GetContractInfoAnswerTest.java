package com.hedera.services.queries.contract;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

class GetContractInfoAnswerTest {
	private Transaction paymentTxn;
	private String node = "0.0.3";
	private String payer = "0.0.12345";
	private String target = "0.0.123";
	private long fee = 1_234L;

	OptionValidator optionValidator;
	StateView view;
	FCMap<MerkleEntityId, MerkleAccount> contracts;

	ContractGetInfoResponse.ContractInfo info;

	GetContractInfoAnswer subject;

	@BeforeEach
	public void setup() {
		info = ContractGetInfoResponse.ContractInfo.newBuilder()
				.setContractID(IdUtils.asContract(target))
				.setContractAccountID(EntityIdUtils.asSolidityAddressHex(IdUtils.asAccount(target)))
				.setMemo("Stay cold...")
				.setAdminKey(COMPLEX_KEY_ACCOUNT_KT.asKey())
				.build();

		contracts = mock(FCMap.class);

		view = mock(StateView.class);
		given(view.contracts()).willReturn(contracts);
		optionValidator = mock(OptionValidator.class);

		subject = new GetContractInfoAnswer(optionValidator);
	}

	@Test
	public void getsTheInfo() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		given(view.infoForContract(asContract(target))).willReturn(Optional.of(info));

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasContractGetInfo());
		assertTrue(response.getContractGetInfo().hasHeader(), "Missing response header!");
		assertEquals(OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, response.getContractGetInfo().getHeader().getResponseType());
		assertEquals(fee, response.getContractGetInfo().getHeader().getCost());
		// and:
		var actual = response.getContractGetInfo().getContractInfo();
		assertEquals(info, actual);
	}

	@Test
	public void getsInfoFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY, info);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		var opResponse = response.getContractGetInfo();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertSame(info, opResponse.getContractInfo());
		// and:
		verify(view, never()).infoForContract(any());
	}

	@Test
	public void recognizesMissingInfoWhenNoCtxGiven() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);

		given(view.infoForContract(asContract(target))).willReturn(Optional.empty());

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		ContractGetInfoResponse opResponse = response.getContractGetInfo();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(INVALID_CONTRACT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	public void recognizesMissingInfoWhenCtxGiven() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, Collections.emptyMap());

		// then:
		ContractGetInfoResponse opResponse = response.getContractGetInfo();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(INVALID_CONTRACT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
		verify(view, never()).infoForContract(any());
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasContractGetInfo());
		assertEquals(OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getContractGetInfo().getHeader().getResponseType());
		assertEquals(fee, response.getContractGetInfo().getHeader().getCost());
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, CONTRACT_DELETED, fee);

		// then:
		assertTrue(response.hasContractGetInfo());
		assertEquals(
				CONTRACT_DELETED,
				response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getContractGetInfo().getHeader().getResponseType());
		assertEquals(fee, response.getContractGetInfo().getHeader().getCost());
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(HederaFunctionality.ContractGetInfo, subject.canonicalFunction());
	}

	@Test
	public void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setContractGetInfo(
				ContractGetInfoResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	public void usesValidator() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		given(optionValidator.queryableContractStatus(asContract(target), contracts))
				.willReturn(CONTRACT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(CONTRACT_DELETED, validity);
		// and:
		verify(optionValidator).queryableContractStatus(any(), any());
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, target);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getBackwardCompatibleSignedTxn());
	}

	private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		ContractGetInfoQuery.Builder op = ContractGetInfoQuery.newBuilder()
				.setHeader(header)
				.setContractID(asContract(idLit));
		return Query.newBuilder().setContractGetInfo(op).build();
	}
}