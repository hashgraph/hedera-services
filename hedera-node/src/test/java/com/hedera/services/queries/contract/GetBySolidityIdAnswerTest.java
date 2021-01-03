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
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class GetBySolidityIdAnswerTest {
	private String node = "0.0.3";
	private long fee = 1_234L;
	private String payer = "0.0.12345";
	private Transaction paymentTxn;

	StateView view;

	GetBySolidityIdAnswer subject;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		subject = new GetBySolidityIdAnswer();
	}

	@Test
	public void noCopyPasteErrors() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertEquals(HederaFunctionality.GetBySolidityID, subject.canonicalFunction());
		assertTrue(subject.extractPaymentFrom(query).isEmpty());
		assertTrue(subject.needsAnswerOnlyCost(query));
		assertFalse(subject.requiresNodePayment(query));
		assertEquals(NOT_SUPPORTED, subject.extractValidityFrom(response));
	}

	@Test
	public void respectsTypeOfUnsupportedQuery() throws Throwable {
		// given:
		Query costAnswer = validQuery(COST_ANSWER, 1L);
		Query answerOnly = validQuery(ANSWER_ONLY, 1L);

		// when:
		Response costAnswerResponse = subject.responseGiven(costAnswer, StateView.EMPTY_VIEW, OK, 0L);
		Response answerOnlyResponse = subject.responseGiven(answerOnly, StateView.EMPTY_VIEW, OK, 0L);

		// then:
		assertEquals(COST_ANSWER, costAnswerResponse.getGetBySolidityID().getHeader().getResponseType());
		assertEquals(ANSWER_ONLY, answerOnlyResponse.getGetBySolidityID().getHeader().getResponseType());
		// and:
		assertEquals(NOT_SUPPORTED, subject.extractValidityFrom(costAnswerResponse));
		assertEquals(NOT_SUPPORTED, subject.extractValidityFrom(answerOnlyResponse));
	}

	private Query validQuery(ResponseType type, long payment) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);

		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		GetBySolidityIDQuery.Builder op = GetBySolidityIDQuery.newBuilder()
				.setHeader(header);
		return Query.newBuilder().setGetBySolidityID(op).build();
	}

	private GetBySolidityIDResponse response(ResponseCodeEnum status) {
		return GetBySolidityIDResponse.newBuilder()
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
				.build();
	}
}
