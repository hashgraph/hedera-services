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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class ContractCallLocalAnswerTest {
	private String node = "0.0.3";
	private long gas = 123L;
	private long fee = 1_234L;
	private String payer = "0.0.12345";
	private Transaction paymentTxn;
	private ContractID target = IdUtils.asContract("0.0.75231");
	ByteString result = ByteString.copyFrom("Searching for images".getBytes());

	StateView view;

	ContractCallLocalAnswer subject;
	OptionValidator validator;
	ContractCallLocalAnswer.LegacyLocalCaller delegate;
	FCMap<MerkleEntityId, MerkleAccount> contracts;

	@BeforeEach
	private void setup() throws Throwable {
		contracts = (FCMap<MerkleEntityId, MerkleAccount>) mock(FCMap.class);
		view = mock(StateView.class);

		delegate = mock(ContractCallLocalAnswer.LegacyLocalCaller.class);
		validator = mock(OptionValidator.class);

		given(view.contracts()).willReturn(contracts);
		given(validator.queryableContractStatus(target, contracts)).willReturn(OK);

		subject = new ContractCallLocalAnswer(delegate, validator);
	}

	@Test
	public void rejectsInvalidCid() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee);
		// and:
		given(validator.queryableContractStatus(target, contracts)).willReturn(CONTRACT_DELETED);

		// expect:
		assertEquals(CONTRACT_DELETED, subject.checkValidity(query, view));
	}

	@Test
	public void rejectsNegativeGas() throws Throwable {
		// setup:
		gas = -1;

		// given:
		Query query = validQuery(COST_ANSWER, fee);

		// expect:
		assertEquals(CONTRACT_NEGATIVE_GAS, subject.checkValidity(query, view));
	}

	@Test
	public void noCopyPasteErrors() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, INSUFFICIENT_TX_FEE, fee);

		// then:
		assertEquals(HederaFunctionality.ContractCallLocal, subject.canonicalFunction());
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
		assertTrue(subject.needsAnswerOnlyCost(query));
		assertFalse(subject.requiresNodePayment(query));
		assertEquals(INSUFFICIENT_TX_FEE, subject.extractValidityFrom(response));
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, FAIL_INVALID, fee);

		// then:
		assertTrue(response.hasContractCallLocal());
		assertEquals(FAIL_INVALID, response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, response.getContractCallLocal().getHeader().getResponseType());
		assertEquals(fee, response.getContractCallLocal().getHeader().getCost());
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasContractCallLocal());
		ContractCallLocalResponse opResponse = response.getContractCallLocal();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void throwsOnAvailCtxWithNoCachedResponse() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
		Map<String, Object> queryCtx = new HashMap<>();

		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.responseGiven(sensibleQuery, view, OK, 0L, queryCtx));
	}

	@Test
	public void usesCtxWhenAvail() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
		Map<String, Object> queryCtx = new HashMap<>();
		var cachedResponse = response(CONTRACT_EXECUTION_EXCEPTION);
		queryCtx.put(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY, cachedResponse);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, queryCtx);

		// then:
		var opResponse = response.getContractCallLocal();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(CONTRACT_EXECUTION_EXCEPTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(result, opResponse.getFunctionResult().getContractCallResult());
		assertEquals(target, opResponse.getFunctionResult().getContractID());
		verify(delegate, never()).perform(any(), anyLong());
	}

	@Test
	public void getsCallResponseWhenNoCtx() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);
		var delegateResponse = response(CONTRACT_EXECUTION_EXCEPTION);

		given(delegate.perform(argThat(sensibleQuery.getContractCallLocal()::equals), anyLong()))
				.willReturn(delegateResponse);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		var opResponse = response.getContractCallLocal();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(CONTRACT_EXECUTION_EXCEPTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(result, opResponse.getFunctionResult().getContractCallResult());
		assertEquals(target, opResponse.getFunctionResult().getContractID());
	}

	@Test
	public void translatesFailWhenNoCtx() throws Throwable {
		// setup:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

		given(delegate.perform(any(), anyLong())).willThrow(Exception.class);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		var opResponse = response.getContractCallLocal();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(FAIL_INVALID, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	public void respectsMetaValidity() throws Throwable {
		// given:
		Query sensibleQuery = validQuery(ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

		// then:
		var opResponse = response.getContractCallLocal();
		assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	private Query validQuery(ResponseType type, long payment) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		ContractCallLocalQuery.Builder op = ContractCallLocalQuery.newBuilder()
				.setHeader(header)
				.setContractID(target)
				.setGas(gas);
		return Query.newBuilder().setContractCallLocal(op).build();
	}

	private ContractCallLocalResponse response(ResponseCodeEnum status) {
		return ContractCallLocalResponse.newBuilder()
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
				.setFunctionResult(ContractFunctionResult.newBuilder()
						.setContractCallResult(result))
				.build();
	}
}
