package com.hedera.services.queries.crypto;

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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountRecordsResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.utils.IdUtils.*;
import static com.hedera.test.utils.TxnUtils.*;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordTwo;

@RunWith(JUnitPlatform.class)
class GetAccountRecordsAnswerTest {
	long fee = 1_234L;
	StateView view;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	Transaction paymentTxn;
	String node = "0.0.3";
	String payer = "0.0.12345";
	String target = payer;
	MerkleAccount payerAccount;
	OptionValidator optionValidator;

	GetAccountRecordsAnswer subject;

	PropertySource propertySource;
	@BeforeEach
	private void setup() throws Throwable {
		payerAccount = MapValueFactory.newAccount()
				.accountKeys(COMPLEX_KEY_ACCOUNT_KT)
				.proxy(asAccount("1.2.3"))
				.senderThreshold(1_234L)
				.receiverThreshold(4_321L)
				.receiverSigRequired(true)
				.balance(555L)
				.autoRenewPeriod(1_000_000L)
				.expirationTime(9_999_999L)
				.get();
		payerAccount.records().offer(recordOne());
		payerAccount.records().offer(recordTwo());

		accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromAccountId(asAccount(target)))).willReturn(payerAccount);

		propertySource = mock(PropertySource.class);
		view = new StateView(StateView.EMPTY_TOPICS_SUPPLIER, () -> accounts, propertySource);

		optionValidator = mock(OptionValidator.class);

		subject = new GetAccountRecordsAnswer(new AnswerFunctions(), optionValidator);
	}

	@Test
	public void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
		assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsInvalidResponse() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, ACCOUNT_DELETED, fee);

		// then:
		assertTrue(response.hasCryptoGetAccountRecords());
		CryptoGetAccountRecordsResponse opResponse = response.getCryptoGetAccountRecords();
		assertEquals(ACCOUNT_DELETED, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasCryptoGetAccountRecords());
		CryptoGetAccountRecordsResponse opResponse = response.getCryptoGetAccountRecords();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	public void getsTheAccountRecords() throws Throwable {
		// setup:
		Query query = validQuery(ANSWER_ONLY, fee, target);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasCryptoGetAccountRecords());
		CryptoGetAccountRecordsResponse opResponse = response.getCryptoGetAccountRecords();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(ANSWER_ONLY, opResponse.getHeader().getResponseType());
		assertEquals(0, opResponse.getHeader().getCost());
		// and:
		assertEquals(ExpirableTxnRecord.allToGrpc(payerAccount.recordList()), opResponse.getRecordsList());
	}

	@Test
	public void usesValidator() throws Throwable {
		// setup:
		Query query = validQuery(COST_ANSWER, fee, target);

		given(optionValidator.queryableAccountStatus(asAccount(target), accounts)).willReturn(ACCOUNT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(ACCOUNT_DELETED, validity);
		// and:
		verify(optionValidator).queryableAccountStatus(any(), any());
	}

	@Test
	public void getsExpectedPayment() throws Throwable {
		// given:
		Query query = validQuery(COST_ANSWER, fee, target);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxn());
	}

	@Test
	public void recognizesFunction() {
		// expect:
		assertEquals(CryptoGetAccountRecords, subject.canonicalFunction());
	}

	@Test
	public void requiresAnswerOnlyPayment() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
		assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
	}

	@Test
	public void getsValidity() {
		// given:
		Response response = Response.newBuilder().setCryptoGetAccountRecords(
				CryptoGetAccountRecordsResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		QueryHeader.Builder header = QueryHeader.newBuilder()
				.setPayment(this.paymentTxn)
				.setResponseType(type);
		CryptoGetAccountRecordsQuery.Builder op = CryptoGetAccountRecordsQuery.newBuilder()
				.setHeader(header)
				.setAccountID(asAccount(idLit));
		return Query.newBuilder().setCryptoGetAccountRecords(op).build();
	}
}
